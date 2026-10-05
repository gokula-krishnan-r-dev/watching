//! Active-use clock predicates (idle + foreground).

use meritscreen_core::app_config::IDLE_THRESHOLD_SECONDS;

/// True when the child is actively using a tracked (non-idle) session.
pub fn is_active_use(idle_seconds: u32, foreground_counts_as_use: bool) -> bool {
    is_active_use_with_threshold(
        idle_seconds,
        IDLE_THRESHOLD_SECONDS,
        foreground_counts_as_use,
    )
}

pub fn is_active_use_with_threshold(
    idle_seconds: u32,
    threshold_seconds: u32,
    foreground_counts_as_use: bool,
) -> bool {
    foreground_counts_as_use && idle_seconds < threshold_seconds
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn idle_pauses_clock() {
        assert!(!is_active_use_with_threshold(200, 150, true));
        assert!(is_active_use_with_threshold(10, 150, true));
        assert!(!is_active_use_with_threshold(10, 150, false));
    }
}
