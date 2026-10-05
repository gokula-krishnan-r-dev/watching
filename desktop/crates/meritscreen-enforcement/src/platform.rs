//! OS probes: idle seconds, foreground app, monotonic ms, admin detection.

use crate::gate::GateAction;
use crate::protected::is_protected_app_id;
use crate::types::ForegroundSample;

/// Sample foreground + idle using documented OS APIs (no Accessibility traps).
pub fn sample_foreground() -> ForegroundSample {
    #[cfg(target_os = "macos")]
    {
        macos::sample()
    }
    #[cfg(windows)]
    {
        windows::sample()
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        linux::sample()
    }
    #[cfg(not(any(windows, unix)))]
    {
        ForegroundSample {
            app_id: None,
            pid: None,
            process_name: None,
            idle_seconds: 0,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
        }
    }
}

pub fn wall_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

pub fn mono_ms() -> i64 {
    use std::sync::OnceLock;
    use std::time::Instant;
    static START: OnceLock<Instant> = OnceLock::new();
    let start = START.get_or_init(Instant::now);
    start.elapsed().as_millis() as i64
}

/// Best-effort: child account elevated?
pub fn detect_admin_account() -> bool {
    #[cfg(windows)]
    {
        windows::is_elevated()
    }
    #[cfg(target_os = "macos")]
    {
        macos::is_admin()
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        unsafe { libc::geteuid() == 0 }
    }
    #[cfg(not(any(windows, unix)))]
    {
        false
    }
}

/// Apply L3 action locally (Agent). Never kills protected apps.
pub fn apply_gate_action(action: &GateAction) -> Result<(), String> {
    match action {
        GateAction::Allow | GateAction::Cover { .. } => Ok(()),
        GateAction::Terminate {
            pid: Some(pid),
            app_id,
            ..
        } => {
            if let Some(id) = app_id {
                if is_protected_app_id(id) {
                    return Ok(());
                }
            }
            if *pid <= 1 {
                return Ok(());
            }
            #[cfg(unix)]
            {
                let _ = unsafe { libc::kill(*pid as i32, libc::SIGTERM) };
                Ok(())
            }
            #[cfg(windows)]
            {
                windows::terminate_pid(*pid)
            }
            #[cfg(not(any(windows, unix)))]
            {
                Ok(())
            }
        }
        GateAction::Terminate { pid: None, .. } => Ok(()),
    }
}

#[cfg(target_os = "macos")]
mod macos {
    use super::{mono_ms, wall_ms};
    use crate::types::ForegroundSample;
    use std::process::Command;

    pub fn sample() -> ForegroundSample {
        let idle = idle_seconds();
        let (app_id, pid, process_name) = frontmost();
        ForegroundSample {
            app_id,
            pid,
            process_name,
            idle_seconds: idle,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
        }
    }

    fn idle_seconds() -> u32 {
        // CGEventSourceSecondsSinceLastEventType(kCGEventSourceStateCombinedSessionState, …)
        #[link(name = "CoreGraphics", kind = "framework")]
        extern "C" {
            fn CGEventSourceSecondsSinceLastEventType(state_id: u32, event_type: u32) -> f64;
        }
        const COMBINED_SESSION_STATE: u32 = 0;
        const KEY_DOWN: u32 = 10;
        let secs =
            unsafe { CGEventSourceSecondsSinceLastEventType(COMBINED_SESSION_STATE, KEY_DOWN) };
        if secs.is_finite() && secs >= 0.0 {
            secs.min(u32::MAX as f64) as u32
        } else {
            0
        }
    }

