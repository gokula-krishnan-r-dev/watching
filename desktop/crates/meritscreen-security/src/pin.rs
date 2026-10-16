//! Parent PIN hashing (PBKDF2-HMAC-SHA256). Never log PIN or hash.

use pbkdf2::pbkdf2_hmac;
use rand::RngCore;
use sha2::Sha256;
use zeroize::Zeroizing;

use base64::engine::general_purpose::{STANDARD, STANDARD_NO_PAD};
use base64::Engine;

/// Matches Android `Pbkdf2PinHasher` (120k iterations, 16-byte salt, 256-bit key).
const PBKDF2_ITERS: u32 = 120_000;
const SALT_LEN: usize = 16;
const KEY_LEN: usize = 32;
const ANDROID_PREFIX: &str = "pbkdf2";

/// Returns the cross-platform format shared with Android/iOS:
/// `pbkdf2$<iters>$<salt base64 no-pad>$<hash base64 no-pad>`.
pub fn hash_parent_pin(pin: &str) -> String {
    let mut salt = [0u8; SALT_LEN];
    rand::thread_rng().fill_bytes(&mut salt);
    let mut key = Zeroizing::new([0u8; KEY_LEN]);
    pbkdf2_hmac::<Sha256>(pin.as_bytes(), &salt, PBKDF2_ITERS, key.as_mut());
    format!(
        "{ANDROID_PREFIX}${PBKDF2_ITERS}${}${}",
        STANDARD_NO_PAD.encode(salt),
        STANDARD_NO_PAD.encode(key.as_ref())
    )
}

fn decode_b64(v: &str) -> Option<Vec<u8>> {
    STANDARD_NO_PAD
        .decode(v.trim_end_matches('='))
        .ok()
        .or_else(|| STANDARD.decode(v).ok())
}

/// Verifies both the shared Android format (`pbkdf2$iters$b64$b64`) and the
/// legacy desktop format (`iters$salt_hex$hash_hex`).
pub fn verify_parent_pin(pin: &str, encoded: &str) -> bool {
    let parts: Vec<&str> = encoded.split('$').collect();
    let (iters_s, salt, expected) = match parts.as_slice() {
        [prefix, iters, salt, hash] if *prefix == ANDROID_PREFIX => {
            let (Some(s), Some(h)) = (decode_b64(salt), decode_b64(hash)) else {
                return false;
            };
            (*iters, s, h)
        }
        [iters, salt, hash] => {
            let (Ok(s), Ok(h)) = (hex::decode(salt), hex::decode(hash)) else {
                return false;
            };
            (*iters, s, h)
        }
        _ => return false,
    };
    let Ok(iters) = iters_s.parse::<u32>() else {
        return false;
    };
    if iters == 0 || expected.is_empty() {
        return false;
    }
    let mut key = Zeroizing::new(vec![0u8; expected.len()]);
    pbkdf2_hmac::<Sha256>(pin.as_bytes(), &salt, iters, key.as_mut());
    key.iter()
        .zip(expected.iter())
        .fold(0u8, |acc, (a, b)| acc | (a ^ b))
        == 0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pin_round_trip() {
        let h = hash_parent_pin("1234");
        assert!(verify_parent_pin("1234", &h));
        assert!(!verify_parent_pin("0000", &h));
        assert!(h.starts_with("pbkdf2$120000$"));
    }

    #[test]
    fn verifies_legacy_desktop_format() {
        let salt = [3u8; 16];
        let mut key = [0u8; 32];
        pbkdf2_hmac::<Sha256>(b"4321", &salt, 1000, &mut key);
        let legacy = format!("1000${}${}", hex::encode(salt), hex::encode(key));
        assert!(verify_parent_pin("4321", &legacy));
        assert!(!verify_parent_pin("1234", &legacy));
    }

    #[test]
    fn verifies_android_format() {
        // Same derivation Android uses (PBKDF2WithHmacSHA256, Base64 no padding).
        let salt = [9u8; 16];
        let mut key = [0u8; 32];
        pbkdf2_hmac::<Sha256>(b"2468", &salt, 2000, &mut key);
        let android = format!(
            "pbkdf2$2000${}${}",
            STANDARD_NO_PAD.encode(salt),
            STANDARD_NO_PAD.encode(key)
        );
        assert!(verify_parent_pin("2468", &android));
        assert!(!verify_parent_pin("2469", &android));
        assert!(!verify_parent_pin("2468", "garbage"));
    }
}
