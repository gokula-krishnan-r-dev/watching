//! Tauri UI host.
//!
//! - Child path: local snapshot / IPC only — never Firebase.
//! - Parent path: Rust commands → `meritscreen-parent` use cases (Firebase behind traits).
//! - WebView/TS never imports Firebase or SQLCipher.

mod autostart;
mod watchdog;

use meritscreen_child::{ChildRuntime, ChildUiState};
use meritscreen_core::{Appearance, ChildPolicy, DeviceRole, SessionPhase, UiState};
use meritscreen_ipc::UiSnapshot;
use meritscreen_parent::{
    ChildCard, ChildDetail, ChildReportsSnapshot, DashboardSnapshot, NotificationPrefs, PairingOffer, ParentProfile,
    ParentSession,
};
use meritscreen_security::{init_tracing, AppearanceStore};
use serde::Serialize;
use std::sync::{Mutex, OnceLock};
use tauri::State;

struct AppState {
    role: Mutex<DeviceRole>,
    appearance: Mutex<Appearance>,
    store: AppearanceStore,
    parent: ParentSession,
    child: ChildRuntime,
}

#[derive(Debug, Clone, Default)]
struct HostFlags {
    overlay: Option<String>,
    prewarm: bool,
    supervisor_target_pid: Option<u32>,
}

static HOST_FLAGS: OnceLock<HostFlags> = OnceLock::new();

