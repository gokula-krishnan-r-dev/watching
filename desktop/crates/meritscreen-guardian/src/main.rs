//! MeritScreen Guardian — system service that survives reboot.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use meritscreen_guardian::runtime::{self, RuntimeOptions};
use meritscreen_guardian::uninstall;
use meritscreen_security::init_tracing;
use tracing::{error, info};

fn main() {
    init_tracing("meritscreen_guardian=info,meritscreen_ipc=info");
    let args: Vec<String> = std::env::args().skip(1).collect();

    if args.iter().any(|a| a == "--help" || a == "-h") {
        print_help();
        return;
    }

    if args.iter().any(|a| a == "--perf-smoke") {
        match meritscreen_guardian::perf::assert_rss_under_budget() {
            Ok(rss) => {
                println!(
                    "perf-smoke OK rss_mb={rss} budget_mb={}",
                    meritscreen_guardian::perf::rss_budget_mb()
                );
                std::process::exit(0);
            }
            Err(e) => {
                eprintln!("perf-smoke FAILED: {e}");
                std::process::exit(1);
            }
        }
    }

    if args.iter().any(|a| a == "--mark-uninstall-attempt") {
        match uninstall::mark_uninstall_attempt_flag() {
            Ok(()) => println!("Recorded tamperFlags.uninstallAttempt"),
            Err(e) => {
                eprintln!("mark uninstall attempt failed: {e}");
                std::process::exit(1);
            }
        }
        return;
    }

    if let Some(pos) = args.iter().position(|a| a == "--uninstall-with-pin") {
        let pin = args.get(pos + 1).map(String::as_str).unwrap_or("");
        match uninstall::uninstall_with_pin(pin) {
            Ok(()) => println!("MeritScreen uninstalled (PIN verified)"),
            Err(e) => {
                eprintln!("{e}");
                std::process::exit(1);
            }
        }
        return;
    }

    #[cfg(windows)]
    {
        use meritscreen_guardian::windows_service;
        if args.iter().any(|a| a == "--install-service") {
            match windows_service::install_service() {
                Ok(()) => println!("Installed {}", windows_service::SERVICE_NAME),
                Err(e) => {
                    eprintln!("install failed: {e}");
                    std::process::exit(1);
                }
            }
            return;
        }
        if args.iter().any(|a| a == "--uninstall-service") {
            // Unguarded path (admin / MSI): detect for parent alert, then remove.
            let _ = uninstall::mark_uninstall_attempt_flag();
            match windows_service::uninstall_service() {
                Ok(()) => println!("Uninstalled {}", windows_service::SERVICE_NAME),
                Err(e) => {
                    eprintln!("uninstall failed: {e}");
                    std::process::exit(1);
                }
            }
            return;
        }
        if args.iter().any(|a| a == "--service") {
            if let Err(e) = windows_service::run_as_service() {
                error!(error = %e, "service dispatcher failed");
                std::process::exit(1);
            }
            return;
        }
    }

    #[cfg(target_os = "macos")]
    {
        use meritscreen_guardian::macos_service;
        if args.iter().any(|a| a == "--install-daemon") {
            if let Err(e) = macos_service::install_daemon() {
                eprintln!("install-daemon failed: {e}");
                std::process::exit(1);
            }
            println!("Installed {}", macos_service::GUARDIAN_LABEL);
            return;
        }
        if args.iter().any(|a| a == "--uninstall-daemon") {
            let _ = uninstall::mark_uninstall_attempt_flag();
            if let Err(e) = macos_service::uninstall_daemon() {
                eprintln!("uninstall-daemon failed: {e}");
                std::process::exit(1);
            }
            println!("Uninstalled {}", macos_service::GUARDIAN_LABEL);
            return;
        }
        if args.iter().any(|a| a == "--install-user") {
            let bin = args
                .iter()
                .position(|a| a == "--bin-dir")
                .and_then(|i| args.get(i + 1))
                .map(std::path::PathBuf::from)
                .unwrap_or_else(|| {
                    std::env::current_exe()
                        .ok()
                        .and_then(|p| p.parent().map(|d| d.to_path_buf()))
                        .unwrap_or_else(|| std::path::PathBuf::from("."))
                });
            if let Err(e) = macos_service::install_user_agents(&bin) {
                eprintln!("install-user failed: {e}");
                std::process::exit(1);
            }
            println!("Installed user LaunchAgents (lab)");
            return;
        }
        if args.iter().any(|a| a == "--uninstall-user") {
            let _ = uninstall::mark_uninstall_attempt_flag();
            if let Err(e) = macos_service::uninstall_user_agents() {
                eprintln!("uninstall-user failed: {e}");
                std::process::exit(1);
            }
            println!("Uninstalled user LaunchAgents");
            return;
        }
        if args.iter().any(|a| a == "--register-smappservice") {
            if let Err(e) = macos_service::register_smappservice_daemon() {
                eprintln!("{e}");
                std::process::exit(1);
            }
            return;
        }
        if args.iter().any(|a| a == "--daemon") {
            let disable_agent = args.iter().any(|a| a == "--no-agent");
            if let Err(e) = macos_service::run_as_daemon(disable_agent) {
                error!(error = %e, "daemon runtime failed");
                std::process::exit(1);
            }
            return;
        }
    }

    let disable_agent = args.iter().any(|a| a == "--no-agent");
    let running = Arc::new(AtomicBool::new(true));
    let r = Arc::clone(&running);
    let _ = ctrlc::set_handler(move || {
        info!("ctrl-c — stopping guardian");
        r.store(false, Ordering::SeqCst);
    });

    let opts = RuntimeOptions {
        console_mode: true,
        disable_agent_spawn: disable_agent,
        mark_tamper_on_stop: false,
    };

    if let Err(e) = runtime::run_with_stop(opts, running) {
        error!(error = %e, "guardian runtime error");
        std::process::exit(1);
    }
}

fn print_help() {
    println!(
        "\
meritscreen-guardian — MeritScreen always-on Guardian

Usage:
  meritscreen-guardian [--no-agent]
  meritscreen-guardian --perf-smoke                 (d10 RSS budget gate)
  meritscreen-guardian --uninstall-with-pin <pin>   (d9 PIN-gated uninstall)
  meritscreen-guardian --mark-uninstall-attempt     (detect / parent alert)
  meritscreen-guardian --install-service            (Windows, admin)
  meritscreen-guardian --uninstall-service          (Windows, admin — marks uninstallAttempt)
  meritscreen-guardian --service                    (Windows SCM entry)
  meritscreen-guardian --install-daemon             (macOS, root)
  meritscreen-guardian --uninstall-daemon           (macOS, root — marks uninstallAttempt)
  meritscreen-guardian --install-user               (macOS lab LaunchAgents)
  meritscreen-guardian --daemon                     (macOS launchd entry)
  meritscreen-guardian --register-smappservice      (macOS .app host)

IPC endpoint: {}
",
        meritscreen_ipc::ENDPOINT_NAME
    );
}
