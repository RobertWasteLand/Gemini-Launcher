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

Addons that belong to the mod pack use the pack's `GM_` prefix (`GL_Java/GM_Environment/` is the water and fog). An addon's own files sit in its `media` folder, in a folder named after the addon: `GL_Java/GM_Environment/media/shaders/GM_Environment/` holds the shaders, `GL_Java/GL_Core/media/ui/GL_Core/` and `GL_Java/GL_Core/media/sound/GL_Core/` the pictures, sounds and Lua of the intro and the main menu. On every Play the launcher places them in the same folders of the game's `media`, next to its jars in `GL_Java/`, and the uninstall removes them again. `GL_Java/GL_Stubs/` holds empty copies of the game classes the addons call, so they compile without the game installed; it is never shipped.

The main menu's labels are drawn with the Alagard font by Pix3M ([OpenGameArt](https://opengameart.org/content/pixel-fonts-by-pix3m), CC-BY 3.0).

## Naming

Everything in the code is named `GL_<Name>_<Extra>`, for example `GL_Steam_Find`.
