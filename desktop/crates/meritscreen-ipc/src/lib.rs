//! Versioned IPC between Guardian, Session Agent, and Tauri UI.
//!
//! Transport: local socket (Windows named pipe / Unix domain socket).
//! Framing: 4-byte LE length + JSON + HMAC-SHA256 (d9).

pub mod auth;
pub mod framing;
pub mod peer;
pub mod protocol;
pub mod transport;

pub use auth::{allow_insecure_ipc, IpcHmacKey};
pub use framing::{
    decode_request, decode_response, encode_request, encode_response, read_frame,
    read_frame_with_key, write_frame, write_frame_with_key, IpcError,
};
pub use peer::{authorize_peer, note_accepted_peer, peer_identity_from_stream, PeerIdentity};
pub use protocol::{IpcRequest, IpcResponse, UiSnapshot, ENDPOINT_NAME, IPC_PROTOCOL_VERSION};
pub use transport::{IpcConn, IpcServer};

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn ping_round_trips() {
        let bytes = encode_request(&IpcRequest::Ping).unwrap();
        assert_eq!(decode_request(&bytes).unwrap(), IpcRequest::Ping);
    }

    #[test]
    fn snapshot_default_is_unassigned() {
        let snap = UiSnapshot::default();
        assert_eq!(snap.role, meritscreen_core::DeviceRole::Unassigned);
        assert_eq!(snap.appearance, meritscreen_core::Appearance::System);
    }

    #[test]
    fn signed_frame_round_trip() {
        let key = IpcHmacKey::from_bytes([9u8; 32]);
        let payload = encode_request(&IpcRequest::AgentHeartbeat { pid: 42 }).unwrap();
        let mut buf = Vec::new();
        write_frame_with_key(&mut buf, &payload, Some(&key)).unwrap();
        let mut cur = Cursor::new(buf);
        let got = read_frame_with_key(&mut cur, Some(&key)).unwrap();
        assert_eq!(got, payload);
    }

    #[test]
    fn spoofed_mac_fails_closed() {
        let key = IpcHmacKey::from_bytes([1u8; 32]);
        let other = IpcHmacKey::from_bytes([2u8; 32]);
        let payload = encode_request(&IpcRequest::Ping).unwrap();
        let mut buf = Vec::new();
        write_frame_with_key(&mut buf, &payload, Some(&key)).unwrap();
        let mut cur = Cursor::new(buf);
        let err = read_frame_with_key(&mut cur, Some(&other)).unwrap_err();
        assert!(matches!(err, IpcError::Auth(_)));
    }

    #[test]
    fn unsigned_frame_rejected_when_key_required() {
        std::env::remove_var("MERITSCREEN_IPC_HMAC_DISABLE");
        let payload = b"ping";
        let mut buf = Vec::new();
        // length + payload, no mac
        let len = payload.len() as u32;
        buf.extend_from_slice(&len.to_le_bytes());
        buf.extend_from_slice(payload);
        let key = IpcHmacKey::from_bytes([3u8; 32]);
        let mut cur = Cursor::new(buf);
        assert!(read_frame_with_key(&mut cur, Some(&key)).is_err());
    }
}
