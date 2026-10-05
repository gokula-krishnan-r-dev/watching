//! Appearance preference — System / Light / Dark (Android + iOS parity).

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Appearance {
    #[default]
    System,
    Light,
    Dark,
}

impl Appearance {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::System => "system",
            Self::Light => "light",
            Self::Dark => "dark",
        }
    }

    pub fn parse(raw: &str) -> Self {
        match raw.trim().to_ascii_lowercase().as_str() {
            "light" => Self::Light,
            "dark" => Self::Dark,
            _ => Self::System,
        }
    }
}

/// Design tokens mapped from Android `:core:ui` Classic theme intent.
/// Feature screens must use CSS variables / these names — no one-off hex.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DesignTokens {
    pub background: &'static str,
    pub on_background: &'static str,
    pub surface: &'static str,
    pub on_surface: &'static str,
    pub on_surface_variant: &'static str,
    pub primary: &'static str,
    pub on_primary: &'static str,
    pub primary_container: &'static str,
    pub secondary_container: &'static str,
    pub outline: &'static str,
    pub error: &'static str,
    pub success: &'static str,
}

pub const LIGHT_TOKENS: DesignTokens = DesignTokens {
    background: "#FCF9F4",
    on_background: "#1C1C19",
    surface: "#FCF9F4",
    on_surface: "#1C1C19",
    on_surface_variant: "#3F4947",
    primary: "#00514D",
    on_primary: "#FFFFFF",
    primary_container: "#0F6B66",
    secondary_container: "#EEDDC7",
    outline: "#6F7978",
    error: "#BA1A1A",
    success: "#2F6B45",
};

pub const DARK_TOKENS: DesignTokens = DesignTokens {
    background: "#1A2124",
    on_background: "#E4EEEC",
    surface: "#1A2124",
    on_surface: "#E4EEEC",
    on_surface_variant: "#B5C2BF",
    primary: "#7ED4CC",
    on_primary: "#003734",
    primary_container: "#0B5C58",
    secondary_container: "#3C3429",
    outline: "#3A4746",
    error: "#FFB4AB",
    success: "#9AD4AE",
};
