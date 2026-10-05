//! Parent PIN hashing (PBKDF2-HMAC-SHA256). Never log PIN or hash.

use pbkdf2::pbkdf2_hmac;
use rand::RngCore;
use sha2::Sha256;
use zeroize::Zeroizing;

const PBKDF2_ITERS: u32 = 100_000;
const SALT_LEN: usize = 16;
const KEY_LEN: usize = 32;

/// Returns `iters$salt_hex$hash_hex`.
pub fn hash_parent_pin(pin: &str) -> String {
    let mut salt = [0u8; SALT_LEN];
    rand::thread_rng().fill_bytes(&mut salt);
    let mut key = Zeroizing::new([0u8; KEY_LEN]);
    pbkdf2_hmac::<Sha256>(pin.as_bytes(), &salt, PBKDF2_ITERS, key.as_mut());
    format!(
        "{PBKDF2_ITERS}${}${}",
        hex::encode(salt),
        hex::encode(key.as_ref())
    )
}

pub fn verify_parent_pin(pin: &str, encoded: &str) -> bool {
    let mut parts = encoded.split('$');
    let (Some(iters_s), Some(salt_hex), Some(hash_hex)) =
        (parts.next(), parts.next(), parts.next())
    else {
        return false;
    };
    let Ok(iters) = iters_s.parse::<u32>() else {
        return false;
    };
    let Ok(salt) = hex::decode(salt_hex) else {
        return false;
    };
    let Ok(expected) = hex::decode(hash_hex) else {
        return false;
    };
    let mut key = Zeroizing::new(vec![0u8; expected.len()]);
    pbkdf2_hmac::<Sha256>(pin.as_bytes(), &salt, iters, key.as_mut());
    if key.len() != expected.len() {
        return false;
    }
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
    }
}
