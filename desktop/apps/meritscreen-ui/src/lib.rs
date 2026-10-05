//! Tauri UI host.
//!
//! - Child path: local snapshot / IPC only — never Firebase.
//! - Parent path: Rust commands → `meritscreen-parent` use cases (Firebase behind traits).
//! - WebView/TS never imports Firebase or SQLCipher.

use meritscreen_child::{ChildRuntime, ChildUiState};
use meritscreen_core::{Appearance, ChildPolicy, DeviceRole, SessionPhase};
use meritscreen_ipc::UiSnapshot;
use meritscreen_parent::{
    ChildCard, ChildDetail, DashboardSnapshot, NotificationPrefs, PairingOffer, ParentProfile,
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
}

static HOST_FLAGS: OnceLock<HostFlags> = OnceLock::new();

fn parse_host_flags() -> HostFlags {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut overlay = None;
    let mut prewarm = false;
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
            _ => {}
        }
        i += 1;
    }
    HostFlags { overlay, prewarm }
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
fn set_appearance(
    appearance: String,
    state: State<'_, AppState>,
) -> Result<Appearance, String> {
    let appearance = Appearance::parse(&appearance);
    state.store.save(appearance).map_err(|e| e.to_string())?;
    *state.appearance.lock().expect("appearance lock") = appearance;
    Ok(appearance)
}

#[tauri::command]
fn set_role(role: String, state: State<'_, AppState>) -> DeviceRole {
    let role = DeviceRole::parse(&role);
    *state.role.lock().expect("role lock") = role;
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
    state: State<'_, AppState>,
) -> Result<ParentProfile, String> {
    let profile = state
        .parent
        .verify_email_otp(&email, &code)
        .await
        .map_err(|e| e.user_message().to_string())?;
    *state.role.lock().expect("role lock") = DeviceRole::Parent;
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
fn parent_sign_out(state: State<'_, AppState>) {
    state.parent.sign_out();
    *state.role.lock().expect("role lock") = DeviceRole::Unassigned;
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
    state: State<'_, AppState>,
) -> Result<ChildUiState, String> {
    state
        .child
        .pair(&code, child_name.as_deref())
        .map_err(|e| e.user_message().to_string())?;
    *state.role.lock().expect("role lock") = DeviceRole::Child;
    Ok(state.child.ui_state())
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
    state
        .child
        .verify_pin(&pin)
        .map_err(|e| e.user_message().to_string())
}

#[tauri::command]
fn child_pin_unpair(pin: String, state: State<'_, AppState>) -> Result<ChildUiState, String> {
    state
        .child
        .pin_unpair(&pin)
        .map_err(|e| e.user_message().to_string())?;
    *state.role.lock().expect("role lock") = DeviceRole::Unassigned;
    Ok(state.child.ui_state())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    init_tracing("meritscreen_ui=info,meritscreen_parent=info,meritscreen_child=info");
    let _ = HOST_FLAGS.set(parse_host_flags());

    let store = AppearanceStore::default_location().unwrap_or_else(|_| {
        let dir = std::env::temp_dir().join("meritscreen-ui");
        let _ = std::fs::create_dir_all(&dir);
        AppearanceStore::in_dir(dir)
    });
    let appearance = store.load().unwrap_or_default();

    let state = AppState {
        role: Mutex::new(DeviceRole::Unassigned),
        appearance: Mutex::new(appearance),
        store,
        parent: ParentSession::from_env(),
        child: ChildRuntime::new(),
    };

    tauri::Builder::default()
        .manage(state)
        .invoke_handler(tauri::generate_handler![
            get_host_flags,
            get_bootstrap,
            get_snapshot,
            set_appearance,
            set_role,
            parent_send_otp,
            parent_verify_otp,
            parent_google_url,
            parent_apple_sign_in,
            parent_onboard,
            parent_dashboard,
            parent_child_detail,
            parent_update_policy,
            parent_create_pairing,
            parent_mark_paired,
            parent_reset_pin,
            parent_notification_prefs,
            parent_set_notification_prefs,
            parent_delete_family,
            parent_sign_out,
            child_ui_state,
            child_pair,
            child_set_checklist_item,
            child_complete_checklist,
            child_launch_app,
            child_tick,
            child_force_quiz,
            child_answer_quiz,
            child_open_pin_menu,
            child_dismiss_pin_menu,
            child_verify_pin,
            child_pin_unpair,
        ])
        .run(tauri::generate_context!())
        .expect("error while running MeritScreen UI");
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
