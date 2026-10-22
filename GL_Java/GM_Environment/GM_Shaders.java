package GL_Java.GM_Environment;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import zombie.GameTime;
import zombie.ZomboidFileSystem;
import zombie.characters.IsoPlayer;
import zombie.core.Color;
import zombie.core.Core;
import zombie.core.SceneShaderStore;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderThread;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoDepthHelper;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoWorld;
import zombie.iso.weather.ClimateManager;
import zombie.iso.weather.WeatherShader;
import zombie.network.GameClient;

final class GM_Shaders {
    static final String GM_Name = "GM_Environment/GM_Shaders_Screen";
    static final String GM_Check_File = "media/shaders/GM_Environment/GM_Shaders_Screen.frag";
    static final int GM_Ring_Size = 8;
    static final int GM_Players = 4;
    static final int GM_Var_Seq = 20;
    static final float GM_Delta = 0.023093667f / 16.0f;
    static final float GM_Fog_Height = 0.6f;
    static final float GM_Fog_Window = 128.0f;
    static final float GM_Fog_Snap = 4.0f;
    static final float GM_Indoor_Top = 0.85f;
    static final float GM_Roof_In = 5.0f;
    static final float GM_Roof_Out = 1.2f;
    static final int GM_Mover_Count = 32;
    static final int GM_Track_Count = 1024;
    static final float GM_Mover_Reach = 48.0f;
    static final float GM_Mover_Speed = 2.5f;
    static final float GM_Mover_Radius = 1.1f;
    static final double GM_Fog_Seconds = 150.0;
    static final double GM_Fog_Wrap = 65536.0;
    static final float GM_Wind_Base = 0.12f;
    static final float GM_Wind_Gain = 0.7f;
    static final float GM_Speed_Cap = 8.0f;
    static final int GM_Unit_Depth = 1;
    static final int GM_Unit_Field = 4;
    static final int GM_Unit_Bank = 5;
    static final int GM_Unit_FogTex = 6;
    static final int GM_Fx_Slots = 10;
    static final int GM_Fx_Fog = 0;
    static final int GM_Fx_Wake = 2;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;

    static final class GM_Frame {
        int seq;
        int player;
        float offX;
        float offY;
        float zoom;
        float tileScale;
        float texLeft;
        float texBottom;
        int usedX;
        int usedY;
        int usedW;
        int usedH;
        float depthRef;
        float sumRef;
        float sunAmount;
        final float[] sunColour = new float[3];
        final float[] fogColour = new float[3];
        float fogAmount;
        float indoors;
        float wake;
        float time;
        float fogTime;
        int moverCount;
        final float[] movers = new float[GM_Mover_Count * 4];
        float dt;
        float windVX;
        float windVY;
        float driftX;
        float driftY;
        float playerX;
        float playerY;
        float playerVX;
        float playerVY;
        float fogX;
        float fogY;
        boolean bypass;
        boolean depth;
        int debug;
        final float[] fx = new float[GM_Fx_Slots];
    }

    static final class GM_Locs {
        int program = -1;
        int depth = -1;
        int field = -1;
        int bank = -1;
        int indoors = -1;
        int fogTex = -1;
        int view = -1;
        int tex = -1;
        int ref = -1;
        int rect = -1;
        int on = -1;
        int fogRect = -1;
        int fog = -1;
        int wind = -1;
        int time = -1;
        int player = -1;
        int wake = -1;
        int fogTime = -1;
        int movers = -1;
        int moverCount = -1;

