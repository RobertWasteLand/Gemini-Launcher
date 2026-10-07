# Gemini Launcher

This Launcher Is For Use Specifically For Gemini Season Two And Utilizes ZombieBuddy!

## Download

Get the latest installer from [Releases](https://github.com/RobertWasteLand/Gemini-Launcher/releases/latest). After that, the launcher updates itself.

## Releasing an update

1. Push.
2. On GitHub: Actions → GL_Release → Run workflow.

Players always see the version set in `GL_Version_Display` at the top of `src/GL_Main.js` (now `V1.0.0`). Each release also gets a hidden build number, counted up automatically, so launchers know there is something new.

## Patch notes

Edit `GL_Live/GL_Notes.txt` and push. Every launcher shows the new notes within a few minutes.

## Addons

Each addon is a folder of Java patches in `GL_Java/` (for example `GL_Java/GL_Core/`) plus one entry in `src-tauri/src/GL_Addons.rs`. They load through [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) by zed-0xff (MIT), and only when started from the launcher.

## Naming

Everything in the code is named `GL_<Name>_<Extra>`, for example `GL_Steam_Find`.
