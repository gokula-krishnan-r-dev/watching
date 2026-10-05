//! Child device local runtime — pairing credential, launcher, quiz, fail lock.
//!
//! **No Firebase dependency.** UI/child path reads this crate + optional Guardian IPC only.

#![forbid(unsafe_code)]

pub mod models;
pub mod runtime;

pub use models::{
    ChecklistItem, ChildScreen, ChildUiState, LauncherApp, QuizChoiceDto, QuizLockDto,
    QuizPromptDto,
};
pub use runtime::ChildRuntime;
