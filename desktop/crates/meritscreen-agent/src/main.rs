//! MeritScreen Session Agent — runs inside the logged-in child user session.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use meritscreen_security::init_tracing;
use tracing::{error, info};

mod overlay;
mod runtime;

fn main() {
    init_tracing("meritscreen_agent=info,meritscreen_ipc=info");
    let args: Vec<String> = std::env::args().skip(1).collect();

    if args.iter().any(|a| a == "--help" || a == "-h") {
        println!(
            "meritscreen-agent — session agent\n\n\
  --console           foreground (default)\n\
  --service-session   started by Guardian via CreateProcessAsUser (Windows)\n\
  --launch-agent      started by macOS LaunchAgent\n\
  --overlay-proof     show topmost/shielding fullscreen proof and exit\n"
        );
        return;
    }

    if args.iter().any(|a| a == "--overlay-proof") {
        overlay::show_overlay_proof("manual");
        return;
    }

    let running = Arc::new(AtomicBool::new(true));
    let r = Arc::clone(&running);
    let _ = ctrlc::set_handler(move || {
        info!("ctrl-c — stopping agent");
        r.store(false, Ordering::SeqCst);
    });

    if args
        .iter()
        .any(|a| a == "--launch-agent" || a == "--service-session")
    {
        info!("agent started by OS session registration");
    }

    if let Err(e) = runtime::run(running) {
        error!(error = %e, "agent runtime error");
        std::process::exit(1);
    }
}
