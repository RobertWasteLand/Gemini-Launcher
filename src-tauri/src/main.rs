#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
#![allow(non_snake_case, non_camel_case_types, non_upper_case_globals)]

mod GL_Config;
mod GL_Memory;
mod GL_Options;
mod GL_Process;
mod GL_Steam;
mod GL_Update;
mod GL_Vdf;

#[cfg(test)]
mod GL_Tests;

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_dialog::DialogExt;

use crate::GL_Config::GL_Settings;
use crate::GL_Options::{GL_Option_Kind, GL_Options};

const GL_Steam_Timeout: Duration = Duration::from_secs(120);
const GL_Steam_Settle: Duration = Duration::from_secs(3);
const GL_Console_Timeout: Duration = Duration::from_secs(180);
const GL_Console_Poll: Duration = Duration::from_secs(1);
const GL_Console_Slack: Duration = Duration::from_secs(2);
const GL_Launch_Event: &str = "GL_Launch_Status";
const GL_Exit_Event: &str = "GL_Game_Exit";
const GL_Settings_File: &str = "settings.json";
const GL_Window_Main: &str = "GL_Main";
const GL_Join_Hint: &str = "Join the server from the in-game menu.";

pub struct GL_State {
    settings_path: PathBuf,
    settings: Mutex<GL_Settings>,
    memory_total: AtomicU64,
    pub busy: AtomicBool,
    pub updating: AtomicBool,
    pub update_text: Mutex<Option<String>>,
    child_running: Arc<AtomicBool>,
}

