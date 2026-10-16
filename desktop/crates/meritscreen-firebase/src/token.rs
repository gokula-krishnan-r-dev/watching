//! ID-token freshness helpers shared by the parent session and the Guardian sync loop.
//! Firebase ID tokens expire after one hour; persisted sessions outlive that, so callers
//! must check expiry before reusing a cached token.

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use serde_json::Value;

/// Refresh this long before the token's `exp`.
pub const TOKEN_SKEW_MS: i64 = 120_000;

/// `exp` claim (epoch ms) of a JWT, decoded without verification (only to decide when to refresh).
pub fn jwt_exp_ms(token: &str) -> Option<i64> {
    let payload = token.split('.').nth(1)?;
    let bytes = URL_SAFE_NO_PAD.decode(payload.trim_end_matches('=')).ok()?;
    let v: Value = serde_json::from_slice(&bytes).ok()?;
    v.get("exp")?.as_i64().map(|s| s * 1000)
}

/// True unless the token is a JWT whose `exp` falls within [`TOKEN_SKEW_MS`] of `now_ms`.
/// Opaque tokens are treated as fresh; the server answers 401 and callers retry.
pub fn token_is_fresh(token: &str, now_ms: i64) -> bool {
    !token.trim().is_empty() && jwt_exp_ms(token).is_none_or(|exp| exp - TOKEN_SKEW_MS > now_ms)
}

/// Current wall clock in epoch ms.
pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn jwt(exp_s: i64) -> String {
        format!("h.{}.s", URL_SAFE_NO_PAD.encode(format!("{{\"exp\":{exp_s}}}")))
    }

    #[test]
    fn freshness() {
        let now = 1_800_000_000_000;
        assert!(token_is_fresh(&jwt(now / 1000 + 3600), now));
        assert!(!token_is_fresh(&jwt(now / 1000 - 10), now));
        assert!(!token_is_fresh(&jwt(now / 1000 + 60), now));
        assert!(token_is_fresh("opaque", now));
        assert!(!token_is_fresh("", now));
        assert_eq!(jwt_exp_ms("not-a-jwt"), None);
    }
}
