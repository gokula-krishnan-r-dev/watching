//! Parent system use cases — auth, family, policy, pairing.
//!
//! Firebase access is behind traits (`meritscreen-firebase`). The Tauri UI host
//! calls these use cases from Rust commands; the WebView never imports Firebase.
//! Child launcher/quiz path must not use this crate.

#![forbid(unsafe_code)]

pub mod models;
pub mod oauth;
pub mod session;

pub use models::{
    show_device_interval, ChildCard, ChildDetail, DashboardSnapshot, DeviceEnforcementStatus,
    InventoryApp, NotificationPrefs, PairingOffer, ParentProfile, MAX_CHILDREN,
};
pub use oauth::{
    apple_sign_in_available, apple_sign_in_stub, generate_pkce, google_auth_url,
    google_sign_in_stub, PkceChallenge,
};
pub use session::ParentSession;
