use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime};

use tauri::{AppHandle, Emitter, Manager};

use crate::GL_Addons::GL_Addon;
use crate::GL_Config::GL_Settings;
use crate::GL_Fixes::{GL_Fixes_Choice, GL_Fixes_Jar_Sha, GL_Fixes_Zip_Sha};
use crate::{GL_Addons, GL_Buddy, GL_Config, GL_Fixes, GL_Memory, GL_Options, GL_Patcher, GL_Process, GL_Steam};
use crate::{GL_Cfg_Update, GL_Game_Dir, GL_Settings_Change, GL_Settings_Snapshot, GL_State};

const GL_Steam_Timeout: Duration = Duration::from_secs(120);
const GL_Steam_Settle: Duration = Duration::from_secs(3);
const GL_Steam_Poll: Duration = Duration::from_secs(1);
const GL_Console_Timeout: Duration = Duration::from_secs(180);
const GL_Addons_Timeout: Duration = Duration::from_secs(300);
const GL_Console_Poll: Duration = Duration::from_secs(1);
const GL_Launch_Event: &str = "GL_Launch_Status";
const GL_Exit_Event: &str = "GL_Game_Exit";
const GL_Note_Skipped: &str = "Started without addons this time.";
const GL_Exit_Early: &str = "Project Zomboid closed before the addons loaded. Press Play to start once without them.";

struct GL_Watch {
    before: Option<SystemTime>,
    requested: u64,
    addons: Vec<&'static GL_Addon>,
    notes: Vec<String>,
}

fn GL_Launch_Report(app: &AppHandle, text: &str) {
    let _ = app.emit(GL_Launch_Event, text);
}

fn GL_Memory_Text(mb: u64) -> String {
    let gb = mb as f64 / 1024.0;
    if (gb - gb.round()).abs() < 0.05 {
        format!("{gb:.0} GB")
    } else {
        format!("{gb:.1} GB")
    }
}

fn GL_Console_Modified(path: &Path) -> Option<SystemTime> {
    std::fs::metadata(path).and_then(|m| m.modified()).ok()
}

fn GL_Console_Watch(app: AppHandle, mut watch: GL_Watch, running: Arc<AtomicBool>, settled: Arc<AtomicBool>) {
    let Some(path) = GL_Config::GL_Console_Path() else {
        settled.store(true, Ordering::SeqCst);
        return;
    };
    let begun = Instant::now();
    let mut memory_at: Option<Instant> = None;
    let mut seen = vec![false; watch.addons.len()];
    let mut last: Option<SystemTime> = None;
    while running.load(Ordering::SeqCst) {
        if memory_at.is_none() && begun.elapsed() > GL_Console_Timeout {
            settled.store(true, Ordering::SeqCst);
            return;
        }
        if memory_at.is_some_and(|at| at.elapsed() > GL_Addons_Timeout) {
            settled.store(true, Ordering::SeqCst);
            let missing: Vec<&str> = watch.addons.iter().zip(&seen).filter(|(_, s)| !**s).map(|(a, _)| a.label).collect();
            watch.notes.push(format!("Addons didn't load: {}.", missing.join(", ")));
            GL_Launch_Report(&app, &watch.notes.join(" "));
            return;
        }
        let modified = GL_Console_Modified(&path).filter(|m| watch.before.is_none_or(|b| *m > b));
        if modified.is_some() && modified != last {
            last = modified;
            if let Ok(bytes) = std::fs::read(&path) {
                let text = String::from_utf8_lossy(&bytes);
                for (addon, seen) in watch.addons.iter().zip(seen.iter_mut()) {
                    *seen = *seen || text.contains(&GL_Addons::GL_Addon_Marker(addon));
                }
                let done = seen.iter().all(|s| *s);
                if done {
                    settled.store(true, Ordering::SeqCst);
                }
                if memory_at.is_none() {
                    if let Some(mb) = GL_Memory::GL_Memory_Parse(&text) {
                        memory_at = Some(Instant::now());
                        if !GL_Memory::GL_Memory_Match(mb, watch.requested) {
                            watch.notes.push(format!(
                                "Project Zomboid got {} of memory instead of {} GB.",
                                GL_Memory_Text(mb),
                                watch.requested
                            ));
                            GL_Launch_Report(&app, &watch.notes.join(" "));
                        }
                    }
                }
                if memory_at.is_some() && done {
                    return;
                }
            }
        }
        std::thread::sleep(GL_Console_Poll);
    }
}

