#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod game;
mod options;
mod procs;
mod settings;
mod steam;
mod vdf;

#[cfg(test)]
mod tests;

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_dialog::DialogExt;

const STEAM_TIMEOUT: Duration = Duration::from_secs(120);
const STEAM_SETTLE: Duration = Duration::from_secs(3);

struct AppState {
    settings_path: PathBuf,
    settings: Mutex<settings::Settings>,
    busy: AtomicBool,
    child_running: Arc<AtomicBool>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OptionView {
    id: &'static str,
    label: &'static str,
    description: &'static str,
    enabled: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Status {
    version: String,
    steam_found: bool,
    steam_ready: bool,
    game_dir: Option<String>,
    game_running: bool,
    busy: bool,
    options: Vec<OptionView>,
}

fn option_enabled(s: &settings::Settings, o: &options::LaunchOption) -> bool {
    s.options.get(o.id).copied().unwrap_or(o.default)
}

fn resolve_game_dir(s: &settings::Settings) -> Option<PathBuf> {
    if let Some(dir) = &s.game_dir {
        if steam::is_game_dir(dir) {
            return Some(dir.clone());
        }
    }
    steam::steam_dir().and_then(|d| steam::find_game(&d))
}

fn snapshot(state: &AppState) -> settings::Settings {
    state.settings.lock().map(|s| s.clone()).unwrap_or_default()
}

fn build_status(app: &AppHandle) -> Status {
    let state = app.state::<AppState>();
    let s = snapshot(&state);
    Status {
        version: app.package_info().version.to_string(),
        steam_found: steam::steam_dir().is_some(),
        steam_ready: procs::steam_ready(),
        game_dir: resolve_game_dir(&s).map(|p| p.display().to_string()),
        game_running: state.child_running.load(Ordering::SeqCst) || procs::game_running(),
        busy: state.busy.load(Ordering::SeqCst),
        options: options::OPTIONS
            .iter()
            .map(|o| OptionView {
                id: o.id,
                label: o.label,
                description: o.description,
                enabled: option_enabled(&s, o),
            })
            .collect(),
    }
}

fn update_settings(app: &AppHandle, f: impl FnOnce(&mut settings::Settings)) -> Result<(), String> {
    let state = app.state::<AppState>();
    let mut s = state.settings.lock().map_err(|e| e.to_string())?;
    f(&mut s);
    s.save(&state.settings_path)
}

fn say(app: &AppHandle, text: &str) {
    let _ = app.emit("launch-status", text);
}

fn wait_for_steam(app: &AppHandle) -> Result<(), String> {
    if procs::steam_ready() {
        return Ok(());
    }
    let steam = steam::steam_dir()
        .ok_or("Steam wasn't found. Install or open Steam, then press Play again.")?;
    if procs::steam_process_running() {
        say(app, "Waiting for Steam to sign in…");
    } else {
        say(app, "Starting Steam…");
        steam::start(&steam).map_err(|e| format!("Couldn't start Steam: {e}"))?;
    }
    let deadline = Instant::now() + STEAM_TIMEOUT;
    while !procs::steam_ready() {
        if Instant::now() > deadline {
            return Err("Steam didn't finish starting. Open Steam, sign in, then press Play again.".into());
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    std::thread::sleep(STEAM_SETTLE);
    Ok(())
}

fn launch(app: &AppHandle) -> Result<(), String> {
    let state = app.state::<AppState>();
    if state.child_running.load(Ordering::SeqCst) || procs::game_running() {
        return Err("Project Zomboid is already running.".into());
    }
    let s = snapshot(&state);
    let dir = resolve_game_dir(&s)
        .ok_or("Project Zomboid wasn't found. Use Locate game to choose its folder.")?;
    wait_for_steam(app)?;

    let mut jvm = Vec::new();
    let mut game_args = Vec::new();
    for o in options::OPTIONS.iter().filter(|o| option_enabled(&s, o)) {
        jvm.extend(o.jvm_args.iter().map(|a| a.to_string()));
        game_args.extend(o.game_args.iter().map(|a| a.to_string()));
    }

    say(app, "Launching Project Zomboid…");
    let mut child = game::spawn(&dir, &game::build_args(&jvm, &game_args))
        .map_err(|e| format!("Couldn't start Project Zomboid: {e}"))?;
    state.child_running.store(true, Ordering::SeqCst);
    let flag = state.child_running.clone();
    let handle = app.clone();
    std::thread::spawn(move || {
        let _ = child.wait();
        flag.store(false, Ordering::SeqCst);
        let _ = handle.emit("game-exited", ());
    });
    say(app, "Project Zomboid is starting. Join the server from the in-game menu.");
    Ok(())
}

#[tauri::command]
async fn status(app: AppHandle) -> Result<Status, String> {
    Ok(build_status(&app))
}

#[tauri::command]
async fn set_option(app: AppHandle, id: String, enabled: bool) -> Result<Status, String> {
    if options::find(&id).is_none() {
        return Err(format!("Unknown option: {id}"));
    }
    update_settings(&app, |s| {
        s.options.insert(id, enabled);
    })?;
    Ok(build_status(&app))
}

#[tauri::command]
async fn locate_game(app: AppHandle) -> Result<Status, String> {
    let picked = app
        .dialog()
        .file()
        .set_title("Select your Project Zomboid folder")
        .blocking_pick_folder();
    if let Some(picked) = picked {
        let dir = picked.into_path().map_err(|e| e.to_string())?;
        if !steam::is_game_dir(&dir) {
            return Err(format!("{} isn't in that folder.", steam::GAME_EXE));
        }
        update_settings(&app, |s| s.game_dir = Some(dir))?;
    }
    Ok(build_status(&app))
}

#[tauri::command]
async fn play(app: AppHandle) -> Result<Status, String> {
    let worker = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let state = worker.state::<AppState>();
        if state.busy.swap(true, Ordering::SeqCst) {
            return Err("Already launching.".to_owned());
        }
        let result = launch(&worker);
        state.busy.store(false, Ordering::SeqCst);
        result
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(build_status(&app))
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let settings_path = app.path().app_config_dir()?.join("settings.json");
            let settings = settings::Settings::load(&settings_path);
            app.manage(AppState {
                settings_path,
                settings: Mutex::new(settings),
                busy: AtomicBool::new(false),
                child_running: Arc::new(AtomicBool::new(false)),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![status, set_option, locate_game, play])
        .run(tauri::generate_context!())
        .expect("Gemini Launcher failed to start");
}
