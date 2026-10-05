//! Wall + monotonic integrity. Large wall rollback → fail closed.

use meritscreen_core::app_config::CLOCK_ROLLBACK_THRESHOLD_MS;

use crate::types::TamperFlags;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClockObservation {
    Ok { mono_elapsed_ms: i64 },
    Rollback,
}

#[derive(Debug, Clone, Default)]
pub struct ClockIntegrity {
    last_wall_ms: Option<i64>,
    last_mono_ms: Option<i64>,
    pub tamper: TamperFlags,
}

impl ClockIntegrity {
    pub fn observe(&mut self, wall_ms: i64, mono_ms: i64) -> ClockObservation {
        let mono_elapsed = match self.last_mono_ms {
            Some(prev) => (mono_ms - prev).max(0),
            None => 0,
        };

        if let Some(prev_wall) = self.last_wall_ms {
            let delta = wall_ms - prev_wall;
            if delta < -CLOCK_ROLLBACK_THRESHOLD_MS {
                self.tamper.clock_rollback = true;
                self.last_wall_ms = Some(wall_ms);
                self.last_mono_ms = Some(mono_ms);
                return ClockObservation::Rollback;
            }
        }

        self.last_wall_ms = Some(wall_ms);
        self.last_mono_ms = Some(mono_ms);
        ClockObservation::Ok {
            mono_elapsed_ms: mono_elapsed,
        }
    }

    pub fn elapsed_mono_ms(&self, now_mono_ms: i64) -> i64 {
        match self.last_mono_ms {
            Some(start) => (now_mono_ms - start).max(0),
            None => 0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_large_rollback() {
        let mut c = ClockIntegrity::default();
        assert!(matches!(
            c.observe(1_000_000, 100),
            ClockObservation::Ok { .. }
        ));
        assert_eq!(
            c.observe(1_000_000 - CLOCK_ROLLBACK_THRESHOLD_MS - 1, 200),
            ClockObservation::Rollback
        );
        assert!(c.tamper.clock_rollback);
    }

    #[test]
    fn small_skew_ok() {
        let mut c = ClockIntegrity::default();
        c.observe(1_000_000, 100);
        assert!(matches!(
            c.observe(1_000_000 - 1_000, 150),
            ClockObservation::Ok {
                mono_elapsed_ms: 50
            }
        ));
    }
}
