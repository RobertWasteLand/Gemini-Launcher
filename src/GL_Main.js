const GL_Invoke = window.__TAURI__.core.invoke;
const GL_Listen = window.__TAURI__.event.listen;
const GL_Window = window.__TAURI__.window.getCurrentWindow();

const GL_Poll_Interval = 4000;
const GL_Notes_Url = "https://raw.githubusercontent.com/RobertWasteLand/Gemini-Launcher/main/GL_Live/GL_Notes.txt";
const GL_Notes_Key = "GL_Notes_Cache";
const GL_Notes_Timeout = 10000;

let GL_Options_Key = "";
let GL_Launching = false;

function GL_Element_Get(id) {
  return document.getElementById(id);
}

function GL_Message_Set(text, isError = false) {
  const message = GL_Element_Get("GL_Message_Text");
  message.textContent = text;
  message.classList.toggle("GL_Error", isError);
}

async function GL_Option_Save(id, value, control) {
  control.disabled = true;
  try {
    GL_Status_Render(await GL_Invoke("GL_Option_Set", { id, value }));
  } catch (error) {
    GL_Message_Set(String(error), true);
    await GL_Status_Refresh();
  } finally {
    control.disabled = false;
  }
}

function GL_Option_Toggle(option) {
  const row = document.createElement("label");
  row.className = "GL_Option";
  const box = document.createElement("input");
  box.type = "checkbox";
  box.checked = option.value === "true";
  box.addEventListener("change", () => GL_Option_Save(option.id, box.checked ? "true" : "false", box));
  row.append(box, GL_Option_Text(option));
  return row;
}

function GL_Option_Choice(option) {
  const row = document.createElement("div");
  row.className = "GL_Option GL_Option_Choice";
  const select = document.createElement("select");
  select.className = "GL_Select";
  select.setAttribute("aria-label", option.label);
  for (const choice of option.choices) {
    const item = document.createElement("option");
    item.value = choice.value;
    item.textContent = choice.label;
    select.append(item);
  }
  select.value = option.value;
  select.addEventListener("change", () => GL_Option_Save(option.id, select.value, select));
  row.append(GL_Option_Text(option), select);
  return row;
}

function GL_Option_Text(option) {
  const text = document.createElement("span");
  const name = document.createElement("strong");
  name.textContent = option.label;
  text.append(name);
  if (option.description) {
    const description = document.createElement("small");
    description.textContent = option.description;
    text.append(description);
  }
  return text;
}

function GL_Options_Render(status) {
  const list = GL_Element_Get("GL_Options_List");
  list.replaceChildren();
  if (!status.options.length) {
    const empty = document.createElement("p");
    empty.className = "GL_Empty";
    empty.textContent = "No options yet.";
    list.append(empty);
    return;
  }
  for (const option of status.options) {
    list.append(option.kind === "choice" ? GL_Option_Choice(option) : GL_Option_Toggle(option));
  }
  const ram = document.createElement("p");
  ram.className = "GL_Empty";
  ram.textContent = `This PC has ${status.memoryTotal} GB of RAM.`;
  list.append(ram);
}

function GL_Status_Render(status) {
  GL_Element_Get("GL_Version_Text").textContent = `Launcher v${status.version}`;
  GL_Element_Get("GL_Update_Text").textContent = status.updateText || "";

  const key = JSON.stringify([status.options, status.memoryTotal]);
  if (key !== GL_Options_Key) {
    GL_Options_Key = key;
    GL_Options_Render(status);
  }

  const play = GL_Element_Get("GL_Play_Button");
  const busy = GL_Launching || status.busy || status.updating;
  if (status.updating) {
    play.textContent = "UPDATING…";
    play.disabled = true;
  } else if (busy) {
    play.textContent = "LAUNCHING…";
    play.disabled = true;
  } else if (status.gameRunning) {
    play.textContent = "RUNNING";
    play.disabled = true;
  } else {
    play.textContent = "PLAY";
    play.disabled = !status.gameDir;
  }

  const path = GL_Element_Get("GL_Game_Path");
  path.textContent = status.gameDir
    ? status.gameDir
    : status.steamFound
      ? "Project Zomboid wasn't found in your Steam libraries."
      : "Steam wasn't found.";
  path.title = status.gameDir || "";
  const locate = GL_Element_Get("GL_Game_Locate");
  locate.textContent = status.gameDir ? "Change" : "Locate game";
  locate.disabled = busy || status.gameRunning;
}

async function GL_Status_Refresh() {
  try {
    GL_Status_Render(await GL_Invoke("GL_Status_Get"));
  } catch (error) {
    GL_Message_Set(String(error), true);
  }
}

function GL_Notes_Cached() {
  try {
    return localStorage.getItem(GL_Notes_Key);
  } catch {
    return null;
  }
}

function GL_Notes_Remember(text) {
  try {
    localStorage.setItem(GL_Notes_Key, text);
  } catch {}
}

async function GL_Notes_Load() {
  const notes = GL_Element_Get("GL_Notes_Text");
  const cached = GL_Notes_Cached();
  if (cached) {
    notes.textContent = cached;
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), GL_Notes_Timeout);
  try {
    const response = await fetch(GL_Notes_Url, { cache: "no-store", signal: controller.signal });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const text = await response.text();
    notes.textContent = text;
    GL_Notes_Remember(text);
  } catch {
    if (!cached) {
      notes.textContent = "Patch notes will show here when you're online.";
    }
  } finally {
    clearTimeout(timer);
  }
}

GL_Element_Get("GL_Play_Button").addEventListener("click", async () => {
  const play = GL_Element_Get("GL_Play_Button");
  GL_Launching = true;
  GL_Message_Set("");
  play.textContent = "LAUNCHING…";
  play.disabled = true;
  try {
    const status = await GL_Invoke("GL_Play");
    GL_Launching = false;
    GL_Status_Render(status);
  } catch (error) {
    GL_Launching = false;
    GL_Message_Set(String(error), true);
    await GL_Status_Refresh();
  }
});

GL_Element_Get("GL_Game_Locate").addEventListener("click", async () => {
  try {
    GL_Status_Render(await GL_Invoke("GL_Game_Locate"));
    GL_Message_Set("");
  } catch (error) {
    GL_Message_Set(String(error), true);
  }
});

GL_Element_Get("GL_Window_Close").addEventListener("click", () => GL_Window.close());
GL_Element_Get("GL_Window_Minimize").addEventListener("click", () => GL_Window.minimize());

document.addEventListener("contextmenu", (event) => event.preventDefault());
document.addEventListener("keydown", (event) => {
  const key = event.key.toLowerCase();
  if (key === "f5" || ((event.ctrlKey || event.metaKey) && (key === "r" || key === "p" || key === "f"))) {
    event.preventDefault();
  }
});

GL_Listen("GL_Launch_Status", (event) => GL_Message_Set(event.payload));
GL_Listen("GL_Update_Status", () => GL_Status_Refresh());
GL_Listen("GL_Game_Exit", () => {
  GL_Message_Set("Project Zomboid closed.");
  GL_Status_Refresh();
});

GL_Notes_Load();
GL_Status_Refresh();
setInterval(GL_Status_Refresh, GL_Poll_Interval);
