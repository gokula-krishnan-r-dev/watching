//! Peer identity helpers for local IPC (d4 + d9).
//!
//! Unix: `LOCAL_PEERCRED` / `SO_PEERCRED` (+ macOS `LOCAL_PEERPID`).
//! Windows: PID + MeritScreen image-path check (relax with env for tests).
//! Frame MAC is enforced separately in `auth` / framing.

use std::io;

use tracing::{debug, warn};

/// Best-effort peer metadata after `accept`.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct PeerIdentity {
    /// Peer process id when the platform exposes it.
    pub pid: Option<u32>,
    /// Peer effective user id when available (Unix).
    pub uid: Option<u32>,
}

/// Log peer identity for an accepted connection.
pub fn note_accepted_peer(peer: PeerIdentity) {
    debug!(?peer, "ipc peer accepted");
}

#[cfg(windows)]
fn peer_path_relax() -> bool {
    std::env::var("MERITSCREEN_IPC_PEER_RELAX").ok().as_deref() == Some("1")
}

/// Whether this peer is allowed to talk to Guardian.
///
/// Rules:
/// - Must present local peer credentials (fail closed if missing on Unix).
/// - Peer euid must equal Guardian euid, **or** Guardian is root (LaunchDaemon)
///   and peer is a non-root local user (Session Agent / UI).
/// - Windows: PID required; image path must contain `meritscreen` unless
///   `MERITSCREEN_IPC_PEER_RELAX=1` (integration tests).
pub fn authorize_peer(peer: PeerIdentity) -> bool {
    #[cfg(unix)]
    {
        let Some(peer_uid) = peer.uid else {
            warn!("rejecting ipc peer without uid credentials");
            return false;
        };
        let self_uid = unsafe { libc::geteuid() };
        if peer_uid == self_uid {
            return true;
        }
        if self_uid == 0 && peer_uid != 0 {
            return true;
        }
        warn!(peer_uid, self_uid, "rejecting ipc peer uid mismatch");
        false
    }
    #[cfg(windows)]
    {
        let Some(pid) = peer.pid else {
            warn!("rejecting ipc peer without process id");
            return false;
        };
        if peer_path_relax() {
            return true;
        }
        match windows_peer_image_ok(pid) {
            Ok(true) => true,
            Ok(false) => {
                warn!(pid, "rejecting ipc peer — image path not MeritScreen");
                false
            }
            Err(e) => {
                warn!(pid, error = %e, "rejecting ipc peer — cannot resolve image path");
                false
            }
        }
    }
    #[cfg(not(any(unix, windows)))]
    {
        let _ = peer;
        true
    }
}

#[cfg(windows)]
fn windows_peer_image_ok(pid: u32) -> io::Result<bool> {
    use std::os::windows::ffi::OsStringExt;
    use windows::Win32::Foundation::{CloseHandle, HANDLE};
    use windows::Win32::System::Threading::{
        OpenProcess, QueryFullProcessImageNameW, PROCESS_NAME_WIN32, PROCESS_QUERY_LIMITED_INFORMATION,
    };

    unsafe {
        let handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid)
            .map_err(|e| io::Error::other(e))?;
        let mut buf = [0u16; 1024];
        let mut size = buf.len() as u32;
        let ok = QueryFullProcessImageNameW(
            HANDLE(handle.0),
            PROCESS_NAME_WIN32,
            windows::core::PWSTR(buf.as_mut_ptr()),
            &mut size,
        );
        let _ = CloseHandle(handle);
        if ok.is_err() {
            return Err(io::Error::other("QueryFullProcessImageNameW failed"));
        }
        let path = std::ffi::OsString::from_wide(&buf[..size as usize]);
        let lower = path.to_string_lossy().to_ascii_lowercase();
        Ok(lower.contains("meritscreen"))
    }
}

/// Read peer credentials from an accepted local-socket stream.
pub fn peer_identity_from_stream(
    stream: &interprocess::local_socket::Stream,
) -> io::Result<PeerIdentity> {
    use interprocess::local_socket::traits::StreamCommon;
    let creds = stream.peer_creds()?;
    let mut identity = PeerIdentity {
        pid: creds.pid().map(|p| p as u32),
        uid: None,
    };
    #[cfg(unix)]
    {
        identity.uid = creds.euid();
        // Darwin xucred exposes euid/groups, not pid. Authorization is UID + HMAC.
    }
    let _ = stream;
    Ok(identity)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn authorize_same_uid() {
        #[cfg(unix)]
        {
            let uid = unsafe { libc::geteuid() } as u32;
            assert!(authorize_peer(PeerIdentity {
                pid: Some(1),
                uid: Some(uid),
            }));
        }
        #[cfg(windows)]
        {
            std::env::set_var("MERITSCREEN_IPC_PEER_RELAX", "1");
            assert!(authorize_peer(PeerIdentity {
                pid: Some(1),
                uid: None,
            }));
            assert!(!authorize_peer(PeerIdentity::default()));
        }
    }

    #[test]
    fn authorize_rejects_missing_unix_uid() {
        #[cfg(unix)]
        {
            assert!(!authorize_peer(PeerIdentity {
                pid: Some(1),
                uid: None,
            }));
        }
    }
}
