package GL_Java.GL_Core;

import java.io.File;

import fmod.javafmod;
import zombie.Lua.LuaManager;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;

public final class GL_Menu {
    static final float GL_Menu_Height = 1359.0f;
    static final int GL_Frame_Left = 9;
    static final int GL_Frame_Right = 10;
    static final int GL_Frame_Top = 12;
    static final int GL_Frame_Bottom = 12;
    static final String GL_Menu_Theme = "GM_Main_Menu_Theme.ogg";
    static final long GL_Menu_Loop = 2L;
    static final long GL_Menu_Stream = 128L;
    static final long GL_Menu_Fade = 1_000_000_000L;
    static final String GL_Menu_Lua = "GL_Menu.lua";
    static final String[] GL_Menu_Pixel_Art = {"GM_Title", "GM_Menu_Button", "GM_Settings", "GM_Label_Multiplayer", "GM_Label_Play_Alone", "GM_Label_Continue", "GM_Label_Load", "GM_Label_Host", "GM_Label_Tutorial", "GM_Label_Credits", "GM_Label_Workshop", "GM_Label_Mods", "GM_Label_Quit"};

    private static boolean GL_Background_Off;
    private static Texture GL_Painting;
    private static Texture GL_Frame;
    private static boolean GL_Theme_Off;
    private static long GL_Theme_Sound;
    private static long GL_Theme_Channel;
    private static long GL_Theme_Fading;

    private GL_Menu() {
    }

    public static boolean GL_Background_Draw() {
        if (GL_Background_Off) {
            return false;
        }
        try {
            if (GL_Painting == null) {
                GL_Painting = GL_Picture("GM_MainMenu", GL_Intro_Art.GL_Art_Smooth);
                GL_Frame = GL_Picture("GM_MainMenu_Frame", GL_Intro_Art.GL_Art_Pixel);
            }
            if (GL_Painting.isFailure() || GL_Frame.isFailure()) {
                throw new IllegalStateException("a menu picture did not load");
            }
            if (!GL_Painting.isReady() || !GL_Frame.isReady()) {
                return true;
            }
            Core core = Core.getInstance();
            int w = core.getScreenWidth();
            int h = core.getScreenHeight();
            int f = Math.max(1, Math.round(h / GL_Menu_Height));
            int left = GL_Frame_Left * f;
            int right = GL_Frame_Right * f;
            int top = GL_Frame_Top * f;
            int bottom = GL_Frame_Bottom * f;
            float iw = w - left - right;
            float ih = h - top - bottom;
            float sw = GL_Painting.getWidth() - GL_Frame_Left - GL_Frame_Right;
            float sh = GL_Painting.getHeight() - GL_Frame_Top - GL_Frame_Bottom;
            float k = Math.max(iw / sw, ih / sh);
            float vw = iw / k;
            float vh = ih / k;
            GL_Intro_Draw.GL_Blend_Normal();
            GL_Part(GL_Painting, GL_Frame_Left + (sw - vw) / 2.0f, GL_Frame_Top + (sh - vh) / 2.0f, vw, vh, left, top, iw, ih);
            int fw = GL_Frame.getWidth();
            int fh = GL_Frame.getHeight();
            float cx = GL_Frame_Left + 0.5f;
            float cy = GL_Frame_Top + 0.5f;
            GL_Part(GL_Frame, 0, 0, GL_Frame_Left, GL_Frame_Top, 0, 0, left, top);
            GL_Part(GL_Frame, fw - GL_Frame_Right, 0, GL_Frame_Right, GL_Frame_Top, w - right, 0, right, top);
            GL_Part(GL_Frame, 0, fh - GL_Frame_Bottom, GL_Frame_Left, GL_Frame_Bottom, 0, h - bottom, left, bottom);
            GL_Part(GL_Frame, fw - GL_Frame_Right, fh - GL_Frame_Bottom, GL_Frame_Right, GL_Frame_Bottom, w - right, h - bottom, right, bottom);
            GL_Part(GL_Frame, cx, 0, 0, GL_Frame_Top, left, 0, iw, top);
            GL_Part(GL_Frame, cx, fh - GL_Frame_Bottom, 0, GL_Frame_Bottom, left, h - bottom, iw, bottom);
            GL_Part(GL_Frame, 0, cy, GL_Frame_Left, 0, 0, top, left, ih);
            GL_Part(GL_Frame, fw - GL_Frame_Right, cy, GL_Frame_Right, 0, w - right, top, right, ih);
            return true;
        } catch (IllegalStateException e) {
            GL_Background_Off = true;
            GL_Off(e.getMessage());
            return false;
        } catch (Throwable t) {
            GL_Background_Off = true;
            GL_Off("background failed (" + t + ")");
            return false;
        }
    }

