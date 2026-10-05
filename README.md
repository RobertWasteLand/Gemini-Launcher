# Gemini Launcher
The Main Launcher For The Gemini Discord Server

Windows launcher for the Gemini Season Two Project Zomboid (Build 42) server, built with [Tauri 2](https://tauri.app).

## Layout

- `src/` — the launcher window (HTML, CSS, JS). Placeholder look until the art lands. `patchnotes.txt` fills the patch notes box.
- `src-tauri/` — the Rust side: finds Steam and Project Zomboid, starts Steam if needed, launches the game.
- `src-tauri/src/options.rs` — the list of toggles shown in the Options panel and the launch arguments each one adds.
- `.github/workflows/build.yml` — builds the Windows installer on every push to `main`; download it from the run's artifacts.

## Building locally

Needs [Node.js](https://nodejs.org) 22+, [Rust](https://rustup.rs) and the Microsoft C++ Build Tools.

```
npm install
npm run dev
npm run build
```

`npm run build` writes the installer to `src-tauri/target/release/bundle/nsis/`.

Player settings are saved in `%APPDATA%\com.robertwasteland.geminilauncher\settings.json`.
