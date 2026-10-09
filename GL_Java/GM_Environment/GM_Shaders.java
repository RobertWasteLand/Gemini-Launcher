package GL_Java.GM_Environment;

import java.util.Stack;

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
import zombie.iso.IsoLightSource;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.weather.ClimateManager;
import zombie.iso.weather.WeatherShader;

final class GM_Shaders {
    static final String GM_Name = "GM_Environment/GM_Shaders_Screen";
    static final String GM_Check_File = "media/shaders/GM_Environment/GM_Shaders_Screen.frag";
    static final int GM_Ring_Size = 8;
    static final int GM_Players = 4;
    static final int GM_Lamp_Count = 8;
    static final int GM_Var_Seq = 20;
    static final float GM_Delta = 0.023093667f / 16.0f;
    static final float GM_Fog_Height = 0.6f;
    static final float GM_Fog_Window = 128.0f;
    static final float GM_Fog_Snap = 4.0f;
    static final float GM_Ray_Strength = 0.6f;
    static final float GM_Refl_Strength = 0.55f;
    static final float GM_Bloom_Strength = 0.18f;
    static final float GM_Wind_Base = 0.12f;
    static final float GM_Wind_Gain = 0.7f;
    static final float GM_Speed_Cap = 8.0f;
    static final int GM_Unit_Depth = 1;
    static final int GM_Unit_Rays = 2;
    static final int GM_Unit_Bloom = 3;
    static final int GM_Unit_Field = 4;
    static final int GM_Unit_Bank = 5;
    static final int GM_Unit_FogTex = 6;
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
        float depthRef;
        float sumRef;
        float sunDirX;
        float sunDirY;
        float sunAmount;
        float sunStep;
        final float[] sunColour = new float[3];
        final float[] fogColour = new float[3];
        final float[] balance = new float[3];
        float fogAmount;
        float night;
        float time;
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
        final float[] lamps = new float[GM_Lamp_Count * 4];
        final float[] lampColours = new float[GM_Lamp_Count * 3];
    }

    static final class GM_Locs {
        int program = -1;
        int depth = -1;
        int field = -1;
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

        void GM_Find(int id) {
            program = id;
            depth = GL20.glGetUniformLocation(id, "GM_Depth");
            field = GL20.glGetUniformLocation(id, "GM_Field");
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
    private static final GM_Motion[] GM_Motions = new GM_Motion[GM_Players];
    private static long GM_Last_Nanos;
    private static float GM_Clock;
    private static double GM_Drift_X;
    private static double GM_Drift_Y;
    private static final float[] GM_Fx = {1, 1, 1, 1, 1, 0, 1, 1, 1, 1};
    private static final GM_Locs GM_Screen_Locs = new GM_Locs();
    private static int GM_Loc_Rays = -1;
    private static int GM_Loc_Bloom = -1;
    private static int GM_Loc_Sun = -1;
    private static int GM_Loc_SunCol = -1;
    private static int GM_Loc_FogCol = -1;
    private static int GM_Loc_Night = -1;
    private static int GM_Loc_WB = -1;
    private static int GM_Loc_Str = -1;
    private static int GM_Loc_Fx = -1;
    private static int GM_Loc_Lamps = -1;
    private static int GM_Loc_LampCol = -1;

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
            if (current != GM_Screen) {
                GM_Vanilla = current;
                SceneShaderStore.weatherShader = (zombie.core.opengl.Shader) GM_Screen;
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

    private static void GM_Install() {
        try {
            WeatherShader shader = new WeatherShader(GM_Name);
            boolean logging = DebugLog.isEnabled(DebugType.Shader);
            if (!logging) {
                DebugLog.setLogEnabled(DebugType.Shader, true);
            }
            boolean passes;
            try {
                shader.Start();
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
            GM_Screen = shader;
            SceneShaderStore.weatherShader = shader;
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
        GM_Frame[] frames = new GM_Frame[players];
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
            GM_Frame_Fill(frame, p, dt, climate);
            frames[p] = frame;
        }
        SpriteRenderer.instance.drawGeneric(new GM_Pass(frames[0]));
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
        GM_Lamps_Fill(frame, level, camX, camY);
        GM_Motion_Fill(frame, p, player, camX, camY, dt);
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
        frame.night = night;
        frame.sunAmount = GM_Mix(GM_Mix(0.6f, 1.0f, lowSun), 0.2f, night);
        frame.sunStep = GM_Mix(GM_Mix(0.6f, 1.1f, lowSun), 0.9f, night);
        float[] day = {1.0f, 0.97f, 0.88f};
        float[] low = {1.0f, 0.74f, 0.52f};
        float[] moon = {0.55f, 0.65f, 0.85f};
        for (int i = 0; i < 3; i++) {
            frame.sunColour[i] = GM_Mix(GM_Mix(day[i], low[i], lowSun), moon[i], night);
        }
        float[] dayWB = {1.0f, 1.0f, 1.0f};
        float[] lowWB = {1.02f, 0.99f, 0.96f};
        float[] nightWB = {0.96f, 0.98f, 1.05f};
        for (int i = 0; i < 3; i++) {
            frame.balance[i] = GM_Mix(GM_Mix(dayWB[i], lowWB[i], lowSun), nightWB[i], night);
        }
        Color exterior = climate.getGlobalLight().getExterior();
        float r = GM_Clamp(exterior.r, 0.0f, 1.0f);
        float g = GM_Clamp(exterior.g, 0.0f, 1.0f);
        float b = GM_Clamp(exterior.b, 0.0f, 1.0f);
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float[] tint = {0.93f, 0.98f, 1.0f};
        float[] base = {r, g, b};
        for (int i = 0; i < 3; i++) {
            frame.fogColour[i] = (GM_Mix(base[i], luma, 0.55f) * 0.78f + 0.03f) * tint[i];
        }
        float fog = GM_Clamp(climate.getFogIntensity(), 0.0f, 1.0f);
        frame.fogAmount = Math.max(fog, dawnK * 0.15f + night * 0.05f);
        float dirX = -0.62f;
        float dirY = -0.78f;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        frame.sunDirX = dirX / len;
        frame.sunDirY = dirY / len;
    }

    private static void GM_Lamps_Fill(GM_Frame frame, int level, float camX, float camY) {
        float[] lamps = frame.lamps;
        float[] colours = frame.lampColours;
        java.util.Arrays.fill(lamps, 0.0f);
        java.util.Arrays.fill(colours, 0.0f);
        IsoWorld world = IsoWorld.instance;
        if (world == null) {
            return;
        }
        IsoCell cell = world.getCell();
        if (cell == null) {
            return;
        }
        Stack<IsoLightSource> lights = cell.getLamppostPositions();
        if (lights == null) {
            return;
        }
        float screenW = IsoCamera.getScreenWidth(frame.player);
        float screenH = IsoCamera.getScreenHeight(frame.player);
        float pxPerTile = 49.0f / frame.zoom * (frame.tileScale / 2.0f);
        int filled = 0;
        float[] best = new float[GM_Lamp_Count];
        for (int i = 0; i < lights.size(); i++) {
            IsoLightSource light = lights.get(i);
            if (light == null || !light.active || light.z != level || light.radius <= 0) {
                continue;
            }
            float sx = (IsoUtils.XToScreen(light.x + 0.5f, light.y + 0.5f, 0.0f, 0) - frame.offX) / frame.zoom;
            float sy = (IsoUtils.YToScreen(light.x + 0.5f, light.y + 0.5f, light.z + 0.7f, 0) - frame.offY) / frame.zoom;
            float margin = screenW * 0.25f;
            if (sx < -margin || sy < -margin || sx > screenW + margin || sy > screenH + margin) {
                continue;
            }
            float dx = light.x - camX;
            float dy = light.y - camY;
            float dist = dx * dx + dy * dy;
            int at = -1;
            if (filled < GM_Lamp_Count) {
                at = filled++;
            } else {
                int worst = 0;
                for (int k = 1; k < GM_Lamp_Count; k++) {
                    if (best[k] > best[worst]) {
                        worst = k;
                    }
                }
                if (best[worst] > dist) {
                    at = worst;
                }
            }
            if (at < 0) {
                continue;
            }
            best[at] = dist;
            float radius = Math.min(light.radius, 10) * pxPerTile;
            lamps[at * 4] = sx;
            lamps[at * 4 + 1] = sy;
            lamps[at * 4 + 2] = radius;
            lamps[at * 4 + 3] = 1.0f;
            colours[at * 3] = GM_Clamp(light.r, 0.0f, 1.0f);
            colours[at * 3 + 1] = GM_Clamp(light.g, 0.0f, 1.0f);
            colours[at * 3 + 2] = GM_Clamp(light.b, 0.0f, 1.0f);
        }
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
        TextureDraw texd = (TextureDraw) draw;
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
            GM_Loc_Rays = GL20.glGetUniformLocation(program, "GM_Rays");
            GM_Loc_Bloom = GL20.glGetUniformLocation(program, "GM_Bloom");
            GM_Loc_Sun = GL20.glGetUniformLocation(program, "GM_Sun");
            GM_Loc_SunCol = GL20.glGetUniformLocation(program, "GM_SunCol");
            GM_Loc_FogCol = GL20.glGetUniformLocation(program, "GM_FogCol");
            GM_Loc_Night = GL20.glGetUniformLocation(program, "GM_Night");
            GM_Loc_WB = GL20.glGetUniformLocation(program, "GM_WB");
            GM_Loc_Str = GL20.glGetUniformLocation(program, "GM_Str");
            GM_Loc_Fx = GL20.glGetUniformLocation(program, "GM_Fx");
            GM_Loc_Lamps = GL20.glGetUniformLocation(program, "GM_Lamps");
            GM_Loc_LampCol = GL20.glGetUniformLocation(program, "GM_LampCol");
        }
        if (locs.view < 0) {
            return;
        }
        int unit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit_Depth);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Pass.GM_Depth_Texture());
        GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit_Rays);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Pass.GM_Rays_Texture());
        GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit_Bloom);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Pass.GM_Bloom_Texture());
        GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit_FogTex);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Pass.GM_Fog_Texture());
        GM_Field.GM_Data field = GM_Field.GM_Upload(GM_Unit_Field, GM_Unit_Bank);
        Texture.lastTextureID = -1;
        SpriteRenderer.ringBuffer.restoreBoundTextures = true;
        GL13.glActiveTexture(unit);
        GL20.glUniform1i(locs.depth, GM_Unit_Depth);
        GL20.glUniform1i(GM_Loc_Rays, GM_Unit_Rays);
        GL20.glUniform1i(GM_Loc_Bloom, GM_Unit_Bloom);
        GL20.glUniform1i(locs.field, GM_Unit_Field);
        GL20.glUniform1i(locs.fogTex, GM_Unit_FogTex);
        GM_Uniforms(frame, field, locs);
        GL20.glUniform4f(GM_Loc_Sun, frame.sunDirX, frame.sunDirY, frame.sunAmount, GM_Ray_Strength);
        GL20.glUniform3fv(GM_Loc_SunCol, frame.sunColour);
        GL20.glUniform3fv(GM_Loc_FogCol, frame.fogColour);
        GL20.glUniform1f(GM_Loc_Night, frame.night);
        GL20.glUniform3fv(GM_Loc_WB, frame.balance);
        GL20.glUniform4f(GM_Loc_Str, GM_Refl_Strength, GM_Bloom_Strength, 0.0f, 0.0f);
        GL20.glUniform1fv(GM_Loc_Fx, GM_Fx);
        GL20.glUniform4fv(GM_Loc_Lamps, frame.lamps);
        GL20.glUniform3fv(GM_Loc_LampCol, frame.lampColours);
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
            GL20.glUniform1f(locs.wake, GM_Fx[2]);
        }
    }

    static boolean GM_Fog_Vanilla(boolean result) {
        if (!result || !GM_Active() || GM_Fx[0] < 0.5f) {
            return result;
        }
        return false;
    }

    static void GM_Water_Depth(boolean shore, int count) {
        if (shore || count <= 0 || !GM_Active()) {
            return;
        }
        GM_Pass.GM_Water_Depth(count);
    }
}