#[derive(Serialize)]
struct GL_Choice_View {
    value: String,
    label: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct GL_Option_View {
    id: &'static str,
    label: &'static str,
    description: &'static str,
    kind: &'static str,
    value: String,
    choices: Vec<GL_Choice_View>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct GL_Status {
    version: String,
    steam_found: bool,
    steam_ready: bool,
    game_dir: Option<String>,
    game_running: bool,
    busy: bool,
    updating: bool,
    update_text: Option<String>,
    memory_total: u64,
    options: Vec<GL_Option_View>,
}

fn GL_Game_Dir(s: &GL_Settings) -> Option<PathBuf> {
    if let Some(dir) = &s.game_dir {
        if GL_Steam::GL_Game_Check(dir) {
            return Some(dir.clone());
        }
    }
    GL_Steam::GL_Steam_Find().and_then(|d| GL_Steam::GL_Game_Find(&d))
}

fn GL_Settings_Snapshot(state: &GL_State) -> GL_Settings {
    state.settings.lock().map(|s| s.clone()).unwrap_or_default()
}

fn GL_Status_Build(app: &AppHandle) -> GL_Status {
    let state = app.state::<GL_State>();
    let s = GL_Settings_Snapshot(&state);
    let total = state.memory_total.load(Ordering::SeqCst);
    GL_Status {
        version: app.package_info().version.to_string(),
        steam_found: GL_Steam::GL_Steam_Find().is_some(),
        steam_ready: GL_Process::GL_Steam_Ready(),
        game_dir: GL_Game_Dir(&s).map(|p| p.display().to_string()),
        game_running: state.child_running.load(Ordering::SeqCst) || GL_Process::GL_Game_Running(),
        busy: state.busy.load(Ordering::SeqCst),
        updating: state.updating.load(Ordering::SeqCst),
        update_text: state.update_text.lock().ok().and_then(|t| t.clone()),
        memory_total: total,
        options: GL_Options
            .iter()
            .map(|o| GL_Option_View {
                id: o.id,
                label: o.label,
                description: o.description,
                kind: match o.kind {
                    GL_Option_Kind::Toggle => "toggle",
                    GL_Option_Kind::Choice => "choice",
                },
                value: GL_Options::GL_Option_Value(&s, o, total),
                choices: GL_Options::GL_Option_Choices(o, total)
                    .into_iter()
                    .map(|c| GL_Choice_View { value: c.value, label: c.label })
                    .collect(),
            })
            .collect(),
    }
}

fn GL_Settings_Change(app: &AppHandle, f: impl FnOnce(&mut GL_Settings)) -> Result<(), String> {
    let state = app.state::<GL_State>();
    let mut s = state.settings.lock().map_err(|e| e.to_string())?;
    f(&mut s);
    s.GL_Save(&state.settings_path)
}

fn GL_Launch_Report(app: &AppHandle, text: &str) {
    let _ = app.emit(GL_Launch_Event, text);
}

fn GL_Memory_Text(mb: u64) -> String {
    let gb = mb as f64 / 1024.0;
    if (gb - gb.round()).abs() < 0.05 {
        format!("{:.0} GB", gb)
    } else {
        format!("{:.1} GB", gb)
    }
}

fn GL_Memory_Watch(app: AppHandle, started: SystemTime, requested: u64, running: Arc<AtomicBool>) {
    let Some(path) = GL_Config::GL_Console_Path() else {
        return;
    };
    let deadline = Instant::now() + GL_Console_Timeout;
    while Instant::now() < deadline && running.load(Ordering::SeqCst) {
        let fresh = std::fs::metadata(&path)
            .and_then(|m| m.modified())
            .map(|modified| modified + GL_Console_Slack >= started)
            .unwrap_or(false);
        if fresh {
            if let Ok(bytes) = std::fs::read(&path) {
                if let Some(mb) = GL_Memory::GL_Memory_Parse(&String::from_utf8_lossy(&bytes)) {
                    let text = if GL_Memory::GL_Memory_Match(mb, requested) {
                        format!("Project Zomboid is running with {requested} GB of memory. {GL_Join_Hint}")
                    } else {
                        format!(
                            "Project Zomboid is running with {} of memory (asked for {requested} GB). {GL_Join_Hint}",
                            GL_Memory_Text(mb)
                        )
                    };
                    GL_Launch_Report(&app, &text);
                    return;
                }
            }
        }
        std::thread::sleep(GL_Console_Poll);
    }
}

fn GL_Steam_Wait(app: &AppHandle) -> Result<(), String> {
    if GL_Process::GL_Steam_Ready() {
        return Ok(());
    }
    let steam = GL_Steam::GL_Steam_Find()
        .ok_or("Steam wasn't found. Install or open Steam, then press Play again.")?;
    if GL_Process::GL_Steam_Running() {
        GL_Launch_Report(app, "Waiting for Steam to sign in…");
    } else {
        GL_Launch_Report(app, "Starting Steam…");
        GL_Steam::GL_Steam_Start(&steam).map_err(|e| format!("Couldn't start Steam: {e}"))?;
    }
    let deadline = Instant::now() + GL_Steam_Timeout;
    while !GL_Process::GL_Steam_Ready() {
        if Instant::now() > deadline {
            return Err("Steam didn't finish starting. Open Steam, sign in, then press Play again.".into());
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    std::thread::sleep(GL_Steam_Settle);
    Ok(())
}

fn GL_Game_Launch(app: &AppHandle) -> Result<(), String> {
    let state = app.state::<GL_State>();
    if state.child_running.load(Ordering::SeqCst) || GL_Process::GL_Game_Running() {
        return Err("Project Zomboid is already running.".into());
    }
    let s = GL_Settings_Snapshot(&state);
    let dir = GL_Game_Dir(&s)
        .ok_or("Project Zomboid wasn't found. Use Locate game to choose its folder.")?;
    GL_Steam_Wait(app)?;

    let plan = GL_Options::GL_Launch_Build(&s, state.memory_total.load(Ordering::SeqCst));
    GL_Launch_Report(app, "Launching Project Zomboid…");
    let started = SystemTime::now();
    let mut child = GL_Process::GL_Game_Spawn(&dir, &GL_Process::GL_Game_Args(&plan.jvm, &plan.game), plan.priority)
        .map_err(|e| format!("Couldn't start Project Zomboid: {e}"))?;
    state.child_running.store(true, Ordering::SeqCst);
    let flag = state.child_running.clone();
    let handle = app.clone();
    std::thread::spawn(move || {
        let _ = child.wait();
        flag.store(false, Ordering::SeqCst);
        let _ = handle.emit(GL_Exit_Event, ());
    });
    let watch_app = app.clone();
    let watch_flag = state.child_running.clone();
    let requested = plan.memory;
    std::thread::spawn(move || GL_Memory_Watch(watch_app, started, requested, watch_flag));
    GL_Launch_Report(app, &format!("Project Zomboid is starting. {GL_Join_Hint}"));
    Ok(())
}

#[tauri::command]
async fn GL_Status_Get(app: AppHandle) -> Result<GL_Status, String> {
    Ok(GL_Status_Build(&app))
}

#[tauri::command]
async fn GL_Option_Set(app: AppHandle, id: String, value: String) -> Result<GL_Status, String> {
    let option = GL_Options::GL_Option_Find(&id).ok_or_else(|| format!("Unknown option: {id}"))?;
    let total = app.state::<GL_State>().memory_total.load(Ordering::SeqCst);
    if !GL_Options::GL_Option_Valid(option, &value, total) {
        return Err(format!("{} can't be set to {value}.", option.label));
    }
    GL_Settings_Change(&app, |s| {
        s.options.insert(id, value);
    })?;
    Ok(GL_Status_Build(&app))
}

#[tauri::command]
async fn GL_Game_Locate(app: AppHandle) -> Result<GL_Status, String> {
    let picked = app
        .dialog()
        .file()
        .set_title("Select your Project Zomboid folder")
        .blocking_pick_folder();
    if let Some(picked) = picked {
        let dir = picked.into_path().map_err(|e| e.to_string())?;
        if !GL_Steam::GL_Game_Check(&dir) {
            return Err(format!("{} isn't in that folder.", GL_Steam::GL_Game_Exe));
        }
        GL_Settings_Change(&app, |s| s.game_dir = Some(dir))?;
    }
    Ok(GL_Status_Build(&app))
}

#[tauri::command]
async fn GL_Play(app: AppHandle) -> Result<GL_Status, String> {
    let worker = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let state = worker.state::<GL_State>();
        if state.updating.load(Ordering::SeqCst) {
            return Err("The launcher is updating. Play will be ready in a moment.".to_owned());
        }
        if state.busy.swap(true, Ordering::SeqCst) {
            return Err("Already launching.".to_owned());
        }
        if state.updating.load(Ordering::SeqCst) {
            state.busy.store(false, Ordering::SeqCst);
            return Err("The launcher is updating. Play will be ready in a moment.".to_owned());
        }
        let result = GL_Game_Launch(&worker);
        state.busy.store(false, Ordering::SeqCst);
        result
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(GL_Status_Build(&app))
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window(GL_Window_Main) {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            let settings_path = app.path().app_config_dir()?.join(GL_Settings_File);
            let settings = GL_Settings::GL_Load(&settings_path);
            app.manage(GL_State {
                settings_path,
                settings: Mutex::new(settings),
                memory_total: AtomicU64::new(GL_Memory::GL_Memory_Total()),
                busy: AtomicBool::new(false),
                updating: AtomicBool::new(false),
                update_text: Mutex::new(None),
                child_running: Arc::new(AtomicBool::new(false)),
            });
            tauri::async_runtime::spawn(GL_Update::GL_Update_Run(app.handle().clone()));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![GL_Status_Get, GL_Option_Set, GL_Game_Locate, GL_Play])
        .run(tauri::generate_context!())
        .expect("Gemini Launcher failed to start");
}
