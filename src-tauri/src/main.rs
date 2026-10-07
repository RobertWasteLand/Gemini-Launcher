#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
#![allow(non_snake_case, non_camel_case_types, non_upper_case_globals)]

mod GL_Addons;
mod GL_Buddy;
mod GL_Config;
mod GL_Launch;
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

use serde::Serialize;
use tauri::{AppHandle, Manager};
use tauri_plugin_dialog::DialogExt;

use crate::GL_Config::GL_Settings;
use crate::GL_Options::{GL_Option_Kind, GL_Options};

const GL_Cleanup_Flag: &str = "--GL_Cleanup";
const GL_Window_Main: &str = "GL_Main";

pub struct GL_State {
    settings_path: PathBuf,
    settings: Mutex<GL_Settings>,
    memory_total: AtomicU64,
    pub busy: AtomicBool,
    pub updating: AtomicBool,
    pub update_text: Mutex<Option<String>>,
    child_running: Arc<AtomicBool>,
    bundle_dir: Option<PathBuf>,
    buddy_config: Option<PathBuf>,
    addons_skip: AtomicBool,
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
struct GL_Addon_View {
    id: &'static str,
    label: &'static str,
    description: &'static str,
    required: bool,
    enabled: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct GL_Status {
    version: String,
    steam_found: bool,
    game_dir: Option<String>,
    game_running: bool,
    busy: bool,
    updating: bool,
    update_text: Option<String>,
    memory_total: u64,
    options: Vec<GL_Option_View>,
    addons: Vec<GL_Addon_View>,
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
        addons: GL_Addons::GL_Addons_Sorted()
            .into_iter()
            .map(|a| GL_Addon_View {
                id: a.id,
                label: a.label,
                description: a.description,
                required: a.required,
                enabled: GL_Addons::GL_Addon_Enabled(&s, a),
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

#[tauri::command]
async fn GL_Status_Get(app: AppHandle) -> Result<GL_Status, String> {
    Ok(GL_Status_Build(&app))
}

#[tauri::command]
async fn GL_Option_Set(app: AppHandle, id: String, value: String) -> Result<GL_Status, String> {
    if let Some(addon) = GL_Addons::GL_Addon_Find(&id) {
        if addon.required {
            return Err(format!("{} is required and can't be turned off.", addon.label));
        }
        if !GL_Addons::GL_Addon_Valid(&value) {
            return Err(format!("{} can't be set to {value}.", addon.label));
        }
    } else {
        let option = GL_Options::GL_Option_Find(&id).ok_or_else(|| format!("Unknown option: {id}"))?;
        let total = app.state::<GL_State>().memory_total.load(Ordering::SeqCst);
        if !GL_Options::GL_Option_Valid(option, &value, total) {
            return Err(format!("{} can't be set to {value}.", option.label));
        }
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
        let result = GL_Launch::GL_Game_Launch(&worker);
        state.busy.store(false, Ordering::SeqCst);
        result
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(GL_Status_Build(&app))
}

fn GL_Cleanup_Run() {
    let Some(dir) = GL_Config::GL_Config_Dir() else {
        return;
    };
    let path = dir.join(GL_Config::GL_Settings_File);
    let mut s = GL_Settings::GL_Load(&path);
    if s.installed.is_empty() {
        return;
    }
    GL_Buddy::GL_Buddy_Clean(&mut s.installed);
    let _ = s.GL_Save(&path);
}

fn main() {
    if std::env::args().skip(1).any(|a| a == GL_Cleanup_Flag) {
        GL_Cleanup_Run();
        return;
    }
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
            let config_dir = app.path().app_config_dir()?;
            let settings_path = config_dir.join(GL_Config::GL_Settings_File);
            let settings = GL_Settings::GL_Load(&settings_path);
            app.manage(GL_State {
                settings_path,
                settings: Mutex::new(settings),
                memory_total: AtomicU64::new(GL_Memory::GL_Memory_Total()),
                busy: AtomicBool::new(false),
                updating: AtomicBool::new(false),
                update_text: Mutex::new(None),
                child_running: Arc::new(AtomicBool::new(false)),
                bundle_dir: app.path().resource_dir().ok().map(|d| d.join(GL_Buddy::GL_Bundle_Folder)),
                buddy_config: Some(config_dir.join(GL_Buddy::GL_Buddy_Config)),
                addons_skip: AtomicBool::new(false),
            });
            tauri::async_runtime::spawn(GL_Update::GL_Update_Run(app.handle().clone()));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![GL_Status_Get, GL_Option_Set, GL_Game_Locate, GL_Play])
        .run(tauri::generate_context!())
        .expect("Gemini Launcher failed to start");
}
