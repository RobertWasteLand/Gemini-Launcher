const GL_Invoke = window.__TAURI__.core.invoke;
const GL_Listen = window.__TAURI__.event.listen;
const GL_Window = window.__TAURI__.window.getCurrentWindow();

const GL_Version_Display = "V1.0.0";
const GL_Poll_Interval = 4000;
const GL_Notes_Url = "https://raw.githubusercontent.com/RobertWasteLand/Gemini-Launcher/main/GL_Live/GL_Notes.txt";
const GL_Notes_Key = "GL_Notes_Cache";
const GL_Notes_Timeout = 10000;
const GL_Tab_Key = "GL_Tab";
const GL_Level_Names = ["Off", "Low", "High"];

let GL_Options_Key = "";
let GL_Environment_Key = "";
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
  const name = document.createElement("strong");
  name.textContent = option.label;
  return name;
}

function GL_Addon_Row(addon) {
  const row = document.createElement("label");
  row.className = "GL_Option";
  const box = document.createElement("input");
  box.type = "checkbox";
  box.checked = addon.enabled;
  box.addEventListener("change", () => GL_Option_Save(addon.id, box.checked ? "true" : "false", box));
  row.append(box, GL_Option_Text(addon));
  return row;
}

function GL_Addons_Render(list, addons) {
  const divider = document.createElement("hr");
  divider.className = "GL_Divider";
  const heading = document.createElement("h2");
  heading.textContent = "Addons";
  list.append(divider, heading);
  if (!addons.length) {
    const empty = document.createElement("p");
    empty.className = "GL_Empty";
    empty.textContent = "No addons yet.";
    list.append(empty);
    return;
  }
  for (const addon of addons) {
    list.append(GL_Addon_Row(addon));
  }
}

function GL_Options_Render(status) {
  const list = GL_Element_Get("GL_Options_List");
  list.replaceChildren();
  if (!status.options.length) {
    const empty = document.createElement("p");
    empty.className = "GL_Empty";
    empty.textContent = "No options yet.";
    list.append(empty);
  }
  for (const option of status.options) {
    list.append(option.kind === "choice" ? GL_Option_Choice(option) : GL_Option_Toggle(option));
  }
  GL_Addons_Render(list, status.addons);
}

function GL_Feature_Switch(feature) {
  const row = document.createElement("label");
  row.className = "GL_Option";
  const box = document.createElement("input");
  box.type = "checkbox";
  box.checked = feature.value > feature.min;
  box.addEventListener("change", () => GL_Option_Save(feature.id, String(box.checked ? feature.max : feature.min), box));
  row.append(box, GL_Option_Text({ label: feature.name }));
  return row;
}

function GL_Feature_Level(feature) {
  const row = document.createElement("div");
  row.className = "GL_Option GL_Feature_Row";
  const select = document.createElement("select");
  select.className = "GL_Select GL_Select_Small";
  select.setAttribute("aria-label", feature.name);
  for (let value = feature.min; value <= feature.max; value++) {
    const item = document.createElement("option");
    item.value = String(value);
    item.textContent = GL_Level_Names[value] || String(value);
    select.append(item);
  }
  select.value = String(feature.value);
  select.addEventListener("change", () => GL_Option_Save(feature.id, select.value, select));
  row.append(GL_Option_Text({ label: feature.name }), select);
  return row;
}

function GL_Feature_Slider(feature) {
  const row = document.createElement("div");
  row.className = "GL_Option GL_Feature_Row";
  const slider = document.createElement("input");
  slider.type = "range";
  slider.className = "GL_Slider";
  slider.min = String(feature.min);
  slider.max = String(feature.max);
  slider.value = String(feature.value);
  slider.setAttribute("aria-label", feature.name);
  const shown = document.createElement("span");
  shown.className = "GL_Slider_Value";
  shown.textContent = String(feature.value);
  slider.addEventListener("input", () => {
    shown.textContent = slider.value;
  });
  slider.addEventListener("change", () => GL_Option_Save(feature.id, slider.value, slider));
  row.append(GL_Option_Text({ label: feature.name }), slider, shown);
  return row;
}

function GL_Environment_Render(status) {
  const list = GL_Element_Get("GL_Environment_List");
  list.replaceChildren();
  const groups = new Map();
  for (const feature of status.features) {
    if (!groups.has(feature.group)) {
      const group = document.createElement("div");
      group.className = "GL_Group";
      const heading = document.createElement("h3");
      heading.textContent = feature.group;
      group.append(heading);
      groups.set(feature.group, group);
      list.append(group);
    }
    const row =
      feature.kind === "slider" ? GL_Feature_Slider(feature) : feature.kind === "level" ? GL_Feature_Level(feature) : GL_Feature_Switch(feature);
    groups.get(feature.group).append(row);
  }
  const environment = GL_Element_Get("GL_Environment");
  environment.classList.toggle("GL_Disabled", !status.featuresOn);
  GL_Element_Get("GL_Environment_Off").hidden = status.featuresOn;
  for (const control of document.querySelectorAll("#GL_Environment input, #GL_Environment select, #GL_Presets button")) {
    control.disabled = !status.featuresOn;
  }
}

function GL_Tab_Show(page) {
  for (const tab of document.querySelectorAll(".GL_Tab")) {
    const active = tab.dataset.page === page;
    tab.classList.toggle("GL_Tab_Active", active);
    tab.setAttribute("aria-selected", String(active));
    GL_Element_Get(tab.dataset.page).hidden = !active;
  }
  GL_Element_Get("GL_Presets").hidden = page !== "GL_Environment";
  try {
    localStorage.setItem(GL_Tab_Key, page);
  } catch {}
}

function GL_Tab_Saved() {
  try {
    const page = localStorage.getItem(GL_Tab_Key);
    return page && GL_Element_Get(page) ? page : "GL_Notes_Text";
  } catch {
    return "GL_Notes_Text";
  }
}

async function GL_Preset_Save(preset) {
  try {
    GL_Status_Render(await GL_Invoke("GL_Preset_Set", { preset }));
  } catch (error) {
    GL_Message_Set(String(error), true);
    await GL_Status_Refresh();
  }
}

function GL_Status_Render(status) {
  const version = GL_Element_Get("GL_Version_Text");
  version.textContent = `Launcher ${GL_Version_Display}`;
  GL_Element_Get("GL_Update_Text").textContent = status.updateText || "";

  const key = JSON.stringify([status.options, status.addons]);
  if (key !== GL_Options_Key) {
    GL_Options_Key = key;
    GL_Options_Render(status);
  }
  const environmentKey = JSON.stringify([status.features, status.featuresOn]);
  if (environmentKey !== GL_Environment_Key) {
    GL_Environment_Key = environmentKey;
    GL_Environment_Render(status);
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

for (const tab of document.querySelectorAll(".GL_Tab")) {
  tab.addEventListener("click", () => GL_Tab_Show(tab.dataset.page));
}
GL_Element_Get("GL_Preset_Vanilla").addEventListener("click", () => GL_Preset_Save("vanilla"));
GL_Element_Get("GL_Preset_Gemini").addEventListener("click", () => GL_Preset_Save("gemini"));

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
GL_Listen("GL_Game_Exit", (event) => {
  GL_Message_Set(event.payload);
  GL_Status_Refresh();
});

GL_Tab_Show(GL_Tab_Saved());
GL_Notes_Load();
GL_Status_Refresh();
window.addEventListener("focus", GL_Status_Refresh);
setInterval(() => {
  if (document.hasFocus()) {
    GL_Status_Refresh();
  }
}, GL_Poll_Interval);
