//! Background watchdog and self-healing supervisor for Watching child protection.
//!
//! Ensures that if the main GUI process is killed (via Activity Monitor, Task Manager,
//! SIGKILL, Force Quit, or unexpected termination), a detached background supervisor
//! immediately detects the termination and relaunches Watching to maintain uninterrupted
//! active window monitoring, timer countdown, and screen time enforcement.
//!
//! Only a verified Parent PIN can authorize a clean exit, which sets an exit flag
//! allowing both the UI and supervisor to terminate cleanly.

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};

pub static AUTHORIZED_EXIT: AtomicBool = AtomicBool::new(false);

/// Directory for supervisor coordination files (PID and authorized exit marker).
pub fn supervisor_dir() -> PathBuf {
    if let Some(proj) = directories::ProjectDirs::from("com", "MeritScreen", "MeritScreen") {
        let dir = proj.config_dir().join("supervisor");
        let _ = std::fs::create_dir_all(&dir);
        dir
    } else {
        let dir = std::env::temp_dir().join("meritscreen_supervisor");
        let _ = std::fs::create_dir_all(&dir);
        dir
    }
}

fn authorized_exit_flag_path() -> PathBuf {
    supervisor_dir().join("authorized_exit.flag")
}

fn supervisor_pid_path() -> PathBuf {
    supervisor_dir().join("supervisor.pid")
}

/// Mark that a verified Parent PIN has authorized the application to exit cleanly.
pub fn mark_authorized_exit() {
    AUTHORIZED_EXIT.store(true, Ordering::SeqCst);
    let path = authorized_exit_flag_path();
    let _ = std::fs::write(&path, format!("{}", std::process::id()));
    tracing::info!(path = ?path, "Marked authorized exit via Parent PIN");
}

/// Clear any lingering authorized exit flag from a previous session.
pub fn clear_authorized_exit_flag() {
    AUTHORIZED_EXIT.store(false, Ordering::SeqCst);
    let path = authorized_exit_flag_path();
    if path.exists() {
        let _ = std::fs::remove_file(path);
    }
}

/// Check whether the parent PIN authorized this exit.
pub fn is_authorized_exit_flag_present() -> bool {
    authorized_exit_flag_path().exists()
}

/// Check whether a process with the given PID is currently alive.
pub fn is_process_alive(pid: u32) -> bool {
    if pid == 0 {
        return false;
    }
    #[cfg(unix)]
    {
        // kill -0 sends no signal, but performs error checking (returns 0 if process exists)
        std::process::Command::new("kill")
            .args(["-0", &pid.to_string()])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
    }
    #[cfg(windows)]
    {
        std::process::Command::new("cmd")
            .args(["/C", &format!("tasklist /FI \"PID eq {pid}\" | findstr {pid}")])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
    }
    #[cfg(not(any(unix, windows)))]
    {
        false
    }
}

/// Check if a supervisor process is currently alive.
pub fn is_supervisor_running() -> bool {
    let pid_path = supervisor_pid_path();
    if let Ok(content) = std::fs::read_to_string(&pid_path) {
        if let Ok(pid) = content.trim().parse::<u32>() {
            if is_process_alive(pid) {
                return true;
            }
        }
    }
    false
}

/// Record the supervisor PID to file.
fn record_supervisor_pid(pid: u32) {
    let _ = std::fs::write(supervisor_pid_path(), pid.to_string());
}

/// Spawn the detached watchdog supervisor if it is not already running.
pub fn spawn_supervisor_if_needed() {
    // If an authorized exit was signaled, don't respawn
    if is_authorized_exit_flag_present() {
        return;
    }

    if is_supervisor_running() {
        return;
    }

    let Ok(current_exe) = std::env::current_exe() else {
        return;
    };
    let current_pid = std::process::id();

    let mut cmd = std::process::Command::new(current_exe);
    cmd.arg("--watchdog-supervisor").arg(current_pid.to_string());

    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        // Run in a new process group so killing parent UI won't kill supervisor
        cmd.process_group(0);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const DETACHED_PROCESS: u32 = 0x00000008;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        cmd.creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP);
    }

    match cmd.spawn() {
        Ok(child) => {
            record_supervisor_pid(child.id());
            tracing::info!(
                supervisor_pid = child.id(),
                target_pid = current_pid,
                "Spawned background watchdog supervisor for child session resilience"
            );
        }
        Err(e) => {
            tracing::warn!(error = %e, "Failed to spawn watchdog supervisor");
        }
    }
}

/// Lightweight supervisor loop executed when binary is invoked with `--watchdog-supervisor <target_pid>`.
pub fn run_watchdog_supervisor(mut target_pid: u32) {
    let current_pid = std::process::id();
    record_supervisor_pid(current_pid);
    tracing::info!(
        supervisor_pid = current_pid,
        target_pid,
        "Watchdog supervisor started: monitoring child protection UI"
    );

    let current_exe = match std::env::current_exe() {
        Ok(exe) => exe,
        Err(e) => {
            tracing::error!(error = %e, "Supervisor cannot resolve current executable path");
            return;
        }
    };

    loop {
        std::thread::sleep(std::time::Duration::from_millis(1500));

        // 1. If Parent PIN authorized exit, terminate the supervisor cleanly
        if is_authorized_exit_flag_present() {
            tracing::info!("Watchdog supervisor exiting: detected Parent PIN authorized exit flag");
            clear_authorized_exit_flag();
            break;
        }

        // 2. Check if the monitored UI process is still alive
        if !is_process_alive(target_pid) {
            // Re-check authorized exit right after death
            std::thread::sleep(std::time::Duration::from_millis(300));
            if is_authorized_exit_flag_present() {
                tracing::info!("Watchdog supervisor exiting: verified Parent PIN authorized exit");
                clear_authorized_exit_flag();
                break;
            }

            // Monitored process died or was killed without Parent PIN!
            tracing::warn!(
                killed_pid = target_pid,
                "Child protection UI was terminated unexpectedly (SIGKILL / Force Quit / Task Manager)! Respawning in background..."
            );

            let mut cmd = std::process::Command::new(&current_exe);
            #[cfg(unix)]
            {
                use std::os::unix::process::CommandExt;
                cmd.process_group(0);
            }
            #[cfg(windows)]
            {
                use std::os::windows::process::CommandExt;
                const DETACHED_PROCESS: u32 = 0x00000008;
                const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
                cmd.creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP);
            }

            match cmd.spawn() {
                Ok(child) => {
                    target_pid = child.id();
                    tracing::info!(
                        new_target_pid = target_pid,
                        "Successfully respawned Watching child protection process"
                    );
                }
                Err(e) => {
                    tracing::error!(error = %e, "Failed to respawn child protection process");
                    std::thread::sleep(std::time::Duration::from_secs(3));
                }
            }
        }
    }

    let _ = std::fs::remove_file(supervisor_pid_path());
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_current_process_is_alive() {
        let my_pid = std::process::id();
        assert!(is_process_alive(my_pid), "Current process should be detected as alive");
    }

    #[test]
    fn test_nonexistent_process_is_dead() {
        // PID 0 or impossibly high PID should return false
        assert!(!is_process_alive(0));
        assert!(!is_process_alive(99_999_999));
    }

    #[test]
    fn test_authorized_exit_flag_lifecycle() {
        clear_authorized_exit_flag();
        assert!(!is_authorized_exit_flag_present());

        mark_authorized_exit();
        assert!(is_authorized_exit_flag_present());
        assert!(AUTHORIZED_EXIT.load(Ordering::SeqCst));

        clear_authorized_exit_flag();
        assert!(!is_authorized_exit_flag_present());
    }
}
