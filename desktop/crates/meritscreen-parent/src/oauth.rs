//! Google loopback+PKCE and Sign in with Apple (macOS) stubs for desktop parent auth.

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use meritscreen_core::AppError;
use rand::RngCore;
use sha2::{Digest, Sha256};

#[derive(Debug, Clone)]
pub struct PkceChallenge {
    pub verifier: String,
    pub challenge: String,
    pub state: String,
}

pub fn generate_pkce() -> PkceChallenge {
    let mut raw = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut raw);
    let verifier = URL_SAFE_NO_PAD.encode(raw);
    let digest = Sha256::digest(verifier.as_bytes());
    let challenge = URL_SAFE_NO_PAD.encode(digest);
    let mut state_raw = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut state_raw);
    let state = URL_SAFE_NO_PAD.encode(state_raw);
    PkceChallenge {
        verifier,
        challenge,
        state,
    }
}

/// Build Google OAuth authorization URL (loopback redirect). Requires console client id.
pub fn google_auth_url(client_id: &str, redirect_uri: &str, pkce: &PkceChallenge) -> String {
    format!(
        "https://accounts.google.com/o/oauth2/v2/auth?\
         client_id={client_id}\
         &redirect_uri={}\
         &response_type=code\
         &scope=openid%20email%20profile\
         &code_challenge={}\
         &code_challenge_method=S256\
         &state={}",
        urlencoding_lite(redirect_uri),
        pkce.challenge,
        pkce.state
    )
}

/// Sign in with Apple — available on macOS parent builds when configured.
pub fn apple_sign_in_available() -> bool {
    cfg!(target_os = "macos")
}

pub fn apple_sign_in_stub() -> Result<String, AppError> {
    if !apple_sign_in_available() {
        return Err(AppError::validation(
            "Sign in with Apple is available on macOS MeritScreen builds.",
        ));
    }
    Err(AppError::unavailable(
        "Sign in with Apple needs the Apple Services ID from ops setup (docs/17 §5).",
    ))
}

pub fn google_sign_in_stub(client_id: Option<&str>) -> Result<String, AppError> {
    let Some(id) = client_id.filter(|s| !s.is_empty()) else {
        return Err(AppError::unavailable(
            "Google sign-in needs a desktop OAuth client id (loopback + PKCE).",
        ));
    };
    let pkce = generate_pkce();
    Ok(google_auth_url(
        id,
        "http://127.0.0.1:8787/oauth/callback",
        &pkce,
    ))
}

fn urlencoding_lite(s: &str) -> String {
    s.replace(':', "%3A")
        .replace('/', "%2F")
        .replace('?', "%3F")
        .replace('&', "%26")
        .replace('=', "%3D")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pkce_is_url_safe() {
        let p = generate_pkce();
        assert!(!p.verifier.is_empty());
        assert!(!p.challenge.is_empty());
        assert!(
            google_auth_url("cid", "http://127.0.0.1:8787/oauth/callback", &p)
                .contains("code_challenge_method=S256")
        );
    }
}