    fn frontmost() -> (Option<String>, Option<u32>, Option<String>) {
        // Avoid MainThread-only AppKit in the sampling thread; use Launch Services helper.
        let front = Command::new("lsappinfo")
            .args(["front"])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());
        let Some(asn) = front else {
            return (None, None, None);
        };
        let info = Command::new("lsappinfo")
            .args(["info", "-only", "bundleid", &asn])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default();
        let bundle = info
            .lines()
            .find_map(|l| {
                l.split('"')
                    .nth(1)
                    .filter(|s| s.contains('.'))
                    .map(|s| s.to_string())
            })
            .or_else(|| {
                info.split('=')
                    .nth(1)
                    .map(|s| s.trim().trim_matches('"').to_string())
                    .filter(|s| !s.is_empty())
            });
        let pid_info = Command::new("lsappinfo")
            .args(["info", "-only", "pid", &asn])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default();
        let pid = pid_info
            .chars()
            .filter(|c| c.is_ascii_digit())
            .collect::<String>()
            .parse()
            .ok();
        let name_info = Command::new("lsappinfo")
            .args(["info", "-only", "name", &asn])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default();
        let name = name_info
            .split('"')
            .nth(1)
            .map(|s| s.to_string())
            .filter(|s| !s.is_empty());
        let app_id = bundle.map(|b| format!("mac:{b}"));
        (app_id, pid, name)
    }

    pub fn is_admin() -> bool {
        Command::new("id")
            .arg("-Gn")
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .map(|s| s.split_whitespace().any(|g| g == "admin"))
            .unwrap_or(false)
    }
}

#[cfg(windows)]
mod windows {
    use super::{mono_ms, wall_ms};
    use crate::types::ForegroundSample;
    use std::path::Path;

    pub fn sample() -> ForegroundSample {
        let idle = idle_seconds();
        let (app_id, pid, process_name) = foreground();
        ForegroundSample {
            app_id,
            pid,
            process_name,
            idle_seconds: idle,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
        }
    }

    fn idle_seconds() -> u32 {
        use windows::Win32::System::SystemInformation::GetTickCount;
        use windows::Win32::UI::Input::{GetLastInputInfo, LASTINPUTINFO};
        unsafe {
            let mut info = LASTINPUTINFO {
                cbSize: std::mem::size_of::<LASTINPUTINFO>() as u32,
                dwTime: 0,
            };
            if GetLastInputInfo(&mut info).is_ok() {
                let now = GetTickCount();
                return now.wrapping_sub(info.dwTime) / 1000;
            }
        }
        0
    }

    fn foreground() -> (Option<String>, Option<u32>, Option<String>) {
        use windows::Win32::Foundation::CloseHandle;
        use windows::Win32::System::Threading::{
            OpenProcess, QueryFullProcessImageNameW, PROCESS_QUERY_LIMITED_INFORMATION,
        };
        use windows::Win32::UI::WindowsAndMessaging::{
            GetForegroundWindow, GetWindowThreadProcessId,
        };
        unsafe {
            let hwnd = GetForegroundWindow();
            if hwnd.0.is_null() {
                return (None, None, None);
            }
            let mut pid = 0u32;
            GetWindowThreadProcessId(hwnd, Some(&mut pid));
            if pid == 0 {
                return (None, None, None);
            }
            let Ok(handle) = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) else {
                return (None, Some(pid), None);
            };
            let mut buf = [0u16; 512];
            let mut size = buf.len() as u32;
            let path = if QueryFullProcessImageNameW(
                handle,
                Default::default(),
                windows::core::PWSTR(buf.as_mut_ptr()),
                &mut size,
            )
            .is_ok()
            {
                String::from_utf16_lossy(&buf[..size as usize])
            } else {
                String::new()
            };
            let _ = CloseHandle(handle);
            let name = Path::new(&path)
                .file_name()
                .and_then(|s| s.to_str())
                .map(|s| s.to_string());
            let app_id = if path.is_empty() {
                None
            } else {
                Some(format!(
                    "win:{}",
                    path.replace('\\', "/").to_ascii_lowercase()
                ))
            };
            (app_id, Some(pid), name)
        }
    }

    pub fn is_elevated() -> bool {
        false
    }

    pub fn terminate_pid(pid: u32) -> Result<(), String> {
        use windows::Win32::Foundation::CloseHandle;
        use windows::Win32::System::Threading::{OpenProcess, TerminateProcess, PROCESS_TERMINATE};
        unsafe {
            let handle = OpenProcess(PROCESS_TERMINATE, false, pid).map_err(|e| e.to_string())?;
            let result = TerminateProcess(handle, 1);
            let _ = CloseHandle(handle);
            result.map_err(|e| e.to_string())
        }
    }
}

#[cfg(all(unix, not(target_os = "macos")))]
mod linux {
    use super::{mono_ms, wall_ms};
    use crate::types::ForegroundSample;

    pub fn sample() -> ForegroundSample {
        ForegroundSample {
            app_id: None,
            pid: None,
            process_name: None,
            idle_seconds: 0,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
        }
    }
}
