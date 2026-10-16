//! Persist Appearance preference locally (survives restart; no network).

use std::fs;
use std::path::{Path, PathBuf};

use meritscreen_core::Appearance;
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppearanceStoreError {
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("parse error: {0}")]
    Parse(#[from] serde_json::Error),
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct AppearanceFile {
    appearance: Appearance,
}

/// File-backed Appearance store under the app config directory.
#[derive(Debug, Clone)]
pub struct AppearanceStore {
    path: PathBuf,
}

impl AppearanceStore {
    pub fn in_dir(dir: impl AsRef<Path>) -> Self {
        Self {
            path: dir.as_ref().join("appearance.json"),
        }
    }

    /// Default location: platform config dir / MeritScreen / appearance.json
    pub fn default_location() -> Result<Self, AppearanceStoreError> {
        let base = directories::ProjectDirs::from("com", "MeritScreen", "MeritScreen").ok_or_else(
            || {
                AppearanceStoreError::Io(std::io::Error::new(
                    std::io::ErrorKind::NotFound,
                    "no project dirs",
                ))
            },
        )?;
        let dir = base.config_dir();
        fs::create_dir_all(dir)?;
        Ok(Self::in_dir(dir))
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn load(&self) -> Result<Appearance, AppearanceStoreError> {
        if !self.path.exists() {
            return Ok(Appearance::System);
        }
        let raw = fs::read_to_string(&self.path)?;
        let parsed: AppearanceFile = serde_json::from_str(&raw)?;
        Ok(parsed.appearance)
    }

    pub fn save(&self, appearance: Appearance) -> Result<(), AppearanceStoreError> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)?;
        }
        let body = AppearanceFile { appearance };
        let raw = serde_json::to_string_pretty(&body)?;
        fs::write(&self.path, raw)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn appearance_persists_across_reload() {
        let dir = tempdir().unwrap();
        let store = AppearanceStore::in_dir(dir.path());
        assert_eq!(store.load().unwrap(), Appearance::System);
        store.save(Appearance::Dark).unwrap();
        let store2 = AppearanceStore::in_dir(dir.path());
        assert_eq!(store2.load().unwrap(), Appearance::Dark);
        store2.save(Appearance::Light).unwrap();
        assert_eq!(store.load().unwrap(), Appearance::Light);
    }
}
