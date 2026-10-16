//! Screen state machine used by every UI surface.

use crate::error::AppError;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "state", content = "data")]
pub enum UiState<T> {
    Loading,
    Empty,
    Success(T),
    Error(AppError),
}

impl<T> UiState<T> {
    pub fn is_loading(&self) -> bool {
        matches!(self, Self::Loading)
    }

    pub fn is_empty(&self) -> bool {
        matches!(self, Self::Empty)
    }

    pub fn map<U>(self, f: impl FnOnce(T) -> U) -> UiState<U> {
        match self {
            Self::Loading => UiState::Loading,
            Self::Empty => UiState::Empty,
            Self::Success(v) => UiState::Success(f(v)),
            Self::Error(e) => UiState::Error(e),
        }
    }
}
