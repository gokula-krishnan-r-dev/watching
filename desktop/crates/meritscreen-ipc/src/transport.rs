//! Local socket transport — Windows named pipe / Unix domain socket via `interprocess`.

use std::io;
use std::sync::OnceLock;

#[cfg(not(windows))]
use std::path::PathBuf;

use interprocess::local_socket::prelude::*;
#[cfg(windows)]
use interprocess::local_socket::GenericNamespaced;
use interprocess::local_socket::{GenericFilePath, ListenerOptions, Name, Stream};

use crate::auth::{allow_insecure_ipc, IpcHmacKey};
use crate::framing::{
    read_request_signed, read_response_signed, write_request_signed, write_response_signed, IpcError,
};
use crate::protocol::{IpcRequest, IpcResponse, ENDPOINT_NAME};

fn shared_hmac_key() -> Option<&'static IpcHmacKey> {
    static KEY: OnceLock<Option<IpcHmacKey>> = OnceLock::new();
    KEY.get_or_init(|| {
        if allow_insecure_ipc() {
            return None;
        }
        match IpcHmacKey::load_or_create() {
            Ok(k) => Some(k),
            Err(e) => {
                tracing::error!(error = %e, "ipc hmac key unavailable");
                None
            }
        }
    })
    .as_ref()
}

fn socket_name() -> Result<Name<'static>, IpcError> {
    #[cfg(windows)]
    {
        use interprocess::local_socket::ToNsName;
        ENDPOINT_NAME
            .to_ns_name::<GenericNamespaced>()
            .map_err(|e| IpcError::Io(io::Error::other(e)))
    }
    #[cfg(not(windows))]
    {
        use interprocess::local_socket::ToFsName;
        let path = unix_socket_path();
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let _ = std::fs::remove_file(&path);
        let path_str = path.into_os_string();
        path_str
            .to_fs_name::<GenericFilePath>()
            .map_err(|e| IpcError::Io(io::Error::other(e)))
    }
}

#[cfg(not(windows))]
pub(crate) fn unix_socket_path() -> PathBuf {
    if let Ok(override_path) = std::env::var("MERITSCREEN_IPC_SOCK") {
        return PathBuf::from(override_path);
    }
    #[cfg(target_os = "macos")]
    {
        PathBuf::from("/tmp").join(format!("{ENDPOINT_NAME}.sock"))
    }
    #[cfg(not(target_os = "macos"))]
    {
        if let Ok(runtime) = std::env::var("XDG_RUNTIME_DIR") {
            return PathBuf::from(runtime).join(format!("{ENDPOINT_NAME}.sock"));
        }
        std::env::temp_dir().join(format!("{ENDPOINT_NAME}.sock"))
    }
}

#[cfg(unix)]
fn chmod_socket_world_connectable() {
    use std::os::unix::fs::PermissionsExt;
    let path = unix_socket_path();
    if let Ok(meta) = std::fs::metadata(&path) {
        let mut perms = meta.permissions();
        // Peer UID + HMAC authorize; socket must be connectable across root↔user.
        perms.set_mode(0o666);
        let _ = std::fs::set_permissions(&path, perms);
    }
}

pub struct IpcServer {
    listener: interprocess::local_socket::Listener,
}

impl IpcServer {
    pub fn bind() -> Result<Self, IpcError> {
        // Ensure HMAC key exists before accepting peers.
        let _ = shared_hmac_key();
        if !allow_insecure_ipc() && shared_hmac_key().is_none() {
            return Err(IpcError::Auth("could not create ipc hmac key".into()));
        }
        let name = socket_name()?;
        let listener = ListenerOptions::new()
            .name(name)
            .create_sync()
            .map_err(|e| IpcError::Io(io::Error::other(e)))?;
        #[cfg(unix)]
        chmod_socket_world_connectable();
        Ok(Self { listener })
    }

    pub fn accept(&self) -> Result<IpcConn, IpcError> {
        let stream = self
            .listener
            .accept()
            .map_err(|e| IpcError::Io(io::Error::other(e)))?;
        Ok(IpcConn {
            stream,
            key: shared_hmac_key().cloned(),
        })
    }
}

pub struct IpcConn {
    stream: Stream,
    key: Option<IpcHmacKey>,
}

impl IpcConn {
    pub fn connect() -> Result<Self, IpcError> {
        let key = if allow_insecure_ipc() {
            None
        } else {
            Some(
                IpcHmacKey::load_or_create()
                    .map_err(|e| IpcError::Auth(e.to_string()))?,
            )
        };
        let name = connect_name()?;
        let stream = Stream::connect(name).map_err(|e| IpcError::Io(io::Error::other(e)))?;
        Ok(Self { stream, key })
    }

    /// Test helper: connect with an explicit key (or none for spoof tests).
    pub fn connect_with_key(key: Option<IpcHmacKey>) -> Result<Self, IpcError> {
        let name = connect_name()?;
        let stream = Stream::connect(name).map_err(|e| IpcError::Io(io::Error::other(e)))?;
        Ok(Self { stream, key })
    }

    pub fn send_request(&mut self, req: &IpcRequest) -> Result<(), IpcError> {
        match &self.key {
            Some(k) => write_request_signed(&mut self.stream, req, k),
            None if allow_insecure_ipc() => {
                crate::framing::write_request(&mut self.stream, req)
            }
            None => Err(IpcError::Auth("HMAC key required".into())),
        }
    }

    pub fn recv_request(&mut self) -> Result<IpcRequest, IpcError> {
        match &self.key {
            Some(k) => read_request_signed(&mut self.stream, k),
            None if allow_insecure_ipc() => crate::framing::read_request(&mut self.stream),
            None => Err(IpcError::Auth("HMAC key required".into())),
        }
    }

    pub fn send_response(&mut self, resp: &IpcResponse) -> Result<(), IpcError> {
        match &self.key {
            Some(k) => write_response_signed(&mut self.stream, resp, k),
            None if allow_insecure_ipc() => {
                crate::framing::write_response(&mut self.stream, resp)
            }
            None => Err(IpcError::Auth("HMAC key required".into())),
        }
    }

    pub fn recv_response(&mut self) -> Result<IpcResponse, IpcError> {
        match &self.key {
            Some(k) => read_response_signed(&mut self.stream, k),
            None if allow_insecure_ipc() => crate::framing::read_response(&mut self.stream),
            None => Err(IpcError::Auth("HMAC key required".into())),
        }
    }

    pub fn call(&mut self, req: &IpcRequest) -> Result<IpcResponse, IpcError> {
        self.send_request(req)?;
        self.recv_response()
    }

    pub fn stream(&self) -> &Stream {
        &self.stream
    }
}

fn connect_name() -> Result<Name<'static>, IpcError> {
    #[cfg(windows)]
    {
        use interprocess::local_socket::ToNsName;
        ENDPOINT_NAME
            .to_ns_name::<GenericNamespaced>()
            .map_err(|e| IpcError::Io(io::Error::other(e)))
    }
    #[cfg(not(windows))]
    {
        use interprocess::local_socket::ToFsName;
        unix_socket_path()
            .into_os_string()
            .to_fs_name::<GenericFilePath>()
            .map_err(|e| IpcError::Io(io::Error::other(e)))
    }
}
