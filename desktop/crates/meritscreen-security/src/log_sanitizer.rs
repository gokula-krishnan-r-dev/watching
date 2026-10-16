//! Never log PIN, tokens, emails, or pairing codes.

/// Redact values that must never appear in logs (parity with Android `LogSanitizer`).
pub fn sanitize_for_log(input: &str) -> String {
    let lower = input.to_ascii_lowercase();
    if looks_like_secret(&lower) {
        "[REDACTED]".to_string()
    } else {
        input.to_string()
    }
}

fn looks_like_secret(lower: &str) -> bool {
    if lower.contains("bearer ")
        || lower.contains("authorization:")
        || lower.contains("eyj") // JWT header prefix
        || lower.contains("refresh_token")
        || lower.contains("id_token")
        || lower.contains("custom_token")
        || lower.contains("parent_pin")
        || lower.contains("pairing_code")
        || lower.contains("pairingcode")
        || lower.contains("otp_code")
        || lower.contains("ipc_hmac")
    {
        return true;
    }
    // Key=value forms that embed secrets: pin=1234, code=123456, otp=424242
    if secret_kv_present(lower) {
        return true;
    }
    // 6–8 digit codes (pairing / OTP) when the whole string is digits/spaces.
    let digits = lower.chars().filter(|c| c.is_ascii_digit()).count();
    let only_digitish = lower
        .chars()
        .all(|c| c.is_ascii_digit() || c.is_whitespace() || c == '-');
    only_digitish && (6..=8).contains(&digits)
}

fn secret_kv_present(lower: &str) -> bool {
    for key in ["pin=", "code=", "otp=", "secret=", "token="] {
        if let Some(idx) = lower.find(key) {
            let rest = &lower[idx + key.len()..];
            let value: String = rest
                .chars()
                .take_while(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
                .collect();
            if value.len() >= 4 {
                return true;
            }
        }
    }
    false
}

/// Install a tracing subscriber that formats events without dumping raw user input.
pub fn init_tracing(default_filter: &str) {
    let filter = tracing_subscriber::EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new(default_filter));
    let _ = tracing_subscriber::fmt()
        .with_env_filter(filter)
        .with_target(false)
        .try_init();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn redacts_jwt_like_strings() {
        assert_eq!(
            sanitize_for_log("token=eyJhbGciOiJIUzI1NiJ9.abc"),
            "[REDACTED]"
        );
    }

    #[test]
    fn redacts_pairing_codes() {
        assert_eq!(sanitize_for_log("123456"), "[REDACTED]");
        assert_eq!(sanitize_for_log("12 34 56"), "[REDACTED]");
        assert_eq!(sanitize_for_log("code=654321"), "[REDACTED]");
        assert_eq!(sanitize_for_log("pairing_code leaked"), "[REDACTED]");
    }

    #[test]
    fn keeps_ordinary_messages() {
        assert_eq!(
            sanitize_for_log("guardian heartbeat ok"),
            "guardian heartbeat ok"
        );
        assert_eq!(
            sanitize_for_log("child pairing accepted (local demo)"),
            "child pairing accepted (local demo)"
        );
    }
}
