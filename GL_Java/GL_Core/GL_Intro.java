package GL_Java.GL_Core;

import fmod.javafmod;
import zombie.GameWindow;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.gameStates.GameStateMachine;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;

public final class GL_Intro {
    static final float GL_Slide_Fade = 1.0f;
    static final float GL_Slide_Hold = 2.0f;
    static final float GL_Slide_Gap = 0.25f;
    static final float GL_Warning_Least = 4.0f;
    static final long GL_Warning_Wait = 3_000_000_000L;
    static final float GL_Frame_Most = 0.1f;
    static final int GL_Catch_Up = 4;
    static final int GL_Wipe_Bands = 8;
    static final int GL_Close_Hold = 2;
    static final long GL_Wipe_Delay = 350_000_000L;
    static final long GL_Menu_Wait = 15_000_000_000L;
    static final int GL_Time_Ms = 1;
    static final int[] GL_Skip_Keys = {28, 57, 1};

    static final int GL_Phase_Warning = 0;
    static final int GL_Phase_Logos = 1;
    static final int GL_Phase_Buddy = 2;
    static final int GL_Phase_Gemini = 3;
    static final int GL_Phase_Knight = 4;
    static final int GL_Phase_Close = 5;
    static final int GL_Phase_Done = 6;

    static final int GL_Cover_None = 0;
    static final int GL_Cover_Black = 1;
    static final int GL_Cover_Wipe = 2;

    private static boolean GL_Failed;
    private static boolean GL_Loaded;
    private static GL_Intro_Art GL_Art;
    private static long GL_Warning_Shown;
    private static boolean GL_Running;
    private static boolean GL_Credited;
    private static boolean GL_Menu_Up;
    private static int GL_Phase = -1;
    private static float GL_Phase_Time;
    private static float GL_Hold;
    private static float GL_Gemini_Time;
    private static long GL_Last;
    private static float GL_Tick_Left;
    private static int GL_Close_Tick;
    private static int GL_Close_Under = -1;
    private static float GL_Close_Time;
    private static boolean GL_Skip_Armed;
    private static long GL_Jingle;
    private static GL_Intro_Knight GL_Knight;
    private static int GL_Cover = GL_Cover_None;
    private static long GL_Wipe_Start;
    private static long GL_Done_At;

    private GL_Intro() {
    }

    public static boolean GL_Warning_Draw() {
        if (GL_Failed) {
            return false;
        }
        try {
            GL_Load();
            if (!GL_Wait(GL_Art.warning)) {
                System.out.println("[GL_Java] GL_Core intro: the warning picture was not ready in time, the game's own warning shows");
                return false;
            }
            GL_Intro_Draw.GL_Frame_Begin();
            try {
                GL_Intro_Draw.GL_Slide(GL_Art.warning, 1.0f);
            } finally {
                GL_Intro_Draw.GL_Frame_Finish();
            }
            GL_Warning_Shown = System.nanoTime();
            return true;
        } catch (IllegalStateException e) {
            GL_Off(e.getMessage());
            return false;
        } catch (Throwable t) {
            GL_Fail("warning", t);
            return false;
        }
    }

    public static GameStateMachine.StateAction GL_Logo_Update(GameStateMachine.StateAction result) {
        if (GL_Failed || GL_Phase == GL_Phase_Done) {
            return result;
        }
        try {
            long now = System.nanoTime();
            if (!GL_Running) {
                GL_Load();
                GL_Running = true;
                GL_Last = now;
                GL_Phase_Start(GL_Warning_Shown != 0L ? GL_Phase_Warning : GL_Phase_Logos);
            }
            float dt = Math.min(GL_Frame_Most, (now - GL_Last) / 1.0e9f);
            GL_Last = now;
            GL_Skip_Check(now);
            GL_Advance(dt);
            return GL_Phase == GL_Phase_Done ? GameStateMachine.StateAction.Continue : GameStateMachine.StateAction.Remain;
        } catch (IllegalStateException e) {
            GL_Off(e.getMessage());
            return result;
        } catch (Throwable t) {
            GL_Fail("logo screen", t);
            return result;
        }
    }

    public static boolean GL_Logo_Render() {
        if (GL_Failed || GL_Phase == GL_Phase_Done || GL_Phase < 0 && GL_Warning_Shown == 0L) {
            return false;
        }
        try {
            GL_Intro_Draw.GL_Frame_Begin();
            try {
                GL_Draw();
            } finally {
                GL_Intro_Draw.GL_Frame_Finish();
            }
            return true;
        } catch (Throwable t) {
            GL_Fail("logo screen drawing", t);
            return false;
        }
    }

    public static void GL_Menu_Enter() {
        GL_Menu_Up = true;
        if (GL_Cover == GL_Cover_Black) {
            GL_Cover = GL_Cover_Wipe;
            GL_Wipe_Start = 0L;
        }
    }