        void GM_Find(int id) {
            program = id;
            depth = GL20.glGetUniformLocation(id, "GM_Depth");
            field = GL20.glGetUniformLocation(id, "GM_Field");
            bank = GL20.glGetUniformLocation(id, "GM_Bank");
            indoors = GL20.glGetUniformLocation(id, "GM_Indoors");
            fogTex = GL20.glGetUniformLocation(id, "GM_FogTex");
            view = GL20.glGetUniformLocation(id, "GM_View");
            tex = GL20.glGetUniformLocation(id, "GM_Tex");
            ref = GL20.glGetUniformLocation(id, "GM_Ref");
            rect = GL20.glGetUniformLocation(id, "GM_FieldRect");
            on = GL20.glGetUniformLocation(id, "GM_FieldOn");
            fogRect = GL20.glGetUniformLocation(id, "GM_FogRect");
            fog = GL20.glGetUniformLocation(id, "GM_Fog");
            wind = GL20.glGetUniformLocation(id, "GM_Wind");
            time = GL20.glGetUniformLocation(id, "GM_Time");
            player = GL20.glGetUniformLocation(id, "GM_Player");
            wake = GL20.glGetUniformLocation(id, "GM_Wake");
            fogTime = GL20.glGetUniformLocation(id, "GM_FogTime");
            movers = GL20.glGetUniformLocation(id, "GM_Movers");
            moverCount = GL20.glGetUniformLocation(id, "GM_MoverCount");
        }
    }

    private static final class GM_Motion {
        float lastX;
        float lastY;
        float vx;
        float vy;
        boolean valid;
    }

    private static volatile boolean GM_Failed;
    private static volatile boolean GM_Missing;
    private static volatile boolean GM_Installing;
    static volatile Object GM_Screen;
    private static Object GM_Vanilla;
    private static int GM_Seq;
    private static final GM_Frame[][] GM_Ring = new GM_Frame[GM_Ring_Size][GM_Players];
    private static final GM_Pass[] GM_Passes = new GM_Pass[GM_Ring_Size];
    private static boolean GM_Shared;
    private static final float[] GM_Sun_Day = {1.0f, 0.97f, 0.88f};
    private static final float[] GM_Sun_Low = {1.0f, 0.74f, 0.52f};
    private static final float[] GM_Sun_Moon = {0.55f, 0.65f, 0.85f};
    private static final GM_Motion[] GM_Motions = new GM_Motion[GM_Players];
    private static final float[] GM_Under_Roof = new float[GM_Players];
    private static final Object[] GM_Mover_Refs = new Object[GM_Track_Count];
    private static final float[] GM_Mover_Last = new float[GM_Track_Count * 2];
    private static final Object[] GM_Mover_Next = new Object[GM_Track_Count];
    private static final float[] GM_Mover_Pos = new float[GM_Track_Count * 2];
    private static int GM_Mover_Seen;
    private static int GM_Mover_Shift;
    private static long GM_Last_Nanos;
    private static float GM_Clock;
    private static double GM_Drift_X;
    private static double GM_Drift_Y;
    private static final GM_Locs GM_Screen_Locs = new GM_Locs();
    private static int GM_Loc_Sun = -1;
    private static int GM_Loc_SunCol = -1;
    private static int GM_Loc_FogCol = -1;
    private static int GM_Loc_Fx = -1;
    private static int GM_Loc_Bypass = -1;
    private static int GM_Loc_Debug = -1;

    private GM_Shaders() {
    }

    static boolean GM_Off() {
        return GM_Failed || GM_Missing;
    }

    static boolean GM_Active() {
        return !GM_Failed && GM_Screen != null && SceneShaderStore.weatherShader == GM_Screen;
    }

    static void GM_Fail(String what, Throwable t) {
        if (GM_Failed) {
            return;
        }
        GM_Failed = true;
        System.out.println("[GL_Java] GM_Environment shaders off: " + what + " (" + t + ")");
        if (GM_Vanilla != null && SceneShaderStore.weatherShader == GM_Screen) {
            SceneShaderStore.weatherShader = (zombie.core.opengl.Shader) GM_Vanilla;
        }
    }

    static void GM_World_Reset() {
        GM_Missing = false;
        for (int i = 0; i < GM_Players; i++) {
            GM_Motions[i] = null;
        }
        GM_Movers_Keep(0);
        GM_Pass.GM_Fog_Reset();
    }

