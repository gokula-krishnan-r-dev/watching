//! Desktop enforcement — inventory, L3 decisions, active-use clock, tamper/tier.
//!
//! Pure policy logic is OS-agnostic. Platform probes live in `platform` / `inventory`.
//! Guardian owns decisions; Agent samples foreground/idle and applies cover/terminate.
//! No Firebase on this path.

#![deny(unsafe_code)]

pub mod active_use;
pub mod clock;
pub mod gate;
pub mod inventory;
#[allow(unsafe_code)] // OS idle/foreground/terminate probes only (documented APIs).
pub mod platform;
pub mod protected;
pub mod strict;
pub mod types;

pub use active_use::is_active_use;
pub use clock::{ClockIntegrity, ClockObservation};
pub use gate::{decide_gate, GateAction, GateContext};
pub use inventory::{hash_inventory, scan_installed_apps, InstalledApp};
pub use protected::{is_protected_app_id, is_protected_process_name, PROTECTED_PROCESS_NAMES};
pub use strict::{strict_mode_availability, StrictAvailability};
pub use types::{
    EnforcementTier, ForegroundSample, GuardianRuntimeState, TamperFlags, DEGRADED_ADMIN,
    DEGRADED_STRICT_UNAVAILABLE,
};