    public static void GL_Lua_Run() {
        try {
            String path = GL_Intro_Art.GL_Art_Pictures + GL_Menu_Lua;
            File file = new File(path).getAbsoluteFile();
            if (!file.isFile()) {
                GL_Off(file.getName() + " is missing from " + file.getParent());
                return;
            }
            for (String name : GL_Menu_Pixel_Art) {
                Texture.getSharedTexture(GL_Intro_Art.GL_Art_Pictures + name + ".png", GL_Intro_Art.GL_Art_Pixel);
            }
            Object result = LuaManager.RunLua(path);
            if (Boolean.TRUE.equals(result)) {
                System.out.println("[GL_Java] GL_Core menu on");
            } else if (result instanceof String) {
                GL_Off((String) result);
            } else {
                GL_Off(GL_Menu_Lua + " stopped with an error, see the lines above");
            }
        } catch (Throwable t) {
            GL_Off("menu Lua failed (" + t + ")");
        }
    }

    public static void GL_Theme_Start() {
        if (GL_Theme_Off) {
            return;
        }
        try {
            if (Core.soundDisabled) {
                throw new IllegalStateException("the game's sound is disabled");
            }
            if (GL_Theme_Sound == 0L) {
                File file = new File(GL_Intro_Art.GL_Art_Sounds + GL_Menu_Theme).getAbsoluteFile();
                if (!file.isFile()) {
                    throw new IllegalStateException(file.getName() + " is missing from " + file.getParent());
                }
                GL_Theme_Sound = javafmod.FMOD_System_CreateSound(file.getAbsolutePath(), GL_Intro_Art.GL_Art_Sound_2D | GL_Menu_Loop | GL_Menu_Stream);
                if (GL_Theme_Sound == 0L) {
                    throw new IllegalStateException("FMOD would not open " + file.getName());
                }
            }
            GL_Theme_Halt();
            GL_Theme_Channel = GL_Intro_Art.GL_Art_Play(GL_Theme_Sound, Core.getInstance().getOptionMusicVolume());
        } catch (IllegalStateException e) {
            GL_Theme_Fail(e.getMessage());
        } catch (Throwable t) {
            GL_Theme_Fail("theme failed (" + t + ")");
        }
    }

    public static void GL_Theme_Stop() {
        if (GL_Theme_Channel != 0L && GL_Theme_Fading == 0L) {
            GL_Theme_Fading = System.nanoTime();
        }
    }

    public static void GL_Frame_End() {
        if (GL_Theme_Channel == 0L) {
            return;
        }
        try {
            float volume = Core.getInstance().getOptionMusicVolume() / 10.0f;
            if (GL_Theme_Fading != 0L) {
                float left = 1.0f - (float) (System.nanoTime() - GL_Theme_Fading) / GL_Menu_Fade;
                if (left <= 0.0f) {
                    GL_Theme_Halt();
                    return;
                }
                volume *= left;
            }
            javafmod.FMOD_Channel_SetVolume(GL_Theme_Channel, volume);
        } catch (Throwable t) {
            GL_Theme_Fail("theme failed (" + t + ")");
        }
    }

    public static boolean GL_Music_Hold() {
        return GL_Intro.GL_Music_Hold() || GL_Theme_Channel != 0L;
    }

    private static void GL_Theme_Halt() {
        if (GL_Theme_Channel != 0L) {
            javafmod.FMOD_Channel_Stop(GL_Theme_Channel);
            GL_Theme_Channel = 0L;
        }
        GL_Theme_Fading = 0L;
    }

    private static void GL_Theme_Fail(String why) {
        GL_Theme_Off = true;
        try {
            GL_Theme_Halt();
        } catch (Throwable ignored) {
            GL_Theme_Channel = 0L;
            GL_Theme_Fading = 0L;
        }
        GL_Off(why);
    }

    private static Texture GL_Picture(String name, int flags) {
        String path = GL_Intro_Art.GL_Art_Pictures + name + ".png";
        File file = new File(path).getAbsoluteFile();
        if (!file.isFile()) {
            throw new IllegalStateException(file.getName() + " is missing from " + file.getParent());
        }
        Texture texture = Texture.getSharedTexture(path, flags);
        if (texture == null) {
            throw new IllegalStateException("the game would not load " + file.getName());
        }
        return texture;
    }

    private static void GL_Part(Texture texture, float sx, float sy, float sw, float sh, float x, float y, float w, float h) {
        float us = texture.getXStart();
        float uw = (texture.getXEnd() - us) / texture.getWidth();
        float vs = texture.getYStart();
        float vh = (texture.getYEnd() - vs) / texture.getHeight();
        float u0 = us + uw * sx;
        float u1 = us + uw * (sx + sw);
        float v0 = vs + vh * sy;
        float v1 = vs + vh * (sy + sh);
        SpriteRenderer.instance.render(texture, x, y, w, h, 1.0f, 1.0f, 1.0f, 1.0f, u0, v0, u1, v0, u1, v1, u0, v1);
    }

    private static void GL_Off(String why) {
        System.out.println("[GL_Java] GL_Core menu off: " + why);
    }
}