    static void GM_Check() {
        if (GM_Failed || GM_Missing || GM_Installing) {
            return;
        }
        zombie.core.opengl.Shader current = SceneShaderStore.weatherShader;
        if (current == null) {
            return;
        }
        if (GM_Screen != null) {
            if (!((zombie.core.opengl.Shader) GM_Screen).isCompiled()) {
                GM_Fail("the screen shader stopped compiling", new IllegalStateException(GM_Name));
                return;
            }
            boolean shared = GM_Shared_Screen();
            if (shared != GM_Shared) {
                GM_Shared = shared;
                System.out.println("[GL_Java] GM_Environment screen effects " + (shared ? "paused: more than one player shares the screen" : "back on: one player on the screen"));
            }
            if (GM_Settings.GM_Get().screen && !shared) {
                if (current != GM_Screen) {
                    GM_Vanilla = current;
                    SceneShaderStore.weatherShader = (zombie.core.opengl.Shader) GM_Screen;
                }
            } else if (current == GM_Screen && GM_Vanilla != null) {
                SceneShaderStore.weatherShader = (zombie.core.opengl.Shader) GM_Vanilla;
            }
            return;
        }
        if (!ZomboidFileSystem.instance.isKnownFile(GM_Check_File)) {
            GM_Missing = true;
            System.out.println("[GL_Java] GM_Environment shaders off for this world: the GM_Environment mod is not enabled, so its screen shaders are not loaded");
            return;
        }
        GM_Vanilla = current;
        GM_Installing = true;
        RenderThread.invokeOnRenderContext(GM_Shaders::GM_Install);
    }

