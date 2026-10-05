//! Windows Service host for MeritScreen Guardian.
#![cfg(windows)]

use std::ffi::OsString;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc;
use std::sync::Arc;
use std::time::Duration;

use tracing::{error, info};
use windows_service::define_windows_service;
use windows_service::service::{
    ServiceAccess, ServiceControl, ServiceControlAccept, ServiceErrorControl, ServiceExitCode,
    ServiceInfo, ServiceStartType, ServiceState, ServiceStatus, ServiceType,
};
use windows_service::service_control_handler::{self, ServiceControlHandlerResult};
use windows_service::service_dispatcher;
use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

use crate::runtime::{self, RuntimeOptions};
// Service binary path uses this crate as a library.

pub const SERVICE_NAME: &str = "MeritScreenGuardian";
pub const SERVICE_DISPLAY: &str = "MeritScreen Guardian";
pub const SERVICE_DESC: &str = "MeritScreen always-on child enforcement and sync service";

define_windows_service!(ffi_service_main, service_main);

pub fn run_as_service() -> windows_service::Result<()> {
    service_dispatcher::start(SERVICE_NAME, ffi_service_main)
}

fn service_main(_args: Vec<OsString>) {
    if let Err(e) = run_service_body() {
        error!(error = %e, "guardian service failed");
    }
}

fn run_service_body() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let (shutdown_tx, shutdown_rx) = mpsc::channel();
    let running = Arc::new(AtomicBool::new(true));
    let running_handler = Arc::clone(&running);

    let event_handler = move |control| match control {
        ServiceControl::Stop | ServiceControl::Shutdown => {
            running_handler.store(false, Ordering::SeqCst);
            let _ = shutdown_tx.send(());
            ServiceControlHandlerResult::NoError
        }
        ServiceControl::Interrogate => ServiceControlHandlerResult::NoError,
        _ => ServiceControlHandlerResult::NotImplemented,
    };

    let status_handle = service_control_handler::register(SERVICE_NAME, event_handler)?;
    status_handle.set_service_status(ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: ServiceState::Running,
        controls_accepted: ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
        exit_code: ServiceExitCode::Win32(0),
        checkpoint: 0,
        wait_hint: Duration::default(),
        process_id: None,
    })?;

    info!("MeritScreen Guardian Windows Service running");

    let run_flag = Arc::clone(&running);
    let runtime_handle = std::thread::spawn(move || {
        runtime::run_with_stop(
            RuntimeOptions {
                console_mode: false,
                disable_agent_spawn: false,
                mark_tamper_on_stop: true,
            },
            run_flag,
        )
    });

    let _ = shutdown_rx.recv();
    running.store(false, Ordering::SeqCst);
    info!("service stop requested");

    let _ = runtime_handle.join();

    status_handle.set_service_status(ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: ServiceState::Stopped,
        controls_accepted: ServiceControlAccept::empty(),
        exit_code: ServiceExitCode::Win32(0),
        checkpoint: 0,
        wait_hint: Duration::default(),
        process_id: None,
    })?;
    Ok(())
}

pub fn install_service() -> windows_service::Result<()> {
    let manager =
        ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CREATE_SERVICE)?;
    let exe = std::env::current_exe()
        .map_err(|e| windows_service::Error::Winapi(std::io::Error::other(e)))?;

    let service_info = ServiceInfo {
        name: OsString::from(SERVICE_NAME),
        display_name: OsString::from(SERVICE_DISPLAY),
        service_type: ServiceType::OWN_PROCESS,
        start_type: ServiceStartType::AutoStart,
        error_control: ServiceErrorControl::Normal,
        executable_path: exe,
        launch_arguments: vec![OsString::from("--service")],
        dependencies: vec![],
        account_name: None,
        account_password: None,
    };

    let service = manager.create_service(&service_info, ServiceAccess::CHANGE_CONFIG)?;
    let _ = service.set_description(SERVICE_DESC);
    info!(name = SERVICE_NAME, "Windows service installed (AutoStart)");
    Ok(())
}

pub fn uninstall_service() -> windows_service::Result<()> {
    let manager = ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)?;
    let service =
        manager.open_service(SERVICE_NAME, ServiceAccess::DELETE | ServiceAccess::STOP)?;
    let _ = service.stop();
    service.delete()?;
    info!(name = SERVICE_NAME, "Windows service uninstalled");
    Ok(())
}