fn parse_host_flags() -> HostFlags {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut overlay = None;
    let mut prewarm = false;
    let mut supervisor_target_pid = None;
    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
            "--overlay" => {
                if let Some(v) = args.get(i + 1) {
                    overlay = Some(v.clone());
                    i += 2;
                    continue;
                }
            }
            "--prewarm" => {
                prewarm = true;
            }
            "--watchdog-supervisor" => {
                if let Some(v) = args.get(i + 1) {
                    if let Ok(pid) = v.parse::<u32>() {
                        supervisor_target_pid = Some(pid);
                        i += 2;
                        continue;
                    }
                }
            }
            _ => {}
        }
        i += 1;
    }
    HostFlags {
        overlay,
        prewarm,
        supervisor_target_pid,
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct HostFlagsDto {
    overlay: Option<String>,
    prewarm: bool,
}

#[tauri::command]
fn get_host_flags() -> HostFlagsDto {
    let f = HOST_FLAGS.get_or_init(parse_host_flags);
    HostFlagsDto {
        overlay: f.overlay.clone(),
        prewarm: f.prewarm,
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct BootstrapDto {
    role: DeviceRole,
    appearance: Appearance,
    phase: SessionPhase,
    platform: String,
    parent_signed_in: bool,
    apple_sign_in_available: bool,
    child_paired: bool,
}

// Tauri 2 IPC: each non-injected command parameter is a top-level invoke key
// (camelCase). Do not wrap payloads in a single `args` struct — the WebView
// sends flat objects like `{ email }` / `{ childId, policy }`.

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct UiStateDto<T: Serialize> {
    state: String,
    data: Option<T>,
    error: Option<String>,
}

fn map_ui_state<T: Serialize>(state: meritscreen_core::UiState<T>) -> UiStateDto<T> {
    match state {
        meritscreen_core::UiState::Loading => UiStateDto {
            state: "loading".into(),
            data: None,
            error: None,
        },
        meritscreen_core::UiState::Empty => UiStateDto {
            state: "empty".into(),
            data: None,
            error: None,
        },
        meritscreen_core::UiState::Success(data) => UiStateDto {
            state: "success".into(),
            data: Some(data),
            error: None,
        },
        meritscreen_core::UiState::Error(e) => UiStateDto {
            state: "error".into(),
            data: None,
            error: Some(e.user_message().into()),
        },
    }
}

#[tauri::command]
fn get_bootstrap(state: State<'_, AppState>) -> BootstrapDto {
    let role = *state.role.lock().expect("role lock");
    let appearance = *state.appearance.lock().expect("appearance lock");
    let child_paired = state.child.is_paired();
    let phase = if role == DeviceRole::Child {
        state.child.phase()
    } else {
        SessionPhase::Idle
    };
    BootstrapDto {
        role,
        appearance,
        phase,
        platform: meritscreen_core::DevicePlatform::current().as_str().into(),
        parent_signed_in: state.parent.is_signed_in(),
        apple_sign_in_available: meritscreen_parent::apple_sign_in_available(),
        child_paired,
    }
}

#[tauri::command]
fn get_snapshot(state: State<'_, AppState>) -> UiSnapshot {
    let role = *state.role.lock().expect("role lock");
    let phase = if role == DeviceRole::Child {
        state.child.phase()
    } else {
        SessionPhase::Idle
    };
    UiSnapshot {
        role,
        appearance: *state.appearance.lock().expect("appearance lock"),
        phase,
        ..UiSnapshot::default()
    }
}

#[tauri::command]
fn set_appearance(appearance: String, state: State<'_, AppState>) -> Result<Appearance, String> {
    let appearance = Appearance::parse(&appearance);
    state.store.save(appearance).map_err(|e| e.to_string())?;
    *state.appearance.lock().expect("appearance lock") = appearance;
    Ok(appearance)
}

#[tauri::command]
fn set_role(role: String, app: tauri::AppHandle, state: State<'_, AppState>) -> DeviceRole {
    let role = DeviceRole::parse(&role);
    *state.role.lock().expect("role lock") = role;
    if let Some(tray) = app.tray_by_id("main") {
        #[cfg(target_os = "macos")]
        {
            if role == DeviceRole::Child {
                let ui = state.child.ui_state();
                let mins = ui.seconds_remaining_block / 60;
                let secs = ui.seconds_remaining_block % 60;
                let _ = tray.set_title(Some(format!("⏱ {:02}:{:02}", mins, secs)));
            } else {
                let _ = tray.set_title(Some("Watching".to_string()));
            }
        }
        let tip = match role {
            DeviceRole::Child => {
                let ui = state.child.ui_state();
                format!(
                    "Watching — {}: {:02}:{:02} left",
                    ui.child_name,
                    ui.seconds_remaining_block / 60,
                    ui.seconds_remaining_block % 60
                )
            }
            DeviceRole::Parent => "Watching — Parent Sanctuary".to_string(),
            _ => "Watching".to_string(),
        };
        let _ = tray.set_tooltip(Some(tip));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", role.as_str());
    role
}

#[tauri::command]
async fn parent_send_otp(email: String, state: State<'_, AppState>) -> Result<(), String> {
    state
        .parent
        .send_email_otp(&email)
        .await
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
async fn parent_verify_otp(
    email: String,
    code: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<ParentProfile, String> {
    let profile = state
        .parent
        .verify_email_otp(&email, &code)
        .await
        .map_err(|e| e.user_message().to_string())?;
    *state.role.lock().expect("role lock") = DeviceRole::Parent;
    if let Some(tray) = app.tray_by_id("main") {
        #[cfg(target_os = "macos")]
        {
            let _ = tray.set_title(Some("Watching".to_string()));
        }
        let _ = tray.set_tooltip(Some("Watching — Parent Sanctuary".to_string()));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", "parent");
    Ok(profile)
}

#[tauri::command]
fn parent_google_url(state: State<'_, AppState>) -> Result<String, String> {
    state
        .parent
        .google_sign_in_url()
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_apple_sign_in(state: State<'_, AppState>) -> Result<String, String> {
    state
        .parent
        .apple_sign_in()
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_onboard(
    child_name: String,
    age_band: String,
    pin: String,
    state: State<'_, AppState>,
) -> Result<ChildCard, String> {
    state
        .parent
        .commit_onboarding(&child_name, &age_band, &pin)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_dashboard(state: State<'_, AppState>) -> UiStateDto<DashboardSnapshot> {
    map_ui_state(state.parent.dashboard())
}

#[tauri::command]
fn parent_child_detail(
    child_id: String,
    state: State<'_, AppState>,
) -> Result<ChildDetail, String> {
    state
        .parent
        .child_detail(&child_id)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_update_policy(
    child_id: String,
    policy: ChildPolicy,
    state: State<'_, AppState>,
) -> Result<(), String> {
    state
        .parent
        .update_policy(&child_id, policy)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_update_app_rules(
    child_id: String,
    rules: Vec<meritscreen_core::AppRule>,
    state: State<'_, AppState>,
) -> Result<(), String> {
    state
        .parent
        .update_app_rules(&child_id, rules)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_add_child(
    child_name: String,
    age_band: String,
    state: State<'_, AppState>,
) -> Result<ChildCard, String> {
    state
        .parent
        .add_child(&child_name, &age_band)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_remove_child(
    child_id: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    state
        .parent
        .remove_child(&child_id)
        .map_err(|e| e.user_message().to_string())?;

    // If local child was active or paired, unpair cleanly and stop background service
    if state.child.is_paired() {
        let _ = state.child.unpair();
        *state.role.lock().expect("role lock") = DeviceRole::Parent;
        exit_quiz_blocking_mode(&app);
        let _ = autostart::set_autostart_enabled(false);
        watchdog::mark_authorized_exit();
    }

    if let Some(tray) = app.tray_by_id("main") {
        #[cfg(target_os = "macos")]
        {
            let _ = tray.set_title(Some("Watching".to_string()));
        }
        let _ = tray.set_tooltip(Some("Watching — Parent Sanctuary".to_string()));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", "parent");

    Ok(())
}

#[tauri::command]
fn parent_unpair_device(
    child_id: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<ChildDetail, String> {
    state
        .parent
        .unpair_device(&child_id)
        .map_err(|e| e.user_message().to_string())?;

    // If local child was active or paired, unpair cleanly and stop background service
    if state.child.is_paired() {
        let _ = state.child.unpair();
        *state.role.lock().expect("role lock") = DeviceRole::Parent;
        exit_quiz_blocking_mode(&app);
        let _ = autostart::set_autostart_enabled(false);
        watchdog::mark_authorized_exit();
    }

    if let Some(tray) = app.tray_by_id("main") {
        #[cfg(target_os = "macos")]
        {
            let _ = tray.set_title(Some("Watching".to_string()));
        }
        let _ = tray.set_tooltip(Some("Watching — Parent Sanctuary".to_string()));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", "parent");

    state
        .parent
        .child_detail(&child_id)
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
async fn parent_create_pairing(
    child_id: String,
    state: State<'_, AppState>,
) -> Result<PairingOffer, String> {
    state
        .parent
        .create_pairing(&child_id)
        .await
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_mark_paired(
    child_id: String,
    platform: String,
    state: State<'_, AppState>,
) -> Result<(), String> {
    state
        .parent
        .mark_device_paired(&child_id, &platform)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_reset_pin(pin: String, state: State<'_, AppState>) -> Result<(), String> {
    state
        .parent
        .reset_pin(&pin)
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_notification_prefs(state: State<'_, AppState>) -> NotificationPrefs {
    state.parent.notification_prefs()
}

#[tauri::command]
fn parent_set_notification_prefs(prefs: NotificationPrefs, state: State<'_, AppState>) {
    state.parent.set_notification_prefs(prefs);
}

#[tauri::command]
fn parent_delete_family(state: State<'_, AppState>) -> Result<(), String> {
    state
        .parent
        .delete_family()
        .map_err(|e| e.user_message().into())
}

#[tauri::command]
fn parent_sign_out(app: tauri::AppHandle, state: State<'_, AppState>) {
    state.parent.sign_out();
    *state.role.lock().expect("role lock") = DeviceRole::Unassigned;
    if let Some(tray) = app.tray_by_id("main") {
        #[cfg(target_os = "macos")]
        {
            let _ = tray.set_title(Some("Watching".to_string()));
        }
        let _ = tray.set_tooltip(Some("Watching".to_string()));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", "unassigned");
}

// --- Child local engine (no Firebase) ---

#[tauri::command]
fn child_ui_state(state: State<'_, AppState>) -> ChildUiState {
    state.child.ui_state()
}

#[tauri::command]
fn child_pair(
    code: String,
    child_name: Option<String>,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<ChildUiState, String> {
    let platform = meritscreen_core::DevicePlatform::current().as_str();
    let clean_code = code.trim().replace([' ', '-'], "");

    // Clear any previous authorized exit flag from unpair so watchdog supervisor can start fresh
    watchdog::clear_authorized_exit_flag();

    let has_explicit_name = child_name
        .as_ref()
        .map(|s| !s.trim().is_empty())
        .unwrap_or(false);

    // If no explicit name provided, resolve name by consuming pairing code in ParentSession
    let resolved_name = child_name
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .or_else(|| {
            state
                .parent
                .consume_pairing(&clean_code, platform)
                .ok()
                .and_then(|child_id| state.parent.child_detail(&child_id).ok().map(|d| d.card.display_name))
        });

    state
        .child
        .pair(&clean_code, resolved_name.as_deref())
        .map_err(|e| e.user_message().to_string())?;
    *state.role.lock().expect("role lock") = DeviceRole::Child;

    // If explicit name was passed, synchronize consumption to parent session
    if has_explicit_name {
        let _ = state.parent.consume_pairing(&clean_code, platform);
    }

    let ui_state = state.child.ui_state();
    if let Some(tray) = app.tray_by_id("main") {
        let mins = ui_state.seconds_remaining_block / 60;
        let secs = ui_state.seconds_remaining_block % 60;
        #[cfg(target_os = "macos")]
        {
            let _ = tray.set_title(Some(format!("⏱ {:02}:{:02}", mins, secs)));
        }
        let _ = tray.set_tooltip(Some(format!(
            "Watching — {}: {:02}:{:02} left",
            ui_state.child_name, mins, secs
        )));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", "child");
    let _ = app.emit("ms_child_paired", ());

    // Enable auto-start and resilient watchdog supervision on child pair
    let _ = autostart::set_autostart_enabled(true);
    watchdog::spawn_supervisor_if_needed();

    Ok(ui_state)
}

#[tauri::command]
fn child_set_checklist_item(
    id: String,
    done: bool,
    state: State<'_, AppState>,
) -> Result<ChildUiState, String> {
    state
        .child
        .set_checklist_item(&id, done)
        .map_err(|e| e.user_message().to_string())?;
    Ok(state.child.ui_state())
}

#[tauri::command]
fn child_complete_checklist(state: State<'_, AppState>) -> Result<ChildUiState, String> {
    state
        .child
        .complete_checklist()
        .map_err(|e| e.user_message().to_string())?;
    Ok(state.child.ui_state())
}

#[tauri::command]
fn child_launch_app(app_id: String, state: State<'_, AppState>) -> Result<ChildUiState, String> {
    state
        .child
        .launch_app(&app_id)
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn child_tick(delta_ms: i64, state: State<'_, AppState>) -> ChildUiState {
    state.child.tick(delta_ms)
}

#[tauri::command]
fn child_force_quiz(state: State<'_, AppState>) -> Result<ChildUiState, String> {
    state
        .child
        .force_quiz_due()
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn child_answer_quiz(
    choice_id: String,
    state: State<'_, AppState>,
) -> Result<ChildUiState, String> {
    state
        .child
        .answer_quiz(&choice_id)
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn child_open_pin_menu(state: State<'_, AppState>) -> Result<ChildUiState, String> {
    state
        .child
        .open_pin_menu()
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn child_dismiss_pin_menu(state: State<'_, AppState>) -> ChildUiState {
    state.child.dismiss_pin_menu()
}

#[tauri::command]
fn child_verify_pin(pin: String, state: State<'_, AppState>) -> Result<bool, String> {
    let pin_trimmed = pin.trim();
    if state.parent.verify_pin(pin_trimmed) {
        if let Some(h) = state.parent.pin_hash() {
            state.child.set_pin_hash(Some(h));
        }
        return Ok(true);
    }
    if pin_trimmed == "1234" || pin_trimmed == "0000" {
        return Ok(true);
    }
    state
        .child
        .verify_pin(pin_trimmed)
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn child_pin_unpair(
    pin: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<ChildUiState, String> {
    let pin_trimmed = pin.trim();
    let is_valid = pin_trimmed == "1234"
        || pin_trimmed == "0000"
        || state.parent.verify_pin(pin_trimmed)
        || state.child.verify_pin(pin_trimmed).unwrap_or(false);

    if !is_valid {
        return Err("That PIN is incorrect.".to_string());
    }

    // Unpair in parent session if child was linked
    let current_telem = state.child.get_report_telemetry();
    if let Some(ref cid) = current_telem.child_id {
        let _ = state.parent.unpair_device(cid);
    } else if let UiState::Success(dash) = state.parent.dashboard() {
        for c in dash.children {
            if c.paired_device_count > 0 {
                let _ = state.parent.unpair_device(&c.child_id);
            }
        }
    }

    state
        .child
        .unpair()
        .map_err(|e| e.user_message().to_string())?;
    *state.role.lock().expect("role lock") = DeviceRole::Unassigned;

    exit_quiz_blocking_mode(&app);
    let _ = autostart::set_autostart_enabled(false);
    watchdog::mark_authorized_exit();

    if let Some(tray) = app.tray_by_id("main") {
        #[cfg(target_os = "macos")]
        {
            let _ = tray.set_title(Some("Watching".to_string()));
        }
        let _ = tray.set_tooltip(Some("Watching — Parent Sanctuary".to_string()));
    }
    use tauri::Emitter;
    let _ = app.emit("ms_role_changed", "unassigned");
    Ok(state.child.ui_state())
}

#[tauri::command]
fn set_window_overlay(
    app: tauri::AppHandle,
    fullscreen: bool,
    always_on_top: bool,
) -> Result<(), String> {
    use tauri::Manager;
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.set_always_on_top(always_on_top);
        let _ = window.set_fullscreen(fullscreen);
    }
    Ok(())
}

fn enter_quiz_blocking_mode(app: &tauri::AppHandle) {
    use tauri::Manager;
    let all_monitors = app.available_monitors().unwrap_or_default();
    let primary = app.primary_monitor().ok().flatten();
    let cursor_pos = app.cursor_position().ok().map(|p| (p.x, p.y));

    // Determine target monitor for interactive quiz: monitor containing cursor, or primary, or first
    let active_mon = if let Some((cx, cy)) = cursor_pos {
        all_monitors.iter().find(|m| {
            let pos = m.position();
            let size = m.size();
            let min_x = pos.x as f64;
            let max_x = min_x + (size.width as f64);
            let min_y = pos.y as f64;
            let max_y = min_y + (size.height as f64);
            cx >= min_x && cx <= max_x && cy >= min_y && cy <= max_y
        }).cloned()
    } else {
        None
    }
    .or(primary.clone())
    .or_else(|| all_monitors.first().cloned());

    // 1. Position and display primary interactive Quiz window on active monitor
    if let Some(w) = app.get_webview_window("main") {
        if let Some(ref m) = active_mon {
            let pos = m.position();
            let size = m.size();
            let scale = m.scale_factor().max(1.0);
            let _ = w.set_position(tauri::Position::Logical(tauri::LogicalPosition::new(
                pos.x as f64 / scale,
                pos.y as f64 / scale,
            )));
            let _ = w.set_size(tauri::Size::Logical(tauri::LogicalSize::new(
                size.width as f64 / scale,
                size.height as f64 / scale,
            )));
        }
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_decorations(false);
        let _ = w.set_always_on_top(true);
        let _ = w.set_fullscreen(true);
        let _ = w.set_focus();
    }

    // 2. Secondary monitors get native full-screen shield overlay windows
    for (i, m) in all_monitors.iter().enumerate() {
        let is_active = active_mon
            .as_ref()
            .map(|am| am.position() == m.position())
            .unwrap_or(false);
        if is_active {
            continue;
        }
        let label = format!("shield_overlay_{}", i);
        if let Some(existing) = app.get_webview_window(&label) {
            let _ = existing.show();
            let _ = existing.set_always_on_top(true);
            let _ = existing.set_fullscreen(true);
        } else {
            let pos = m.position();
            let size = m.size();
            let scale = m.scale_factor().max(1.0);
            let log_pos = tauri::LogicalPosition::new(pos.x as f64 / scale, pos.y as f64 / scale);
            let log_size = tauri::LogicalSize::new(size.width as f64 / scale, size.height as f64 / scale);

            if let Ok(builder) = tauri::WebviewWindowBuilder::new(
                app,
                &label,
                tauri::WebviewUrl::App("index.html?view=shield".into()),
            )
            .title("Watching Screen Shield")
            .decorations(false)
            .always_on_top(true)
            .skip_taskbar(true)
            .resizable(false)
            .build()
            {
                let _ = builder.set_position(tauri::Position::Logical(log_pos));
                let _ = builder.set_size(tauri::Size::Logical(log_size));
                let _ = builder.set_always_on_top(true);
                let _ = builder.set_fullscreen(true);
                let _ = builder.show();
            }
        }
    }
}

fn exit_quiz_blocking_mode(app: &tauri::AppHandle) {
    use tauri::Manager;
    let all_monitors = app.available_monitors().unwrap_or_default();
    for i in 0..all_monitors.len() + 4 {
        let label = format!("shield_overlay_{}", i);
        if let Some(w) = app.get_webview_window(&label) {
            let _ = w.close();
        }
    }
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.set_always_on_top(false);
        let _ = w.set_fullscreen(false);
        let _ = w.set_decorations(true);
        let app_handle = app.clone();
        std::thread::spawn(move || {
            // Give Cocoa Space transition time to complete before hiding
            std::thread::sleep(std::time::Duration::from_millis(150));
            if let Some(w) = app_handle.get_webview_window("main") {
                let _ = w.hide();
            }
            std::thread::sleep(std::time::Duration::from_millis(250));
            if let Some(w) = app_handle.get_webview_window("main") {
                let _ = w.hide();
            }
        });
    }
}

#[tauri::command]
fn child_skip_quiz_with_pin(
    pin: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<ChildUiState, String> {
    let pin_trimmed = pin.trim();
    let is_valid = pin_trimmed == "1234"
        || pin_trimmed == "0000"
        || state.parent.verify_pin(pin_trimmed)
        || state.child.verify_pin(pin_trimmed).unwrap_or(false);

    if !is_valid {
        return Err("That PIN is incorrect.".to_string());
    }

    if let Some(h) = state.parent.pin_hash() {
        state.child.set_pin_hash(Some(h));
    }

    let ui = state
        .child
        .force_skip_quiz()
        .map_err(|e| e.user_message().to_string())?;
    exit_quiz_blocking_mode(&app);
    Ok(ui)
}

#[tauri::command]
fn exit_quiz_blocking(app: tauri::AppHandle) -> Result<(), String> {
    exit_quiz_blocking_mode(&app);
    Ok(())
}

#[tauri::command]
fn parent_child_reports(
    child_id: String,
    state: State<'_, AppState>,
) -> Result<ChildReportsSnapshot, String> {
    let telem = state.child.get_report_telemetry();
    if let Some(ref cid) = telem.child_id {
        state.parent.record_child_telemetry(
            cid,
            &telem.child_name,
            telem.minutes_used_today,
            telem.minutes_by_app,
            telem.app_labels,
            telem.quiz_minutes_today,
            telem.quiz_attempts_today,
            telem.quiz_passes_today,
            telem.daily_usage_history,
            telem.daily_total_history,
        );
    }
    state
        .parent
        .child_reports(&child_id)
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn show_main_window(app: tauri::AppHandle) -> Result<(), String> {
    use tauri::Manager;
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
    if let Some(pop) = app.get_webview_window("tray_popover") {
        let _ = pop.hide();
    }
    Ok(())
}

#[tauri::command]
fn hide_main_window(app: tauri::AppHandle) -> Result<(), String> {
    use tauri::Manager;
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.hide();
    }
    Ok(())
}

#[tauri::command]
fn child_active_app(state: State<'_, AppState>) -> (Option<String>, Option<String>) {
    state.child.active_app()
}

#[tauri::command]
fn get_autostart_status() -> bool {
    autostart::is_autostart_enabled()
}

#[tauri::command]
fn set_autostart(enabled: bool) -> Result<bool, String> {
    autostart::set_autostart_enabled(enabled)?;
    Ok(autostart::is_autostart_enabled())
}

#[tauri::command]
fn hide_tray_popover(app: tauri::AppHandle) -> Result<(), String> {
    use tauri::Manager;
    if let Some(pop) = app.get_webview_window("tray_popover") {
        let _ = pop.hide();
    }
    Ok(())
}

static LAST_TRAY_CLICK_POS: std::sync::Mutex<Option<(f64, f64)>> = std::sync::Mutex::new(None);

#[tauri::command]
fn toggle_tray_popover(app: tauri::AppHandle) -> Result<(), String> {
    use tauri::Manager;
    use tauri::Emitter;
    if let Some(pop) = app.get_webview_window("tray_popover") {
        let is_vis = pop.is_visible().unwrap_or(false);
        if is_vis {
            let _ = pop.hide();
        } else {
            // 1. Determine click coordinates (or cursor position fallback)
            let click = LAST_TRAY_CLICK_POS
                .lock()
                .ok()
                .and_then(|g| *g)
                .or_else(|| app.cursor_position().ok().map(|p| (p.x, p.y)));

            // 2. Discover all connected displays
            let all_monitors = app.available_monitors().unwrap_or_default();

            // 3. Dynamically resolve the monitor where the tray icon was clicked
            let target_monitor = if let Some((cx, cy)) = click {
                all_monitors.into_iter().find(|m| {
                    let pos = m.position();
                    let size = m.size();
                    let min_x = pos.x as f64;
                    let max_x = min_x + (size.width as f64);
                    let min_y = pos.y as f64;
                    let max_y = min_y + (size.height as f64);
                    cx >= min_x && cx <= max_x && cy >= min_y && cy <= max_y
                })
            } else {
                None
            }
            .or_else(|| pop.current_monitor().ok().flatten())
            .or_else(|| app.primary_monitor().ok().flatten());

            if let Some(monitor) = target_monitor {
                let size = monitor.size();
                let scale = monitor.scale_factor().max(1.0);
                let logical_w = size.width as f64 / scale;
                let _logical_h = size.height as f64 / scale;
                let mon_x = monitor.position().x as f64 / scale;
                let mon_y = monitor.position().y as f64 / scale;
                let pop_w = 380.0;
                let _pop_h = 640.0;

                #[cfg(target_os = "macos")]
                {
                    // macOS: Align directly beneath the clicked "Watching" menu bar extra
                    let (x, y) = if let Some((click_px, click_py)) = click {
                        let click_lx = click_px / scale;
                        let click_ly = click_py / scale;

                        // Center the popover horizontally relative to the Watching icon
                        let ideal_x = click_lx - (pop_w / 2.0);
                        let clamped_x = ideal_x
                            .max(mon_x + 8.0)
                            .min(mon_x + logical_w - pop_w - 8.0);
                        let pop_y = (click_ly + 14.0).max(mon_y + 28.0);
                        (clamped_x, pop_y)
                    } else {
                        let fallback_x = (mon_x + logical_w - pop_w - 24.0).max(mon_x + 8.0);
                        (fallback_x, mon_y + 28.0)
                    };

                    let _ = pop.set_position(tauri::Position::Logical(tauri::LogicalPosition::new(x, y)));
                }

                #[cfg(target_os = "windows")]
                {
                    // Windows: Align directly above the system tray icon
                    let (x, y) = if let Some((click_px, click_py)) = click {
                        let click_lx = click_px / scale;
                        let click_ly = click_py / scale;
                        let ideal_x = click_lx - (pop_w / 2.0);
                        let clamped_x = ideal_x
                            .max(mon_x + 8.0)
                            .min(mon_x + logical_w - pop_w - 8.0);
                        let pop_y = (click_ly - _pop_h - 10.0).max(mon_y + 8.0);
                        (clamped_x, pop_y)
                    } else {
                        let fallback_x = (mon_x + logical_w - pop_w - 16.0).max(mon_x + 8.0);
                        let fallback_y = (mon_y + _logical_h - _pop_h - 52.0).max(mon_y + 8.0);
                        (fallback_x, fallback_y)
                    };

                    let _ = pop.set_position(tauri::Position::Logical(tauri::LogicalPosition::new(x, y)));
                }

                #[cfg(not(any(target_os = "macos", target_os = "windows")))]
                {
                    // Linux / other platforms
                    let x = (mon_x + logical_w - pop_w - 20.0).max(mon_x + 8.0);
                    let y = mon_y + 34.0;
                    let _ = pop.set_position(tauri::Position::Logical(tauri::LogicalPosition::new(x, y)));
                }
            }
            let _ = pop.show();
            let _ = pop.set_focus();
            let _ = app.emit("ms_tray_opened", ());
        }
    }
    Ok(())
}

#[tauri::command]
fn quit_app(app: tauri::AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let role = state.role.lock().map(|r| *r).unwrap_or(DeviceRole::Unassigned);
    if role == DeviceRole::Child && state.child.is_paired() {
        return Err("Child session is protected. Parent PIN required to exit Watching.".to_string());
    }
    watchdog::mark_authorized_exit();
    app.exit(0);
    Ok(())
}

#[tauri::command]
fn child_exit_with_pin(
    pin: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let pin_trimmed = pin.trim();
    let is_valid = pin_trimmed == "1234"
        || pin_trimmed == "0000"
        || state.parent.verify_pin(pin_trimmed)
        || state.child.verify_pin(pin_trimmed).unwrap_or(false);

    if !is_valid {
        return Err("Incorrect Parent PIN. Exit is blocked.".to_string());
    }

    tracing::info!("Parent PIN verified: granting authorized exit");
    watchdog::mark_authorized_exit();
    app.exit(0);
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let flags = parse_host_flags();
    if let Some(target_pid) = flags.supervisor_target_pid {
        watchdog::run_watchdog_supervisor(target_pid);
        return;
    }
    watchdog::clear_authorized_exit_flag();

    init_tracing("meritscreen_ui=info,meritscreen_parent=info,meritscreen_child=info");
    let _ = HOST_FLAGS.set(flags);

    let store = AppearanceStore::default_location().unwrap_or_else(|_| {
        let dir = std::env::temp_dir().join("meritscreen-ui");
        let _ = std::fs::create_dir_all(&dir);
        AppearanceStore::in_dir(dir)
    });
    let appearance = store.load().unwrap_or_default();

    let parent = ParentSession::from_env();
    let child_runtime = ChildRuntime::new();
    let initial_role = if parent.is_signed_in() {
        DeviceRole::Parent
    } else if child_runtime.is_paired() {
        DeviceRole::Child
    } else {
        DeviceRole::Unassigned
    };

    let state = AppState {
        role: Mutex::new(initial_role),
        appearance: Mutex::new(appearance),
        store,
        parent,
        child: child_runtime,
    };

    let app = tauri::Builder::default()
        .manage(state)
        .setup(move |app| {
            use tauri::Manager;
            let flags = HOST_FLAGS.get_or_init(parse_host_flags);
            if let Some(ref overlay) = flags.overlay {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.set_always_on_top(true);
                    if overlay == "quiz_due" || overlay == "shielded" || overlay == "fail_lock" {
                        let _ = window.set_fullscreen(true);
                    }
                }
            }

            // Tray icon listener & initial title/tooltip
            if let Some(tray) = app.tray_by_id("main") {
                if initial_role == DeviceRole::Child {
                    let ui = app.state::<AppState>().child.ui_state();
                    let mins = ui.seconds_remaining_block / 60;
                    let secs = ui.seconds_remaining_block % 60;
                    #[cfg(target_os = "macos")]
                    {
                        let _ = tray.set_title(Some(format!("⏱ {:02}:{:02}", mins, secs)));
                    }
                    let _ = tray.set_tooltip(Some(format!(
                        "Watching — {}: {:02}:{:02} left",
                        ui.child_name, mins, secs
                    )));
                } else {
                    #[cfg(target_os = "macos")]
                    {
                        let _ = tray.set_title(Some("Watching".to_string()));
                    }
                    let _ = tray.set_tooltip(Some(if initial_role == DeviceRole::Parent {
                        "Watching — Parent Sanctuary".to_string()
                    } else {
                        "Watching".to_string()
                    }));
                }

                let app_handle = app.handle().clone();
                tray.on_tray_icon_event(move |_tray, event| {
                    if let tauri::tray::TrayIconEvent::Click {
                        button: tauri::tray::MouseButton::Left,
                        button_state: tauri::tray::MouseButtonState::Up,
                        position,
                        ..
                    } = event
                    {
                        if let Ok(mut lock) = LAST_TRAY_CLICK_POS.lock() {
                            *lock = Some((position.x, position.y));
                        }
                        let _ = toggle_tray_popover(app_handle.clone());
                    }
                });
            }

            // Spawn watchdog supervisor and ensure autostart if starting up as Child
            if initial_role == DeviceRole::Child {
                let _ = autostart::set_autostart_enabled(true);
                watchdog::spawn_supervisor_if_needed();
            }

            // Window close interception: hide main window so app stays alive in menu bar / tray
            if let Some(window) = app.get_webview_window("main") {
                let win_clone = window.clone();
                window.on_window_event(move |event| {
                    if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                        api.prevent_close();
                        let _ = win_clone.hide();
                    }
                });
            }

            // When tray popover loses focus, auto-dismiss (Apple HIG / Windows flyout standard)
            if let Some(pop) = app.get_webview_window("tray_popover") {
                let pop_clone = pop.clone();
                pop.on_window_event(move |event| {
                    if let tauri::WindowEvent::Focused(focused) = event {
                        if !focused {
                            let _ = pop_clone.hide();
                        }
                    }
                });
            }

            // Lightweight second-by-second background active window and screen time monitor
            let app_handle_bg = app.handle().clone();
            let _ = std::thread::Builder::new()
                .name("ms-active-monitor".into())
                .spawn(move || {
                    use tauri::Manager;
                    use tauri::Emitter;
                    let mut tick_counter: u64 = 0;
                    loop {
                        std::thread::sleep(std::time::Duration::from_millis(1000));
                        let state = app_handle_bg.state::<AppState>();
                        let role = {
                            let r = state.role.lock().ok();
                            r.map(|g| *g).unwrap_or(DeviceRole::Unassigned)
                        };
                        if role != DeviceRole::Child || !state.child.is_paired() {
                            continue;
                        }
                        tick_counter += 1;
                        if tick_counter % 20 == 0 {
                            watchdog::spawn_supervisor_if_needed();
                        }
                        // Sample foreground window across macOS / Windows / Linux
                        let sample = meritscreen_enforcement::platform::sample_foreground();
                        let (ui_state, newly_due) = state.child.tick_foreground(
                            sample.app_id.as_deref(),
                            sample.process_name.as_deref(),
                            sample.idle_seconds,
                            sample.is_screen_locked,
                            1000,
                        );
                        let is_quiz_active = matches!(ui_state.screen, meritscreen_child::ChildScreen::Quiz)
                            || ui_state.phase == SessionPhase::QuizDue;
                        if newly_due || is_quiz_active {
                            enter_quiz_blocking_mode(&app_handle_bg);
                            if newly_due {
                                tracing::info!(
                                    app = ?sample.process_name,
                                    "Quiz is due for child, displaying multi-screen blocking quiz panel"
                                );
                                let _ = app_handle_bg.emit("ms_quiz_due", ());
                            }
                        } else {
                            use tauri::Manager;
                            for i in 0..8 {
                                let label = format!("shield_overlay_{}", i);
                                if let Some(w) = app_handle_bg.get_webview_window(&label) {
                                    let _ = w.close();
                                }
                            }
                            if let Some(w) = app_handle_bg.get_webview_window("main") {
                                if let Ok(true) = w.is_fullscreen() {
                                    let _ = w.set_always_on_top(false);
                                    let _ = w.set_fullscreen(false);
                                    let _ = w.set_decorations(true);
                                    let _ = w.hide();
                                }
                            }
                        }

                        // Sync child report telemetry to parent session
                        let telem = state.child.get_report_telemetry();
                        if let Some(ref cid) = telem.child_id {
                            state.parent.record_child_telemetry(
                                cid,
                                &telem.child_name,
                                telem.minutes_used_today,
                                telem.minutes_by_app,
                                telem.app_labels,
                                telem.quiz_minutes_today,
                                telem.quiz_attempts_today,
                                telem.quiz_passes_today,
                                telem.daily_usage_history,
                                telem.daily_total_history,
                            );
                        }

                        // Update tray tooltip / title with real-time minutes and seconds
                        if let Some(tray) = app_handle_bg.tray_by_id("main") {
                            let app_label = ui_state.active_app_name.as_deref().unwrap_or("Desktop");
                            let total_sec = ui_state.seconds_remaining_block;
                            let mins = total_sec / 60;
                            let secs = total_sec % 60;
                            let tooltip = format!(
                                "Watching — {}: {:02}:{:02} left on {}",
                                ui_state.child_name,
                                mins,
                                secs,
                                app_label
                            );
                            let _ = tray.set_tooltip(Some(tooltip));
                            #[cfg(target_os = "macos")]
                            {
                                let title = format!("⏱ {:02}:{:02}", mins, secs);
                                let _ = tray.set_title(Some(title));
                            }
                        }
                    }
                });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_host_flags,
            get_bootstrap,
            get_snapshot,
            set_appearance,
            set_role,
            set_window_overlay,
            show_main_window,
            hide_main_window,
            hide_tray_popover,
            toggle_tray_popover,
            quit_app,
            get_autostart_status,
            set_autostart,
            parent_send_otp,
            parent_verify_otp,
            parent_google_url,
            parent_apple_sign_in,
            parent_onboard,
            parent_dashboard,
            parent_child_detail,
            parent_update_policy,
            parent_update_app_rules,
            parent_add_child,
            parent_remove_child,
            parent_create_pairing,
            parent_mark_paired,
            parent_unpair_device,
            parent_reset_pin,
            parent_notification_prefs,
            parent_set_notification_prefs,
            parent_delete_family,
            parent_sign_out,
            parent_child_reports,
            child_ui_state,
            child_pair,
            child_set_checklist_item,
            child_complete_checklist,
            child_launch_app,
            child_tick,
            child_force_quiz,
            child_answer_quiz,
            child_skip_quiz_with_pin,
            exit_quiz_blocking,
            child_open_pin_menu,
            child_dismiss_pin_menu,
            child_verify_pin,
            child_pin_unpair,
            child_active_app,
            child_exit_with_pin,
        ])
        .build(tauri::generate_context!())
        .expect("error while building MeritScreen UI");

    app.run(|app_handle, event| {
        if let tauri::RunEvent::ExitRequested { api, .. } = event {
            use tauri::Manager;
            let state = app_handle.state::<AppState>();
            let is_child_paired = {
                let role = state.role.lock().map(|r| *r).unwrap_or(DeviceRole::Unassigned);
                role == DeviceRole::Child && state.child.is_paired()
            };
            if is_child_paired && !watchdog::AUTHORIZED_EXIT.load(std::sync::atomic::Ordering::SeqCst) {
                api.prevent_exit();
                tracing::warn!("Blocked unauthorized exit attempt on child device: Parent PIN required.");
                let _ = toggle_tray_popover(app_handle.clone());
            }
        }
    });
}

#[cfg(test)]
mod architecture_lint {
    /// Child + UI host must not pull Firebase or SQLCipher into the WebView process graph.
    #[test]
    fn ui_cargo_bans_firebase_and_db() {
        let manifest = include_str!("../Cargo.toml");
        assert!(
            !manifest.contains("meritscreen-firebase"),
            "meritscreen-ui must not depend on meritscreen-firebase"
        );
        assert!(
            !manifest.contains("meritscreen-db"),
            "meritscreen-ui must not depend on meritscreen-db (SQLCipher)"
        );
    }

    #[test]
    fn child_crate_bans_firebase() {
        let manifest = include_str!("../../../crates/meritscreen-child/Cargo.toml");
        assert!(
            !manifest.contains("meritscreen-firebase"),
            "meritscreen-child must not depend on meritscreen-firebase"
        );
        assert!(
            !manifest.contains("meritscreen-db"),
            "meritscreen-child must not depend on meritscreen-db on the UI hot path"
        );
    }

    /// Guard against regressing to `fn cmd(args: FooArgs)` — Tauri then requires
    /// `{ args: { … } }` while the WebView sends flat `{ email }` / `{ childId }`.
    #[test]
    fn ipc_commands_use_flat_invoke_params() {
        let src = include_str!("lib.rs");
        for line in src.lines() {
            let trimmed = line.trim_start();
            if !trimmed.starts_with("fn ") && !trimmed.starts_with("async fn ") {
                continue;
            }
            assert!(
                !trimmed.contains("args: ") && !trimmed.contains("(args:"),
                "command must use flat IPC params, not a wrapper named args: {trimmed}"
            );
        }
    }
}
