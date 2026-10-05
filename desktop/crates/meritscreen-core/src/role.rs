use serde::{Deserialize, Serialize};

/// Device role gate — same three states as Android / iOS.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum DeviceRole {
    #[default]
    Unassigned,
    Parent,
    Child,
}

impl DeviceRole {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Unassigned => "unassigned",
            Self::Parent => "parent",
            Self::Child => "child",
        }
    }

    pub fn parse(raw: &str) -> Self {
        match raw.trim().to_ascii_lowercase().as_str() {
            "parent" => Self::Parent,
            "child" => Self::Child,
            _ => Self::Unassigned,
        }
    }
}