    private static boolean GM_Shared_Screen() {
        if (GM_Settings.GM_Test("split")) {
            return true;
        }
        IsoPlayer[] players = IsoPlayer.players;
        if (players == null) {
            return false;
        }
        int local = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                local++;
            }
        }
        return local > 1;
    }

    private static void GM_Install() {
        try {
            WeatherShader shader = new WeatherShader(GM_Name);
            boolean logging = DebugLog.isEnabled(DebugType.Shader);
            if (!logging) {
                DebugLog.setLogEnabled(DebugType.Shader, true);
            }
            boolean passes;
            int fx = -1;
            try {
                shader.Start();
                int program = GL11.glGetInteger(0x8B8D);
                if (program != 0) {
                    fx = GL20.glGetUniformLocation(program, "GM_Fx");
                }
                shader.End();
                passes = GM_Pass.GM_Create();
            } finally {
                if (!logging) {
                    DebugLog.setLogEnabled(DebugType.Shader, false);
                }
            }
            if (!shader.isCompiled()) {
                GM_Fail("screen shader did not compile, keeping the game's screen pass", new IllegalStateException(GM_Name));
                return;
            }
            if (!passes) {
                GM_Fail("render passes did not compile, keeping the game's screen pass", new IllegalStateException(GM_Name));
                return;
            }
            if (fx < 0) {
                GM_Fail("the pack's screen shader is older than this launcher, so the screen stays vanilla until the pack updates", new IllegalStateException(GM_Name));
                return;
            }
            GM_Screen = shader;
            if (GM_Settings.GM_Get().screen && !GM_Shared_Screen()) {
                SceneShaderStore.weatherShader = shader;
            }
            System.out.println("[GL_Java] GM_Environment shaders on");
        } catch (Throwable t) {
            GM_Fail("screen shader install failed on the render thread", t);
        } finally {
            GM_Installing = false;
        }
    }

    static void GM_Queue() {
        if (!GM_Active()) {
            return;
        }
        long now = System.nanoTime();
        float dt = GM_Last_Nanos == 0 ? 0.016f : Math.min(0.1f, (now - GM_Last_Nanos) / 1.0e9f);
        GM_Last_Nanos = now;
        if (GameTime.isGamePaused()) {
            dt = 0.0f;
        }
        GM_Clock += dt;
        if (GM_Clock > 100000.0f) {
            GM_Clock -= 100000.0f;
        }
        ClimateManager climate = ClimateManager.getInstance();
        float windAngle = climate.getWindAngleRadians();
        float windStrength = GM_Clamp(climate.getWindIntensity(), 0.0f, 1.0f);
        float windSpeed = GM_Wind_Base + GM_Wind_Gain * windStrength;
        float windVX = (float) Math.cos(windAngle) * windSpeed;
        float windVY = (float) Math.sin(windAngle) * windSpeed;
        GM_Drift_X += windVX * (double) dt;
        GM_Drift_Y += windVY * (double) dt;
        if (Math.abs(GM_Drift_X) > 1.0e6 || Math.abs(GM_Drift_Y) > 1.0e6) {
            GM_Drift_X = 0.0;
            GM_Drift_Y = 0.0;
            GM_Pass.GM_Fog_Reset();
        }
        GM_Seq++;
        int slot = GM_Seq % GM_Ring_Size;
        int players = Math.max(1, Math.min(GM_Players, IsoPlayer.numPlayers));
        GM_Settings settings = GM_Settings.GM_Get();
        for (int p = 0; p < players; p++) {
            GM_Frame frame = GM_Ring[slot][p];
            if (frame == null) {
                frame = new GM_Frame();
                GM_Ring[slot][p] = frame;
            }
            frame.dt = dt;
            frame.windVX = windVX;
            frame.windVY = windVY;
            frame.driftX = (float) GM_Drift_X;
            frame.driftY = (float) GM_Drift_Y;
            frame.bypass = settings.bypass;
            frame.depth = settings.depth;
            frame.debug = settings.debug;
            frame.fx[GM_Fx_Fog] = settings.GM_Value(GM_Features.GM_Fog, 1);
            frame.fx[GM_Fx_Wake] = settings.GM_Value(GM_Features.GM_Fog_Wake, 1);
            GM_Frame_Fill(frame, p, dt, climate);
        }
        GM_Pass pass = GM_Passes[slot];
        if (pass == null) {
            pass = new GM_Pass();
            GM_Passes[slot] = pass;
        }
        SpriteRenderer.instance.drawGeneric(pass.GM_Use(GM_Ring[slot][0]));
    }

    private static void GM_Frame_Fill(GM_Frame frame, int p, float dt, ClimateManager climate) {
        frame.seq = GM_Seq;
        frame.player = p;
        frame.offX = IsoCamera.getOffX(p);
        frame.offY = IsoCamera.getOffY(p);
        frame.zoom = Core.getInstance().getZoom(p);
        frame.tileScale = Core.tileScale;
        frame.texLeft = IsoCamera.getOffscreenLeft(p);
        frame.texBottom = IsoCamera.getOffscreenTop(p) + IsoCamera.getScreenHeight(p);
        frame.usedX = IsoCamera.getOffscreenLeft(p);
        frame.usedY = IsoCamera.getOffscreenTop(p);
        frame.usedW = IsoCamera.getScreenWidth(p);
        frame.usedH = IsoCamera.getScreenHeight(p);
        frame.time = GM_Clock;
        IsoPlayer player = IsoPlayer.players[p];
        float camX = IsoCamera.frameState.camCharacterX;
        float camY = IsoCamera.frameState.camCharacterY;
        float camZ = IsoCamera.frameState.camCharacterZ;
        if (player != null) {
            camX = player.getX();
            camY = player.getY();
            camZ = player.getZ();
        }
        int level = (int) Math.floor(camZ);
        frame.depthRef = IsoDepthHelper.getSquareDepthData((int) camX, (int) camY, camX, camY, level).depthStart;
        frame.sumRef = camX + camY;
        frame.fogX = (float) Math.floor((camX - frame.driftX - GM_Fog_Window * 0.5f) * GM_Fog_Snap) / GM_Fog_Snap;
        frame.fogY = (float) Math.floor((camY - frame.driftY - GM_Fog_Window * 0.5f) * GM_Fog_Snap) / GM_Fog_Snap;
        GM_Env_Fill(frame, climate);
        GM_Motion_Fill(frame, p, player, camX, camY, dt);
        IsoGridSquare here = player == null ? null : player.getCurrentSquare();
        float under = here != null && !here.isOutside() ? 1.0f : 0.0f;
        float rate = under > GM_Under_Roof[p] ? GM_Roof_In : GM_Roof_Out;
        GM_Under_Roof[p] += (under - GM_Under_Roof[p]) * GM_Clamp(dt * rate, 0.0f, 1.0f);
        frame.indoors = GM_Under_Roof[p] > 0.01f ? (Math.max(0, level) + GM_Indoor_Top) * GM_Under_Roof[p] : 0.0f;
        frame.wake = frame.fx[GM_Fx_Wake] * (1.0f - GM_Under_Roof[p]);
        frame.fogTime = (float) ((GameTime.getInstance().getWorldAgeHours() * GM_Fog_Seconds) % GM_Fog_Wrap);
        GM_Movers_Fill(frame, p, player, camX, camY, dt);
    }

    private static void GM_Movers_Fill(GM_Frame frame, int p, IsoPlayer player, float camX, float camY, float dt) {
        frame.moverCount = 0;
        if (p != 0 || frame.fx[GM_Fx_Wake] < 0.5f || frame.fogAmount <= 0.001f) {
            if (GM_Mover_Seen > 0) {
                GM_Movers_Keep(0);
            }
            return;
        }
        if (player != null && frame.wake > 0.0f) {
            float speed = (float) Math.sqrt(frame.playerVX * frame.playerVX + frame.playerVY * frame.playerVY);
            GM_Mover_Put(frame, frame.playerX, frame.playerY, speed);
        }
        int next = 0;
        IsoWorld world = IsoWorld.instance;
        IsoCell cell = world == null ? null : world.currentCell;
        if (cell != null) {
            if (GameClient.client) {
                java.util.ArrayList<IsoPlayer> others = GameClient.instance.getPlayers();
                for (int i = 0; others != null && i < others.size() && next < GM_Track_Count; i++) {
                    IsoPlayer other = others.get(i);
                    if (other != null && !other.isLocalPlayer()) {
                        next = GM_Mover_Track(frame, other, next, camX, camY, dt);
                    }
                }
            }
            java.util.ArrayList<zombie.characters.IsoZombie> zombies = cell.getZombieList();
            for (int i = 0; zombies != null && i < zombies.size() && next < GM_Track_Count; i++) {
                next = GM_Mover_Track(frame, zombies.get(i), next, camX, camY, dt);
            }
        }
        GM_Movers_Keep(next);
    }

    private static void GM_Movers_Keep(int next) {
        int top = Math.max(next, GM_Mover_Seen);
        for (int i = 0; i < top; i++) {
            GM_Mover_Refs[i] = i < next ? GM_Mover_Next[i] : null;
            GM_Mover_Next[i] = null;
            GM_Mover_Last[i * 2] = GM_Mover_Pos[i * 2];
            GM_Mover_Last[i * 2 + 1] = GM_Mover_Pos[i * 2 + 1];
        }
        GM_Mover_Seen = next;
        GM_Mover_Shift = 0;
    }

    private static int GM_Mover_Track(GM_Frame frame, IsoMovingObject mover, int next, float camX, float camY, float dt) {
        if (mover == null) {
            return next;
        }
        float x = mover.getX();
        float y = mover.getY();
        if (Math.abs(x - camX) > GM_Mover_Reach || Math.abs(y - camY) > GM_Mover_Reach || mover.getZ() >= 1.0f) {
            return next;
        }
        IsoGridSquare square = mover.getCurrentSquare();
        if (square == null || !square.isOutside()) {
            return next;
        }
        int hint = next + GM_Mover_Shift;
        int at = hint >= 0 && hint < GM_Mover_Seen && GM_Mover_Refs[hint] == mover ? hint : -1;
        for (int i = 0; at < 0 && i < GM_Mover_Seen; i++) {
            if (GM_Mover_Refs[i] == mover) {
                at = i;
                GM_Mover_Shift = i - next;
            }
        }
        float speed = 0.0f;
        if (at >= 0 && dt > 0.0f) {
            float dx = x - GM_Mover_Last[at * 2];
            float dy = y - GM_Mover_Last[at * 2 + 1];
            speed = (float) Math.sqrt(dx * dx + dy * dy) / dt;
            if (speed > GM_Speed_Cap) {
                speed = 0.0f;
            }
        }
        GM_Mover_Next[next] = mover;
        GM_Mover_Pos[next * 2] = x;
        GM_Mover_Pos[next * 2 + 1] = y;
        GM_Mover_Put(frame, x, y, speed);
        return next + 1;
    }

    private static void GM_Mover_Put(GM_Frame frame, float x, float y, float speed) {
        float strength = GM_Clamp(speed / GM_Mover_Speed, 0.0f, 1.0f);
        if (strength < 0.08f || frame.moverCount >= GM_Mover_Count) {
            return;
        }
        int at = frame.moverCount * 4;
        frame.movers[at] = x;
        frame.movers[at + 1] = y;
        frame.movers[at + 2] = strength;
        frame.movers[at + 3] = GM_Mover_Radius;
        frame.moverCount++;
    }

    private static float GM_Clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float GM_Mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static void GM_Env_Fill(GM_Frame frame, ClimateManager climate) {
        GameTime time = GameTime.getInstance();
        float hour = time.getTimeOfDay();
        float dawn = time.getDawn();
        float dusk = time.getDusk();
        float dawnK = 1.0f - GM_Clamp(Math.abs(hour - dawn) / 1.5f, 0.0f, 1.0f);
        float duskK = 1.0f - GM_Clamp(Math.abs(hour - dusk) / 1.5f, 0.0f, 1.0f);
        float lowSun = Math.max(dawnK, duskK);
        float night = GM_Clamp(climate.getNightStrength(), 0.0f, 1.0f);
        frame.sunAmount = GM_Mix(GM_Mix(0.6f, 1.0f, lowSun), 0.2f, night);
        for (int i = 0; i < 3; i++) {
            frame.sunColour[i] = GM_Mix(GM_Mix(GM_Sun_Day[i], GM_Sun_Low[i], lowSun), GM_Sun_Moon[i], night);
        }
        Color exterior = climate.getGlobalLight().getExterior();
        float r = GM_Clamp(exterior.r, 0.0f, 1.0f);
        float g = GM_Clamp(exterior.g, 0.0f, 1.0f);
        float b = GM_Clamp(exterior.b, 0.0f, 1.0f);
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        Color tint = climate.getColorNewFog().getExterior();
        for (int i = 0; i < 3; i++) {
            float base = i == 0 ? r : i == 1 ? g : b;
            float shade = i == 0 ? tint.r : i == 1 ? tint.g : tint.b;
            frame.fogColour[i] = GM_Clamp(shade, 0.0f, 1.0f) * GM_Mix(luma, base, 0.35f);
        }
        float fog = GM_Clamp(climate.getFogIntensity(), 0.0f, 1.0f);
        frame.fogAmount = Math.max(fog, dawnK * 0.15f + night * 0.05f);
    }

    private static void GM_Motion_Fill(GM_Frame frame, int p, IsoPlayer player, float camX, float camY, float dt) {
        GM_Motion motion = GM_Motions[p];
        if (motion == null) {
            motion = new GM_Motion();
            GM_Motions[p] = motion;
        }
        float px = player != null ? player.getX() : camX;
        float py = player != null ? player.getY() : camY;
        if (motion.valid && dt > 0.0f) {
            float vx = (px - motion.lastX) / dt;
            float vy = (py - motion.lastY) / dt;
            float speed = (float) Math.sqrt(vx * vx + vy * vy);
            if (speed > GM_Speed_Cap) {
                vx = vy = 0.0f;
            }
            float k = GM_Clamp(dt * 8.0f, 0.0f, 1.0f);
            motion.vx = GM_Mix(motion.vx, vx, k);
            motion.vy = GM_Mix(motion.vy, vy, k);
        } else if (dt <= 0.0f) {
            motion.vx = 0.0f;
            motion.vy = 0.0f;
        }
        motion.lastX = px;
        motion.lastY = py;
        motion.valid = true;
        frame.playerX = px;
        frame.playerY = py;
        frame.playerVX = motion.vx;
        frame.playerVY = motion.vy;
    }

    static void GM_Mark(Object self, Object draw, int player) {
        if (self != GM_Screen || !(draw instanceof TextureDraw)) {
            return;
        }
        TextureDraw texd = (TextureDraw) draw;
        if (texd.vars != null && texd.vars.length > GM_Var_Seq) {
            texd.vars[GM_Var_Seq] = GM_Seq;
        }
    }

    static GM_Frame GM_Frame_Of(int seq, int player) {
        if (seq <= 0 || player < 0 || player >= GM_Players) {
            return null;
        }
        GM_Frame frame = GM_Ring[seq % GM_Ring_Size][player];
        return frame != null && frame.seq == seq ? frame : null;
    }

    static void GM_Bind(Object self, Object draw) {
        if (GM_Failed || self != GM_Screen || !(draw instanceof TextureDraw)) {
            return;
        }
        long start = GM_Timing.GM_Cpu_Start();
        GM_Bind_Run((TextureDraw) draw);
        GM_Timing.GM_Cpu_Bind(start);
    }

    private static void GM_Bind_Run(TextureDraw texd) {
        int player = SpriteRenderer.instance.getRenderingPlayerIndex();
        int seq = texd.vars != null && texd.vars.length > GM_Var_Seq ? (int) texd.vars[GM_Var_Seq] : 0;
        GM_Frame frame = GM_Frame_Of(seq, player);
        if (frame == null) {
            frame = GM_Frame_Of(seq, 0);
        }
        if (frame == null) {
            return;
        }
        int program = GL11.glGetInteger(0x8B8D);
        if (program == 0) {
            return;
        }
        GM_Locs locs = GM_Screen_Locs;
        if (program != locs.program) {
            locs.GM_Find(program);
            GM_Loc_Sun = GL20.glGetUniformLocation(program, "GM_Sun");
            GM_Loc_SunCol = GL20.glGetUniformLocation(program, "GM_SunCol");
            GM_Loc_FogCol = GL20.glGetUniformLocation(program, "GM_FogCol");
            GM_Loc_Fx = GL20.glGetUniformLocation(program, "GM_Fx");
            GM_Loc_Bypass = GL20.glGetUniformLocation(program, "GM_Bypass");
            GM_Loc_Debug = GL20.glGetUniformLocation(program, "GM_Debug");
        }
        if (locs.view < 0) {
            return;
        }
        int unit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        GM_Field.GM_Data field;
        try {
            GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit_Depth);
            GL11.glBindTexture(GL_TEXTURE_2D, GM_Pass.GM_Depth_Texture());
            if (GM_Settings.GM_Test("screen")) {
                throw new IllegalStateException("test failure: screen");
            }
            GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit_FogTex);
            GL11.glBindTexture(GL_TEXTURE_2D, GM_Pass.GM_Fog_Texture());
            field = GM_Field.GM_Upload(GM_Unit_Field, GM_Unit_Bank);
        } finally {
            Texture.lastTextureID = -1;
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
            GL13.glActiveTexture(unit);
        }
        GL20.glUniform1i(locs.depth, GM_Unit_Depth);
        GL20.glUniform1i(locs.field, GM_Unit_Field);
        if (locs.bank >= 0 && locs.indoors >= 0) {
            GL20.glUniform1i(locs.bank, GM_Unit_Bank);
            GL20.glUniform1f(locs.indoors, frame.indoors);
        }
        GL20.glUniform1i(locs.fogTex, GM_Unit_FogTex);
        GM_Uniforms(frame, field, locs);
        GL20.glUniform4f(GM_Loc_Sun, 0.0f, 0.0f, frame.sunAmount, 0.0f);
        GL20.glUniform3fv(GM_Loc_SunCol, frame.sunColour);
        GL20.glUniform3fv(GM_Loc_FogCol, frame.fogColour);
        GL20.glUniform1fv(GM_Loc_Fx, frame.fx);
        GL20.glUniform1f(GM_Loc_Bypass, frame.bypass ? 1.0f : 0.0f);
        GL20.glUniform1f(GM_Loc_Debug, frame.debug);
    }

    static void GM_Uniforms(GM_Frame frame, GM_Field.GM_Data field, GM_Locs locs) {
        if (locs.view >= 0) {
            GL20.glUniform4f(locs.view, frame.offX, frame.offY, frame.zoom, frame.tileScale);
        }
        if (locs.tex >= 0) {
            GL20.glUniform4f(locs.tex, frame.texLeft, frame.texBottom, 1.0f / Math.max(1, GM_Pass.GM_Width()), 1.0f / Math.max(1, GM_Pass.GM_Height()));
        }
        if (locs.ref >= 0) {
            GL20.glUniform4f(locs.ref, frame.depthRef, frame.sumRef, GM_Delta, 0.0f);
        }
        if (locs.rect >= 0) {
            if (field != null) {
                GL20.glUniform4f(locs.rect, field.x0, field.y0, 1.0f / GM_Field.GM_Size, 1.0f / GM_Field.GM_Size);
            } else {
                GL20.glUniform4f(locs.rect, 0.0f, 0.0f, 1.0f / GM_Field.GM_Size, 1.0f / GM_Field.GM_Size);
            }
        }
        if (locs.on >= 0) {
            GL20.glUniform1f(locs.on, field != null ? 1.0f : 0.0f);
        }
        if (locs.fogRect >= 0) {
            GL20.glUniform4f(locs.fogRect, frame.fogX, frame.fogY, 1.0f / GM_Fog_Window, GM_Fog_Window);
        }
        if (locs.fog >= 0) {
            GL20.glUniform4f(locs.fog, frame.fogAmount, GM_Fog_Height, 0.0f, 0.0f);
        }
        if (locs.wind >= 0) {
            GL20.glUniform4f(locs.wind, frame.windVX, frame.windVY, frame.driftX, frame.driftY);
        }
        if (locs.time >= 0) {
            GL20.glUniform1f(locs.time, frame.time);
        }
        if (locs.player >= 0) {
            GL20.glUniform4f(locs.player, frame.playerX, frame.playerY, frame.playerVX, frame.playerVY);
        }
        if (locs.wake >= 0) {
            GL20.glUniform1f(locs.wake, frame.wake);
        }
        if (locs.fogTime >= 0) {
            GL20.glUniform1f(locs.fogTime, frame.fogTime);
        }
        if (locs.movers >= 0 && locs.moverCount >= 0) {
            GL20.glUniform4fv(locs.movers, frame.movers);
            GL20.glUniform1i(locs.moverCount, frame.moverCount);
        }
    }

    static boolean GM_Fog_Vanilla(boolean result) {
        GM_Settings settings = GM_Settings.GM_Get();
        if (!result || !GM_Active() || !settings.GM_On(GM_Features.GM_Fog) || settings.bypass) {
            return result;
        }
        return false;
    }
}
