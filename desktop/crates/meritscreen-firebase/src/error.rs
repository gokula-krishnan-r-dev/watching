use meritscreen_core::AppError;
use serde_json::Value;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum FirebaseError {
    #[error("network error")]
    Network(#[source] reqwest::Error),
    #[error("auth error: {0}")]
    Auth(String),
    #[error("http {status}: {body}")]
    Http { status: u16, body: String },
    #[error("decode error: {0}")]
    Decode(String),
    #[error("{0}")]
    Other(String),
}

impl FirebaseError {
    pub fn to_app_error(&self) -> AppError {
        match self {
            Self::Network(_) => AppError::network(),
            Self::Auth(m) => AppError::Auth(m.clone()),
            Self::Http {
                status: 401 | 403,
                body,
            } => {
                if let Some(msg) = callable_error_message(body) {
                    AppError::auth_msg(msg)
                } else {
                    AppError::auth()
                }
            }
            Self::Http { status: 404, .. } => AppError::not_found(),
            Self::Http { body, .. } => {
                if let Some(msg) = callable_error_message(body) {
                    AppError::validation(msg)
                } else {
                    AppError::unknown()
                }
            }
            Self::Decode(_) => AppError::unknown(),
            Self::Other(m) => AppError::unavailable(m.clone()),
        }
    }
}

/// Extract user-facing text from Firebase callable / HTTPS error JSON.
fn callable_error_message(body: &str) -> Option<String> {
    let v: Value = serde_json::from_str(body).ok()?;
    let err = v.get("error")?;
    if let Some(msg) = err.get("message").and_then(|m| m.as_str()) {
        let msg = msg.trim();
        if !msg.is_empty() && msg.len() < 280 && !msg.starts_with('{') {
            return Some(msg.to_string());
        }
    }
    // Some gateways nest: error.status / error.message already handled above.
    None
}

pub type FirebaseResult<T> = Result<T, FirebaseError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_callable_https_error_message() {
        let err = FirebaseError::Http {
            status: 400,
            body: r#"{"error":{"message":"Please enter a valid email address.","status":"INVALID_ARGUMENT"}}"#.into(),
        };
        assert_eq!(
            err.to_app_error().user_message(),
            "Please enter a valid email address."
        );
    }
}
