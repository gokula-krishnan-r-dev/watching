//! MeritScreen Guardian library — runtime used by the service binary and tests.

pub mod agent_spawn;
pub mod db_boot;
pub mod enforce;
pub mod perf;
pub mod runtime;
pub mod state;
pub mod sync;
pub mod uninstall;

#[cfg(test)]
pub(crate) mod test_env {
    use std::sync::{Mutex, MutexGuard};

    static ENV_LOCK: Mutex<()> = Mutex::new(());

    /// Serialize tests that mutate `MERITSCREEN_*` process env.
    pub fn lock() -> MutexGuard<'static, ()> {
        ENV_LOCK.lock().unwrap_or_else(|e| e.into_inner())
    }
}

#[cfg(windows)]
pub mod windows_service;

#[cfg(target_os = "macos")]
pub mod macos_service;
