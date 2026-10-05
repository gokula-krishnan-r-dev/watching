//! Length-prefixed JSON framing (4-byte little-endian length + body [+ HMAC]).
//!
//! Authenticated frames (d9): `len || payload || hmac_sha256(key, payload)`.

use std::io::{Read, Write};

use thiserror::Error;

use crate::auth::{self, allow_insecure_ipc, IpcHmacKey, HMAC_LEN};
use crate::protocol::{IpcRequest, IpcResponse};

const MAX_FRAME: u32 = 1_048_576;

#[derive(Debug, Error)]
pub enum IpcError {
    #[error("protocol mismatch: got {got}, expected {expected}")]
    VersionMismatch { got: u16, expected: u16 },
    #[error("decode error: {0}")]
    Decode(String),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("frame too large: {0} bytes")]
    FrameTooLarge(u32),
    #[error("auth error: {0}")]
    Auth(String),
}

pub fn encode_request(req: &IpcRequest) -> Result<Vec<u8>, IpcError> {
    serde_json::to_vec(req).map_err(|e| IpcError::Decode(e.to_string()))
}

pub fn decode_request(bytes: &[u8]) -> Result<IpcRequest, IpcError> {
    serde_json::from_slice(bytes).map_err(|e| IpcError::Decode(e.to_string()))
}

pub fn encode_response(resp: &IpcResponse) -> Result<Vec<u8>, IpcError> {
    serde_json::to_vec(resp).map_err(|e| IpcError::Decode(e.to_string()))
}

pub fn decode_response(bytes: &[u8]) -> Result<IpcResponse, IpcError> {
    serde_json::from_slice(bytes).map_err(|e| IpcError::Decode(e.to_string()))
}

pub fn write_frame(mut w: impl Write, payload: &[u8]) -> Result<(), IpcError> {
    write_frame_with_key(&mut w, payload, None)
}

pub fn write_frame_with_key(
    mut w: impl Write,
    payload: &[u8],
    key: Option<&IpcHmacKey>,
) -> Result<(), IpcError> {
    let len = payload.len() as u32;
    if len > MAX_FRAME {
        return Err(IpcError::FrameTooLarge(len));
    }
    w.write_all(&len.to_le_bytes())?;
    w.write_all(payload)?;
    if let Some(k) = key {
        let mac = auth::sign(k, payload);
        w.write_all(&mac)?;
    } else if !allow_insecure_ipc() {
        return Err(IpcError::Auth(
            "HMAC key required (set MERITSCREEN_IPC_HMAC_DISABLE=1 only in lab)".into(),
        ));
    }
    w.flush()?;
    Ok(())
}

pub fn read_frame(mut r: impl Read) -> Result<Vec<u8>, IpcError> {
    read_frame_with_key(&mut r, None)
}

pub fn read_frame_with_key(
    mut r: impl Read,
    key: Option<&IpcHmacKey>,
) -> Result<Vec<u8>, IpcError> {
    let mut len_buf = [0u8; 4];
    r.read_exact(&mut len_buf)?;
    let len = u32::from_le_bytes(len_buf);
    if len > MAX_FRAME {
        return Err(IpcError::FrameTooLarge(len));
    }
    let mut buf = vec![0u8; len as usize];
    r.read_exact(&mut buf)?;
    if let Some(k) = key {
        let mut mac = [0u8; HMAC_LEN];
        r.read_exact(&mut mac)?;
        auth::verify(k, &buf, &mac).map_err(|e| IpcError::Auth(e.to_string()))?;
    } else if !allow_insecure_ipc() {
        return Err(IpcError::Auth("HMAC key required".into()));
    }
    Ok(buf)
}

pub fn write_request(w: impl Write, req: &IpcRequest) -> Result<(), IpcError> {
    write_frame(w, &encode_request(req)?)
}

pub fn write_request_signed(
    w: impl Write,
    req: &IpcRequest,
    key: &IpcHmacKey,
) -> Result<(), IpcError> {
    write_frame_with_key(w, &encode_request(req)?, Some(key))
}

pub fn read_request(r: impl Read) -> Result<IpcRequest, IpcError> {
    decode_request(&read_frame(r)?)
}

pub fn read_request_signed(r: impl Read, key: &IpcHmacKey) -> Result<IpcRequest, IpcError> {
    decode_request(&read_frame_with_key(r, Some(key))?)
}

pub fn write_response(w: impl Write, resp: &IpcResponse) -> Result<(), IpcError> {
    write_frame(w, &encode_response(resp)?)
}

pub fn write_response_signed(
    w: impl Write,
    resp: &IpcResponse,
    key: &IpcHmacKey,
) -> Result<(), IpcError> {
    write_frame_with_key(w, &encode_response(resp)?, Some(key))
}

pub fn read_response(r: impl Read) -> Result<IpcResponse, IpcError> {
    decode_response(&read_frame(r)?)
}

pub fn read_response_signed(r: impl Read, key: &IpcHmacKey) -> Result<IpcResponse, IpcError> {
    decode_response(&read_frame_with_key(r, Some(key))?)
}
