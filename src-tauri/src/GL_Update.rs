use std::sync::atomic::Ordering;
use std::time::Duration;

use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_updater::{Update, UpdaterExt};

use crate::GL_State;

const GL_Update_Event: &str = "GL_Update_Status";
const GL_Update_Timeout: Duration = Duration::from_secs(20);
const GL_Update_Poll: Duration = Duration::from_millis(500);
const GL_Update_From_Key: &str = "Update from:";
pub const GL_Text_From_Gemini: &str = "From Gemini.";
pub const GL_Text_From_Fixes: &str = "From Minidoracat Patcher.";
pub const GL_Text_From_Both: &str = "From Gemini and Minidoracat Patcher.";

fn GL_Update_Text(app: &AppHandle, text: Option<&str>) {
    let state = app.state::<GL_State>();
    if let Ok(mut slot) = state.update_text.lock() {
        *slot = text.map(str::to_owned);
    }
    let _ = app.emit(GL_Update_Event, ());
}

fn GL_Update_Report(app: &AppHandle, text: &str) {
    GL_Update_Text(app, Some(text));
}

fn GL_Update_Flag(app: &AppHandle, on: bool) {
    app.state::<GL_State>().updating.store(on, Ordering::SeqCst);
    let _ = app.emit(GL_Update_Event, ());
}

async fn GL_Update_Claim(app: &AppHandle) {
    let state = app.state::<GL_State>();
    loop {
        state.updating.store(true, Ordering::SeqCst);
        if !state.busy.load(Ordering::SeqCst) {
            return;
        }
        state.updating.store(false, Ordering::SeqCst);
        tokio::time::sleep(GL_Update_Poll).await;
    }
}

pub fn GL_Update_From_Text(notes: Option<&str>) -> &'static str {
    let from = notes
        .unwrap_or_default()
        .lines()
        .find_map(|line| line.trim().strip_prefix(GL_Update_From_Key))
        .map(str::trim)
        .unwrap_or_default();
    if from.eq_ignore_ascii_case("Both") {
        GL_Text_From_Both
    } else if from.eq_ignore_ascii_case("Minidoracat Patcher") {
        GL_Text_From_Fixes
    } else {
        GL_Text_From_Gemini
    }
}

fn GL_Update_Take(app: &AppHandle) -> Option<Update> {
    app.state::<GL_State>().update_offer.lock().ok().and_then(|mut slot| slot.take())
}

pub fn GL_Update_Offer_From(app: &AppHandle) -> Option<&'static str> {
    let state = app.state::<GL_State>();
    let slot = state.update_offer.lock().ok()?;
    slot.as_ref().map(|update| GL_Update_From_Text(update.body.as_deref()))
}

pub fn GL_Update_Later(app: &AppHandle) {
    GL_Update_Take(app);
}

pub fn GL_Update_Accept(app: &AppHandle) {
    let Some(update) = GL_Update_Take(app) else {
        return;
    };
    app.state::<GL_State>().updating.store(true, Ordering::SeqCst);
    tauri::async_runtime::spawn(GL_Update_Install(app.clone(), update));
}

pub async fn GL_Update_Run(app: AppHandle) {
    if cfg!(debug_assertions) {
        return;
    }
    GL_Update_Report(&app, "Checking for updates...");
    let checked = match app.updater_builder().timeout(GL_Update_Timeout).build() {
        Ok(updater) => updater.check().await,
        Err(e) => Err(e),
    };
    match checked {
        Ok(Some(update)) => {
            if let Ok(mut slot) = app.state::<GL_State>().update_offer.lock() {
                *slot = Some(update);
            }
            GL_Update_Text(&app, None);
        }
        Ok(None) => GL_Update_Text(&app, None),
        Err(_) => GL_Update_Report(&app, "Couldn't check for updates"),
    }
}

async fn GL_Update_Install(app: AppHandle, update: Update) {
    GL_Update_Claim(&app).await;
    GL_Update_Report(&app, "Downloading update...");

    let progress_app = app.clone();
    let mut received: u64 = 0;
    let mut shown: Option<u64> = None;
    let finished_app = app.clone();
    let result = update
        .download_and_install(
            move |chunk, total| {
                received += chunk as u64;
                if let Some(total) = total.filter(|t| *t > 0) {
                    let percent = (received * 100 / total).min(100);
                    if shown != Some(percent) {
                        shown = Some(percent);
                        GL_Update_Report(&progress_app, &format!("Downloading update... {percent}%"));
                    }
                }
            },
            move || GL_Update_Report(&finished_app, "Installing update..."),
        )
        .await;

    match result {
        Ok(()) => app.restart(),
        Err(_) => {
            GL_Update_Report(&app, "Update failed. It's offered next time.");
            GL_Update_Flag(&app, false);
        }
    }
}