fn GL_Addons_Prepare(app: &AppHandle, dir: &Path, addons: &[&'static GL_Addon]) -> Result<String, String> {
    let state = app.state::<GL_State>();
    let bundle = state
        .bundle_dir
        .clone()
        .ok_or_else(|| GL_Buddy::GL_Reason_Damaged.to_owned())?;
    let mut installed = GL_Settings_Snapshot(&state).installed;
    GL_Buddy::GL_Buddy_Prepare(&bundle, dir, addons, state.buddy_config.as_deref(), &mut installed, |record| {
        GL_Settings_Change(app, |s| s.installed = record.clone())
            .map_err(|e| format!("couldn't save the launcher's settings ({e})"))
    })
}

fn GL_Fixes_Load(app: &AppHandle, dir: &Path, zip: &[u8], variant: GL_Fixes::GL_Fixes_Variant) -> Result<String, String> {
    let package = GL_Fixes::GL_Fixes_Unpack(zip, GL_Fixes_Zip_Sha, GL_Fixes_Jar_Sha)?;
    let text = GL_Fixes::GL_Fixes_State(&package, variant)?;
    let mut installed = GL_Settings_Snapshot(&app.state::<GL_State>()).installed;
    GL_Fixes::GL_Fixes_Place(dir, &package, &text, &mut installed, |record| {
        GL_Settings_Change(app, |s| s.installed = record.clone())
            .map_err(|e| format!("couldn't save the launcher's settings ({e})"))
    })?;
    Ok(GL_Fixes::GL_Fixes_Arg(variant))
}

fn GL_Fixes_Prepare(app: &AppHandle, dir: &Path, s: &GL_Settings, total: u64) -> Result<Option<String>, String> {
    if !GL_Fixes::GL_Fixes_On(s) || GL_Buddy::GL_Buddy_Configured(dir) {
        return Ok(None);
    }
    let json = std::fs::read_to_string(dir.join(GL_Fixes::GL_Fixes_Launch_Json)).unwrap_or_default();
    let jar = GL_Fixes::GL_Fixes_Jar_Hash(&dir.join(GL_Fixes::GL_Fixes_Game_Jar), s.fixes_jar.as_ref());
    if jar.is_some() && jar != s.fixes_jar {
        let _ = GL_Settings_Change(app, |s| s.fixes_jar = jar.clone());
    }
    let zip = GL_Fixes::GL_Fixes_Zip_Read();
    match GL_Fixes::GL_Fixes_Decide(&json, jar.as_ref().map(|j| j.sha.as_str()), zip.is_some(), total) {
        GL_Fixes_Choice::Foreign => Err(GL_Fixes::GL_Text_Fixes_Foreign.to_owned()),
        GL_Fixes_Choice::Outdated => Err(GL_Fixes::GL_Text_Fixes_Outdated.to_owned()),
        GL_Fixes_Choice::Waiting => {
            GL_Fixes::GL_Fixes_Start(app);
            Err(GL_Fixes::GL_Text_Fixes_Waiting.to_owned())
        }
        GL_Fixes_Choice::Load(variant) => GL_Fixes_Load(app, dir, zip.as_deref().unwrap_or_default(), variant)
            .map(Some)
            .map_err(|reason| format!("Minidoracat Patcher is off: {reason}.")),
    }
}

fn GL_Patcher_Prepare(app: &AppHandle, s: &GL_Settings) -> Result<(), String> {
    let Some(path) = GL_Config::GL_Game_Options_Path() else {
        return Ok(());
    };
    GL_Patcher::GL_Patcher_Apply(&path, GL_Patcher::GL_Patcher_On(s), s.patcher_saved.as_deref(), |saved| {
        GL_Settings_Change(app, |s| s.patcher_saved = saved)
    })
    .map_err(|e| format!("Robert Patcher couldn't change your game settings ({e})."))
}

fn GL_Steam_Wait(app: &AppHandle) -> Result<(), String> {
    if GL_Process::GL_Steam_Ready() {
        return Ok(());
    }
    let steam = GL_Steam::GL_Steam_Find()
        .ok_or("Steam wasn't found. Install or open Steam, then press Play again.")?;
    if GL_Process::GL_Steam_Running() {
        GL_Launch_Report(app, "Waiting for Steam to sign in...");
    } else {
        GL_Launch_Report(app, "Starting Steam...");
        GL_Steam::GL_Steam_Start(&steam).map_err(|e| format!("Couldn't start Steam: {e}"))?;
    }
    let deadline = Instant::now() + GL_Steam_Timeout;
    while !GL_Process::GL_Steam_Ready() {
        if Instant::now() > deadline {
            return Err("Steam didn't finish starting. Open Steam, sign in, then press Play again.".into());
        }
        std::thread::sleep(GL_Steam_Poll);
    }
    std::thread::sleep(GL_Steam_Settle);
    Ok(())
}

pub fn GL_Game_Launch(app: &AppHandle) -> Result<(), String> {
    let state = app.state::<GL_State>();
    if state.child_running.load(Ordering::SeqCst) || GL_Process::GL_Game_Running() {
        return Err("Project Zomboid is already running.".into());
    }
    let s = GL_Settings_Snapshot(&state);
    let dir = GL_Game_Dir(&s).ok_or("Project Zomboid wasn't found. Use Locate game to choose its folder.")?;
    GL_Steam_Wait(app)?;

    let total = state.memory_total.load(Ordering::SeqCst);
    let mut plan = GL_Options::GL_Launch_Build(&s, total);
    let active = GL_Addons::GL_Addons_Active(&s);
    let mut loaded: Vec<&'static GL_Addon> = Vec::new();
    let mut notes: Vec<String> = Vec::new();
    let skip = state.addons_skip.swap(false, Ordering::SeqCst);
    if let Err(note) = GL_Patcher_Prepare(app, &s) {
        notes.push(note);
    }
    if !skip {
        match GL_Fixes_Prepare(app, &dir, &s, total) {
            Ok(Some(arg)) => plan.jvm.push(arg),
            Ok(None) => {}
            Err(note) => notes.push(note),
        }
    }
    if skip && !active.is_empty() {
        notes.push(GL_Note_Skipped.to_owned());
    } else if !active.is_empty() {
        match GL_Addons_Prepare(app, &dir, &active) {
            Ok(arg) => {
                plan.jvm.push(arg);
                loaded = active;
                if let Err(e) = GL_Cfg_Update(app) {
                    notes.push(format!("The game's Environment file couldn't be updated ({e})."));
                }
            }
            Err(reason) => notes.push(format!("Addons are off: {reason}.")),
        }
    }

    let before = GL_Config::GL_Console_Path().and_then(|p| GL_Console_Modified(&p));
    let mut child = GL_Process::GL_Game_Spawn(&dir, &GL_Process::GL_Game_Args(&plan.jvm, &plan.game), plan.priority)
        .map_err(|e| format!("Couldn't start Project Zomboid: {e}"))?;
    state.child_running.store(true, Ordering::SeqCst);
    GL_Launch_Report(app, &notes.join(" "));

    let settled = Arc::new(AtomicBool::new(loaded.is_empty()));
    let exit_app = app.clone();
    let exit_settled = settled.clone();
    let exit_flag = state.child_running.clone();
    std::thread::spawn(move || {
        let _ = child.wait();
        exit_flag.store(false, Ordering::SeqCst);
        let text = if exit_settled.load(Ordering::SeqCst) {
            ""
        } else {
            exit_app.state::<GL_State>().addons_skip.store(true, Ordering::SeqCst);
            GL_Exit_Early
        };
        let _ = exit_app.emit(GL_Exit_Event, text);
    });

    let watch = GL_Watch {
        before,
        requested: plan.memory,
        addons: loaded,
        notes,
    };
    let watch_app = app.clone();
    let watch_flag = state.child_running.clone();
    std::thread::spawn(move || GL_Console_Watch(watch_app, watch, watch_flag, settled));
    Ok(())
}
