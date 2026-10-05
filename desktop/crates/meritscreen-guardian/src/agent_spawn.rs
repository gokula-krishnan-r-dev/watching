//! Spawn Session Agent in the interactive user session.

use std::path::PathBuf;
use std::process::Child;

use tracing::info;

/// Locate `meritscreen-agent` next to the guardian binary (installer layout).
pub fn agent_binary_path() -> PathBuf {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            #[cfg(windows)]
            let candidate = dir.join("meritscreen-agent.exe");
            #[cfg(not(windows))]
            let candidate = dir.join("meritscreen-agent");
            if candidate.exists() {
                return candidate;
            }
        }
    }
    PathBuf::from("meritscreen-agent")
}

/// Console / non-service spawn: start agent as a child of the current process.
pub fn spawn_agent_console() -> std::io::Result<Child> {
    let bin = agent_binary_path();
    info!(path = %bin.display(), "spawning session agent (console)");
    std::process::Command::new(bin).arg("--console").spawn()
}

/// Spawn agent for the logged-in user.
///
/// - Windows service context: `WTSQueryUserToken` + `CreateProcessAsUserW`
/// - Console / non-Windows: child process of Guardian
pub fn spawn_agent_in_user_session() -> anyhow::Result<()> {
    #[cfg(windows)]
    {
        match windows_impl::spawn_via_wts() {
            Ok(()) => return Ok(()),
            Err(e) => {
                tracing::warn!(error = %e, "WTS agent spawn failed — console fallback");
            }
        }
    }
    spawn_agent_console()?;
    Ok(())
}

#[cfg(windows)]
mod windows_impl {
    use super::agent_binary_path;
    use std::mem::size_of;
    use tracing::info;
    use windows::core::PWSTR;
    use windows::Win32::Foundation::{CloseHandle, HANDLE, INVALID_HANDLE_VALUE};
    use windows::Win32::System::Environment::{CreateEnvironmentBlock, DestroyEnvironmentBlock};
    use windows::Win32::System::RemoteDesktop::{
        WTSEnumerateSessionsW, WTSFreeMemory, WTSQueryUserToken, WTS_CONNECTSTATE_CLASS,
        WTS_CURRENT_SERVER_HANDLE, WTS_SESSION_INFOW,
    };
    use windows::Win32::System::Threading::{
        CreateProcessAsUserW, CREATE_UNICODE_ENVIRONMENT, PROCESS_INFORMATION, STARTUPINFOW,
    };

    pub fn spawn_via_wts() -> anyhow::Result<()> {
        let session_id = active_session_id()
            .ok_or_else(|| anyhow::anyhow!("no active interactive Windows session"))?;

        let mut token = HANDLE::default();
        unsafe {
            WTSQueryUserToken(session_id, &mut token)?;
        }
        if token.is_invalid() || token == INVALID_HANDLE_VALUE {
            anyhow::bail!("invalid user token");
        }

        let bin = agent_binary_path();
        let mut cmd = format!("\"{}\" --service-session\0", bin.display());
        let mut cmd_wide: Vec<u16> = cmd.encode_utf16().collect();

        let mut env_block: *mut std::ffi::c_void = std::ptr::null_mut();
        unsafe {
            CreateEnvironmentBlock(&mut env_block, token, false)?;
        }

        let mut si = STARTUPINFOW::default();
        si.cb = size_of::<STARTUPINFOW>() as u32;
        let mut pi = PROCESS_INFORMATION::default();

        let result = unsafe {
            CreateProcessAsUserW(
                token,
                None,
                Some(PWSTR(cmd_wide.as_mut_ptr())),
                None,
                None,
                false,
                CREATE_UNICODE_ENVIRONMENT,
                Some(env_block),
                None,
                &si,
                &mut pi,
            )
        };

        unsafe {
            if !env_block.is_null() {
                let _ = DestroyEnvironmentBlock(env_block);
            }
            let _ = CloseHandle(token);
        }

        result?;
        info!(
            pid = pi.dwProcessId,
            session_id, "agent started in user session"
        );
        unsafe {
            let _ = CloseHandle(pi.hThread);
            let _ = CloseHandle(pi.hProcess);
        }
        Ok(())
    }

    fn active_session_id() -> Option<u32> {
        unsafe {
            let mut ptr = std::ptr::null_mut();
            let mut count = 0u32;
            if WTSEnumerateSessionsW(WTS_CURRENT_SERVER_HANDLE, 0, 1, &mut ptr, &mut count).is_err()
            {
                return None;
            }
            let sessions =
                std::slice::from_raw_parts(ptr as *const WTS_SESSION_INFOW, count as usize);
            // WTSActive == 0
            let found = sessions
                .iter()
                .find(|s| s.State == WTS_CONNECTSTATE_CLASS(0))
                .map(|s| s.SessionId);
            WTSFreeMemory(ptr as *mut _);
            found
        }
    }
}
