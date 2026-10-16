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
            is_screen_locked: false,
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
        let is_locked = app_id.as_deref() == Some("mac:com.apple.loginwindow")
            || app_id.as_deref() == Some("mac:com.apple.ScreenSaver.Engine")
            || process_name.as_deref() == Some("loginwindow")
            || process_name.as_deref() == Some("ScreenSaverEngine");
        ForegroundSample {
            app_id,
            pid,
            process_name,
            idle_seconds: idle,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
            is_screen_locked: is_locked,
        }
    }

    fn idle_seconds() -> u32 {
        // CGEventSourceSecondsSinceLastEventType(kCGEventSourceStateCombinedSessionState, kCGAnyInputEventType)
        // Passing u32::MAX (~0) corresponds to kCGAnyInputEventType in CoreGraphics, tracking keyboard, mouse, trackpad & scroll.
        #[link(name = "CoreGraphics", kind = "framework")]
        extern "C" {
            fn CGEventSourceSecondsSinceLastEventType(state_id: u32, event_type: u32) -> f64;
        }
        const COMBINED_SESSION_STATE: u32 = 0;
        const ANY_INPUT_EVENT_TYPE: u32 = u32::MAX;
        let secs =
            unsafe { CGEventSourceSecondsSinceLastEventType(COMBINED_SESSION_STATE, ANY_INPUT_EVENT_TYPE) };
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
            .args([
                "info", "-only", "bundleid", "-only", "pid", "-only", "name", &asn,
            ])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default();

        let mut bundle = None;
        let mut pid = None;
        let mut name = None;

        for line in info.lines() {
            let line = line.trim();
            if let Some((k, v)) = line.split_once('=') {
                let key = k.trim().trim_matches('"');
                let val = v.trim().trim_matches('"');
                if key.eq_ignore_ascii_case("CFBundleIdentifier") && !val.is_empty() {
                    bundle = Some(val.to_string());
                } else if key.eq_ignore_ascii_case("pid") {
                    pid = val.parse::<u32>().ok();
                } else if (key.eq_ignore_ascii_case("LSDisplayName")
                    || key.eq_ignore_ascii_case("name"))
                    && !val.is_empty()
                {
                    name = Some(val.to_string());
                }
            }
        }

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
        let is_locked = app_id.is_none()
            || process_name
                .as_deref()
                .map(|p| p.eq_ignore_ascii_case("LockApp.exe") || p.eq_ignore_ascii_case("LogonUI.exe"))
                .unwrap_or(false);
        ForegroundSample {
            app_id,
            pid,
            process_name,
            idle_seconds: idle,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
            is_screen_locked: is_locked,
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
        use windows::Win32::Foundation::CloseHandle;
        use windows::Win32::Security::{
            GetTokenInformation, TokenElevation, TOKEN_ELEVATION, TOKEN_QUERY,
        };
        use windows::Win32::System::Threading::{GetCurrentProcess, OpenProcessToken};

        unsafe {
            let mut token = windows::Win32::Foundation::HANDLE::default();
            if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token).is_err() {
                return false;
            }
            let mut elevation = TOKEN_ELEVATION::default();
            let mut return_length = 0u32;
            let ok = GetTokenInformation(
                token,
                TokenElevation,
                Some(&mut elevation as *mut _ as *mut std::ffi::c_void),
                std::mem::size_of::<TOKEN_ELEVATION>() as u32,
                &mut return_length,
            );
            let _ = CloseHandle(token);
            ok.is_ok() && elevation.TokenIsElevated != 0
        }
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

/// Whether current desktop session is Wayland.
pub fn is_wayland() -> bool {
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        std::env::var("WAYLAND_DISPLAY").is_ok()
            || std::env::var("XDG_SESSION_TYPE").as_deref() == Ok("wayland")
    }
    #[cfg(not(all(unix, not(target_os = "macos"))))]
    {
        false
    }
}

#[cfg(all(unix, not(target_os = "macos")))]
mod linux {
    use super::{is_wayland, mono_ms, wall_ms};
    use crate::types::ForegroundSample;
    use std::process::Command;

    pub fn sample() -> ForegroundSample {
        let (app_id, pid, process_name) = if is_wayland() {
            wayland_sample()
        } else {
            x11_sample()
        };
        let idle_seconds = query_idle_seconds();
        let is_locked = process_name
            .as_deref()
            .map(|p| p.contains("lock") || p.contains("screensaver"))
            .unwrap_or(false);
        ForegroundSample {
            app_id,
            pid,
            process_name,
            idle_seconds,
            wall_ms: wall_ms(),
            mono_ms: mono_ms(),
            is_screen_locked: is_locked,
        }
    }

    fn x11_sample() -> (Option<String>, Option<u32>, Option<String>) {
        if std::env::var("DISPLAY").is_err() {
            return (None, None, None);
        }
        let out = Command::new("xprop")
            .args(["-root", "_NET_ACTIVE_WINDOW"])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default();

        let window_id = out
            .split_whitespace()
            .last()
            .filter(|w| w.starts_with("0x"));

        let Some(win_id) = window_id else {
            return (None, None, None);
        };

        if win_id == "0x0" {
            return (None, None, None);
        }

        let win_info = Command::new("xprop")
            .args(["-id", win_id, "_NET_WM_PID", "WM_CLASS"])
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default();

        let mut pid = None;
        let mut class_name = None;

        for line in win_info.lines() {
            if line.contains("_NET_WM_PID") {
                if let Some(val) = line.split('=').nth(1) {
                    pid = val.trim().parse::<u32>().ok();
                }
            } else if line.contains("WM_CLASS") {
                if let Some(val) = line.split('=').nth(1) {
                    let parts: Vec<&str> = val.split(',').collect();
                    if let Some(first) = parts.first() {
                        class_name = Some(first.trim().trim_matches('"').to_string());
                    }
                }
            }
        }

        let app_id = pid.and_then(|p| {
            std::fs::read_link(format!("/proc/{p}/exe"))
                .ok()
                .map(|p| format!("linux:{}", p.to_string_lossy().to_ascii_lowercase()))
        });

        let process_name = pid
            .and_then(|p| {
                std::fs::read_link(format!("/proc/{p}/exe"))
                    .ok()
                    .and_then(|p| p.file_name().map(|n| n.to_string_lossy().to_string()))
            })
            .or(class_name);

        (app_id, pid, process_name)
    }

    fn wayland_sample() -> (Option<String>, Option<u32>, Option<String>) {
        (None, None, None)
    }

    fn query_idle_seconds() -> u32 {
        if let Ok(out) = Command::new("xprintidle").output() {
            if let Ok(s) = String::from_utf8(out.stdout) {
                if let Ok(ms) = s.trim().parse::<u64>() {
                    return (ms / 1000) as u32;
                }
            }
        }
        0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sample_foreground_produces_valid_timestamps() {
        let sample = sample_foreground();
        assert!(
            sample.wall_ms > 0,
            "wall_ms must be positive epoch timestamp"
        );
        assert!(sample.mono_ms >= 0, "mono_ms must be non-negative");

        #[cfg(target_os = "macos")]
        {
            // On macOS dev machine running interactive session, frontmost app is often discovered
            if let Some(ref id) = sample.app_id {
                assert!(id.starts_with("mac:"), "mac app id must have mac: prefix");
            }
        }
    }

    #[test]
    fn mono_clock_advances() {
        let m1 = mono_ms();
        std::thread::sleep(std::time::Duration::from_millis(5));
        let m2 = mono_ms();
        assert!(m2 >= m1, "mono clock must never run backwards");
    }
}