    public static void GL_Frame_End() {
        if (GL_Warning_Waiting()) {
            try {
                GL_Intro_Draw.GL_Measure();
                GL_Intro_Draw.GL_Blend_Normal();
                GL_Intro_Draw.GL_Fill(0.0f, 0.0f, 0.0f);
                GL_Intro_Draw.GL_Slide(GL_Art.warning, 1.0f);
            } catch (Throwable t) {
                GL_Fail("warning while loading", t);
            }
            return;
        }
        if (GL_Cover == GL_Cover_None) {
            return;
        }
        try {
            long now = System.nanoTime();
            if (GL_Cover == GL_Cover_Black && now - GL_Done_At > GL_Menu_Wait) {
                GL_Menu_Up = true;
                GL_Cover = GL_Cover_None;
                return;
            }
            int covered = GL_Wipe_Bands;
            if (GL_Cover == GL_Cover_Wipe) {
                if (GL_Wipe_Start == 0L) {
                    GL_Wipe_Start = now + GL_Wipe_Delay;
                }
                covered = GL_Wipe_Bands - (int) (Math.max(0L, now - GL_Wipe_Start) * GL_Intro_Knight.GL_Knight_Rate / 1_000_000_000L);
                if (covered <= 0) {
                    GL_Cover = GL_Cover_None;
                    return;
                }
            }
            GL_Intro_Draw.GL_Cover_Bands(GL_Wipe_Bands - covered, GL_Wipe_Bands, GL_Wipe_Bands);
        } catch (Throwable t) {
            GL_Cover = GL_Cover_None;
            GL_Fail("menu wipe", t);
        }
    }

    private static boolean GL_Warning_Waiting() {
        return GL_Warning_Shown != 0L && !GL_Failed && !GL_Menu_Up && GL_Phase < 0 && GameWindow.states != null && GameWindow.states.current == null;
    }

    public static boolean GL_Watermark_Hold() {
        return GL_Music_Hold() || !GL_Failed && GL_Cover != GL_Cover_None;
    }

    public static boolean GL_Watermark_Credited() {
        return GL_Credited && !GL_Failed;
    }

    public static boolean GL_Music_Hold() {
        return GL_Loaded && !GL_Failed && !GL_Menu_Up;
    }

    private static void GL_Load() {
        if (GL_Loaded) {
            return;
        }
        GL_Loaded = true;
        GL_Art = GL_Intro_Art.GL_Art_Load();
        System.out.println("[GL_Java] GL_Core intro: files from " + GL_Art.folder + (GL_Art.GL_Art_Sound_On() ? "" : ", sound off"));
    }

    private static boolean GL_Wait(Texture texture) throws InterruptedException {
        long start = System.nanoTime();
        while (!texture.isReady() && !texture.isFailure() && System.nanoTime() - start < GL_Warning_Wait) {
            GameWindow.fileSystem.updateAsyncTransactions();
            Thread.sleep(10L);
        }
        return texture.isReady();
    }

    private static void GL_Phase_Start(int phase) {
        GL_Phase = phase;
        GL_Phase_Time = 0.0f;
        GL_Tick_Left = 0.0f;
        if (phase == GL_Phase_Warning) {
            GL_Hold = Math.max(0.0f, GL_Warning_Least - (System.nanoTime() - GL_Warning_Shown) / 1.0e9f);
        } else if (phase == GL_Phase_Gemini) {
            GL_Gemini_Time = 0.0f;
            GL_Jingle = GL_Intro_Art.GL_Art_Play(GL_Art.jingle, Core.getInstance().getOptionMusicVolume());
        } else if (phase == GL_Phase_Knight) {
            GL_Jingle_Stop();
            GL_Knight = new GL_Intro_Knight(GL_Art);
        } else if (phase == GL_Phase_Close) {
            GL_Jingle_Stop();
            GL_Close_Tick = 0;
            GL_Intro_Art.GL_Art_Effect(GL_Art.flash);
        } else if (phase == GL_Phase_Done) {
            GL_Jingle_Stop();
            GL_Running = false;
            GL_Cover = GL_Cover_Black;
            GL_Done_At = System.nanoTime();
            GL_Knight = null;
        }
    }

