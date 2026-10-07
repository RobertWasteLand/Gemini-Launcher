use std::sync::atomic::Ordering;
use std::time::Duration;

use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_updater::UpdaterExt;

use crate::GL_State;

const GL_Update_Event: &str = "GL_Update_Status";
const GL_Update_Timeout: Duration = Duration::from_secs(20);
const GL_Update_Poll: Duration = Duration::from_millis(500);

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

pub async fn GL_Update_Run(app: AppHandle) {
    if cfg!(debug_assertions) {
        return;
    }
    GL_Update_Report(&app, "Checking for updates…");
    let checked = match app.updater_builder().timeout(GL_Update_Timeout).build() {
        Ok(updater) => updater.check().await,
        Err(e) => Err(e),
    };
    let update = match checked {
        Ok(Some(update)) => update,
        Ok(None) => {
            GL_Update_Text(&app, None);
            return;
        }
        Err(_) => {
            GL_Update_Report(&app, "Couldn't check for updates");
            return;
        }
    };

    GL_Update_Claim(&app).await;
    GL_Update_Report(&app, "Downloading update…");

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
                        GL_Update_Report(&progress_app, &format!("Downloading update… {percent}%"));
                    }
                }
            },
            move || GL_Update_Report(&finished_app, "Installing update…"),
        )
        .await;

    match result {
        Ok(()) => app.restart(),
        Err(_) => {
            GL_Update_Report(&app, "Update failed. You can still play; it will try again next launch.");
            GL_Update_Flag(&app, false);
        }
    }
}
