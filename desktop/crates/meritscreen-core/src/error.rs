//! User-facing errors. Never carry raw Firebase/API messages into the UI layer.

use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Clone, PartialEq, Eq, Error, Serialize, Deserialize)]
#[serde(tag = "kind", content = "message")]
pub enum AppError {
    #[error("{0}")]
    Network(String),
    #[error("{0}")]
    Auth(String),
    #[error("{0}")]
    Permission(String),
    #[error("{0}")]
    NotFound(String),
    #[error("{0}")]
    Validation(String),
    #[error("{0}")]
    OfflinePolicy(String),
    #[error("{0}")]
    Unknown(String),
}

impl AppError {
    pub fn network() -> Self {
        Self::Network("Check your connection and try again.".into())
    }

    pub fn auth() -> Self {
        Self::Auth("Please sign in again to continue.".into())
    }

    pub fn auth_msg(message: impl Into<String>) -> Self {
        Self::Auth(message.into())
    }

    pub fn permission() -> Self {
        Self::Permission("Watching needs a permission to continue.".into())
    }

    pub fn not_found() -> Self {
        Self::NotFound("We couldn't find that information.".into())
    }

    pub fn not_found_msg(message: impl Into<String>) -> Self {
        Self::NotFound(message.into())
    }

    pub fn unavailable(message: impl Into<String>) -> Self {
        Self::Unknown(message.into())
    }

    pub fn offline_policy() -> Self {
        Self::OfflinePolicy("Using saved settings until the device is back online.".into())
    }

    pub fn unknown() -> Self {
        Self::Unknown("Something went wrong. Please try again.".into())
    }

    pub fn validation(message: impl Into<String>) -> Self {
        Self::Validation(message.into())
    }

    pub fn user_message(&self) -> &str {
        match self {
            Self::Network(m)
            | Self::Auth(m)
            | Self::Permission(m)
            | Self::NotFound(m)
            | Self::Validation(m)
            | Self::OfflinePolicy(m)
            | Self::Unknown(m) => m,
        }
    }
}