    private static void GL_Advance(float dt) {
        if (GL_Phase == GL_Phase_Warning) {
            GL_Phase_Time += dt;
            if (GL_Phase_Time >= GL_Hold + GL_Slide_Fade) {
                GL_Phase_Start(GL_Phase_Logos);
            }
        } else if (GL_Phase == GL_Phase_Logos || GL_Phase == GL_Phase_Buddy) {
            if (GL_Art.GL_Art_Ready()) {
                GL_Phase_Time += dt;
            }
            if (GL_Phase == GL_Phase_Buddy && GL_Phase_Time >= GL_Slide_Fade) {
                GL_Credited = true;
            }
            if (GL_Phase_Time >= GL_Slide_Fade * 2.0f + GL_Slide_Hold + GL_Slide_Gap) {
                GL_Phase_Start(GL_Phase == GL_Phase_Logos ? GL_Phase_Buddy : GL_Phase_Gemini);
            }
        } else if (GL_Phase == GL_Phase_Gemini) {
            GL_Phase_Time += dt;
            GL_Gemini_Time = GL_Jingle_Time(GL_Phase_Time);
            if (GL_Gemini_Time >= GL_Intro_Logo.GL_Logo_End) {
                GL_Phase_Start(GL_Phase_Knight);
            }
        } else if (GL_Phase == GL_Phase_Knight) {
            if (GL_Knight.GL_Knight_Update(dt)) {
                GL_Close(GL_Phase_Knight);
            }
        } else if (GL_Phase == GL_Phase_Close) {
            if (GL_Close_Under == GL_Phase_Knight) {
                GL_Knight.GL_Knight_Update(dt);
            }
            GL_Tick_Left += dt;
            for (int n = 0; GL_Tick_Left >= GL_Tick() && n < GL_Catch_Up; n++) {
                GL_Tick_Left -= GL_Tick();
                GL_Close_Tick++;
                if (GL_Close_Tick >= GL_Wipe_Bands + GL_Close_Hold) {
                    GL_Phase_Start(GL_Phase_Done);
                    return;
                }
            }
            GL_Tick_Left = Math.min(GL_Tick_Left, GL_Tick());
        }
    }

    private static void GL_Close(int under) {
        GL_Close_Under = under;
        GL_Close_Time = GL_Phase_Time;
        GL_Phase_Start(GL_Phase_Close);
    }

    private static void GL_Draw() {
        if (GL_Phase == GL_Phase_Close) {
            GL_Draw_Scene(GL_Close_Under, GL_Close_Time);
            GL_Intro_Draw.GL_Cover_Bands(0, Math.min(GL_Wipe_Bands, GL_Close_Tick + 1), GL_Wipe_Bands);
        } else {
            GL_Draw_Scene(GL_Phase, GL_Phase_Time);
        }
    }

    private static void GL_Draw_Scene(int phase, float time) {
        if (phase < 0) {
            GL_Intro_Draw.GL_Slide(GL_Art.warning, 1.0f);
        } else if (phase == GL_Phase_Warning) {
            float alpha = time <= GL_Hold ? 1.0f : 1.0f - (time - GL_Hold) / GL_Slide_Fade;
            GL_Intro_Draw.GL_Slide(GL_Art.warning, alpha);
        } else if (phase == GL_Phase_Logos) {
            GL_Intro_Draw.GL_Slide(GL_Art.logos, GL_Slide_Alpha(time));
        } else if (phase == GL_Phase_Buddy) {
            GL_Intro_Draw.GL_Slide(GL_Art.buddy, GL_Slide_Alpha(time));
        } else if (phase == GL_Phase_Gemini) {
            GL_Intro_Logo.GL_Logo_Draw(GL_Art, GL_Gemini_Time);
        } else if (phase == GL_Phase_Knight) {
            GL_Knight.GL_Knight_Draw();
        }
    }

    private static float GL_Slide_Alpha(float t) {
        if (t < GL_Slide_Fade) {
            return t / GL_Slide_Fade;
        }
        if (t < GL_Slide_Fade + GL_Slide_Hold) {
            return 1.0f;
        }
        return 1.0f - (t - GL_Slide_Fade - GL_Slide_Hold) / GL_Slide_Fade;
    }

    private static float GL_Tick() {
        return 1.0f / GL_Intro_Knight.GL_Knight_Rate;
    }

    private static float GL_Jingle_Time(float wall) {
        if (GL_Jingle != 0L && javafmod.FMOD_Channel_IsPlaying(GL_Jingle)) {
            long ms = javafmod.FMOD_Channel_GetPosition(GL_Jingle, GL_Time_Ms);
            if (ms > 0L && ms < 60_000L) {
                return ms / 1000.0f;
            }
        }
        return wall;
    }

    private static void GL_Jingle_Stop() {
        if (GL_Jingle != 0L) {
            javafmod.FMOD_Channel_Stop(GL_Jingle);
            GL_Jingle = 0L;
        }
    }

    private static void GL_Skip_Check(long now) {
        boolean pressed = Mouse.isLeftDown();
        for (int key : GL_Skip_Keys) {
            pressed |= GameKeyboard.isKeyDown(key);
        }
        if (!pressed) {
            GL_Skip_Armed = true;
            return;
        }
        if (!GL_Skip_Armed || GL_Phase >= GL_Phase_Close) {
            return;
        }
        if (GL_Phase == GL_Phase_Warning && (now - GL_Warning_Shown) / 1.0e9f < GL_Warning_Least) {
            return;
        }
        GL_Skip_Armed = false;
        GL_Close(GL_Phase);
    }

    private static void GL_Off(String why) {
        if (GL_Failed) {
            return;
        }
        GL_Failed = true;
        GL_Running = false;
        GL_Cover = GL_Cover_None;
        try {
            GL_Jingle_Stop();
        } catch (Throwable ignored) {
            GL_Jingle = 0L;
        }
        System.out.println("[GL_Java] GL_Core intro off: " + why + "; the game's own intro plays");
    }

    private static void GL_Fail(String what, Throwable t) {
        GL_Off(what + " failed (" + t + ")");
    }
}
