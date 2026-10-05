//! Guardian performance smoke helpers (d10 / docs/15 §15).

use meritscreen_core::app_config::{GUARDIAN_RSS_BUDGET_DEBUG_MB, GUARDIAN_RSS_BUDGET_MB};

/// Current process RSS in megabytes (best-effort).
pub fn current_rss_mb() -> Option<u64> {
    #[cfg(unix)]
    {
        let pid = std::process::id();
        let out = std::process::Command::new("ps")
            .args(["-o", "rss=", "-p", &pid.to_string()])
            .output()
            .ok()?;
        let s = String::from_utf8_lossy(&out.stdout);
        let kb: u64 = s.trim().parse().ok()?;
        Some(kb / 1024)
    }
    #[cfg(windows)]
    {
        windows_rss_mb()
    }
    #[cfg(not(any(unix, windows)))]
    {
        None
    }
}

pub fn rss_budget_mb() -> u32 {
    if cfg!(debug_assertions) {
        GUARDIAN_RSS_BUDGET_DEBUG_MB
    } else {
        GUARDIAN_RSS_BUDGET_MB
    }
}

/// Returns Ok(rss_mb) when under budget; Err with message otherwise.
pub fn assert_rss_under_budget() -> Result<u64, String> {
    let Some(rss) = current_rss_mb() else {
        return Err("could not sample RSS".into());
    };
    let budget = u64::from(rss_budget_mb());
    if rss > budget {
        return Err(format!("Guardian RSS {rss} MB exceeds budget {budget} MB"));
    }
    Ok(rss)
}

#[cfg(windows)]
fn windows_rss_mb() -> Option<u64> {
    use windows::Win32::System::ProcessStatus::{
        GetProcessMemoryInfo, PROCESS_MEMORY_COUNTERS,
    };
    use windows::Win32::System::Threading::GetCurrentProcess;
    unsafe {
        let mut counters = PROCESS_MEMORY_COUNTERS::default();
        counters.cb = std::mem::size_of::<PROCESS_MEMORY_COUNTERS>() as u32;
        GetProcessMemoryInfo(GetCurrentProcess(), &mut counters, counters.cb).ok()?;
        Some((counters.WorkingSetSize as u64) / (1024 * 1024))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rss_sample_succeeds_on_supported_os() {
        let rss = current_rss_mb().expect("rss");
        assert!(rss > 0, "rss should be positive");
        assert_rss_under_budget().expect("under debug/release budget");
    }
}
