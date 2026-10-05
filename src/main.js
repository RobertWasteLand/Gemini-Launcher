const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const appWindow = window.__TAURI__.window.getCurrentWindow();

const el = (id) => document.getElementById(id);
const POLL_MS = 4000;

let optionsKey = "";
let launching = false;

function setMessage(text, isError = false) {
  const message = el("message");
  message.textContent = text;
  message.classList.toggle("error", isError);
}

function renderOptions(options) {
  const list = el("option-list");
  list.replaceChildren();
  if (!options.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "No options yet.";
    list.append(empty);
    return;
  }
  for (const option of options) {
    const row = document.createElement("label");
    row.className = "option";
    const box = document.createElement("input");
    box.type = "checkbox";
    box.checked = option.enabled;
    box.addEventListener("change", async () => {
      box.disabled = true;
      try {
        render(await invoke("set_option", { id: option.id, enabled: box.checked }));
      } catch (error) {
        box.checked = !box.checked;
        setMessage(String(error), true);
      } finally {
        box.disabled = false;
      }
    });
    const text = document.createElement("span");
    const name = document.createElement("strong");
    name.textContent = option.label;
    text.append(name);
    if (option.description) {
      const description = document.createElement("small");
      description.textContent = option.description;
      text.append(description);
    }
    row.append(box, text);
    list.append(row);
  }
}

function render(status) {
  el("version").textContent = `Launcher v${status.version}`;

  const key = JSON.stringify(status.options);
  if (key !== optionsKey) {
    optionsKey = key;
    renderOptions(status.options);
  }

  const play = el("play");
  const busy = launching || status.busy;
  if (busy) {
    play.textContent = "LAUNCHING…";
    play.disabled = true;
  } else if (status.gameRunning) {
    play.textContent = "RUNNING";
    play.disabled = true;
  } else {
    play.textContent = "PLAY";
    play.disabled = !status.gameDir;
  }

  el("game-path").textContent = status.gameDir
    ? status.gameDir
    : status.steamFound
      ? "Project Zomboid wasn't found in your Steam libraries."
      : "Steam wasn't found.";
  el("game-path").title = status.gameDir || "";
  el("locate").textContent = status.gameDir ? "Change" : "Locate game";
  el("locate").disabled = busy || status.gameRunning;
}

async function refresh() {
  try {
    render(await invoke("status"));
  } catch (error) {
    setMessage(String(error), true);
  }
}

async function loadNotes() {
  try {
    const response = await fetch("patchnotes.txt", { cache: "no-store" });
    el("notes").textContent = await response.text();
  } catch {
    el("notes").textContent = "Patch notes couldn't be loaded.";
  }
}

el("play").addEventListener("click", async () => {
  launching = true;
  setMessage("");
  el("play").textContent = "LAUNCHING…";
  el("play").disabled = true;
  try {
    const status = await invoke("play");
    launching = false;
    render(status);
  } catch (error) {
    launching = false;
    setMessage(String(error), true);
    await refresh();
  }
});

el("locate").addEventListener("click", async () => {
  try {
    render(await invoke("locate_game"));
    setMessage("");
  } catch (error) {
    setMessage(String(error), true);
  }
});

el("close").addEventListener("click", () => appWindow.close());
el("minimize").addEventListener("click", () => appWindow.minimize());

document.addEventListener("contextmenu", (event) => event.preventDefault());
document.addEventListener("keydown", (event) => {
  const key = event.key.toLowerCase();
  if (key === "f5" || ((event.ctrlKey || event.metaKey) && (key === "r" || key === "p" || key === "f"))) {
    event.preventDefault();
  }
});

listen("launch-status", (event) => setMessage(event.payload));
listen("game-exited", () => {
  setMessage("Project Zomboid closed.");
  refresh();
});

loadNotes();
refresh();
setInterval(refresh, POLL_MS);
