package GL_Java.GM_Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.HexFormat;

import zombie.ZomboidFileSystem;
import zombie.core.PerformanceSettings;
import zombie.core.opengl.Shader;
import zombie.core.opengl.RenderThread;
import zombie.debug.DebugLog;
import zombie.debug.DebugOptions;
import zombie.debug.DebugType;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWater;
import zombie.iso.IsoWaterGeometry;
import zombie.iso.WaterShader;

public final class GM_Environment {
    static final String GM_Shader_High = "GM_Environment/GM_Water_hq";
    static final String GM_Shader_Medium = "GM_Environment/GM_Water";
    static final String GM_Shader_Check = "media/shaders/GM_Environment/GM_Water_hq.frag";
    static final float GM_Toon_Default = 0.55f;

    public static volatile Object GM_ShoreFade;
    private static volatile boolean GM_Failed;
    private static volatile boolean GM_Missing;
    private static boolean GM_Shown;
    private static Object GM_Effect;
    private static Object GM_Vanilla_Effect;
    private static Object GM_ShoreFade_Option;
    private static boolean GM_Field_Idle;
    private static boolean GM_Shore_Live = true;
    private static boolean GM_Shore_Pending;
    private static int GM_Effect_Quality = -1;

    private GM_Environment() {
    }

    public static synchronized void GM_Marker_Print() {
        if (GM_Shown) {
            return;
        }
        GM_Shown = true;
        System.out.println("[GL_Java] GM_Environment active (" + GM_Build_Tag() + ")");
        GM_ShoreFade_Find();
    }

    private static String GM_Build_Tag() {
        String dev = Boolean.getBoolean("GL_Dev") ? "dev, " : "";
        try {
            CodeSource source = GM_Environment.class.getProtectionDomain().getCodeSource();
            byte[] jar = Files.readAllBytes(Path.of(source.getLocation().toURI()));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(jar);
            return dev + "jar " + HexFormat.of().formatHex(digest, 0, 4);
        } catch (Exception e) {
            return dev + "jar unknown";
        }
    }

    static void GM_Fail(String what, Throwable t) {
        if (GM_Failed) {
            return;
        }
        GM_Failed = true;
        System.out.println("[GL_Java] GM_Environment off: " + what + " (" + t + ")");
    }

    static boolean GM_Off() {
        return GM_Failed;
    }

    public static void GM_World_Reset() {
        GM_Missing = false;
        GM_Settings_Tick(true);
        GM_Shore_Pending = false;
        GM_Shaders.GM_World_Reset();
    }

    private static boolean GM_Shore_On(GM_Settings settings) {
        return settings.GM_On(GM_Features.GM_Water) && settings.GM_On(GM_Features.GM_Water_Shore);
    }

    private static void GM_Settings_Tick(boolean force) {
        if (GM_Failed) {
            return;
        }
        try {
            if (!GM_Settings.GM_Poll(force)) {
                return;
            }
            GM_ShoreFade_Update();
            boolean shore = GM_Shore_On(GM_Settings.GM_Get());
            if (shore != GM_Shore_Live) {
                GM_Shore_Live = shore;
                GM_Shore_Pending = true;
            }
        } catch (Throwable t) {
            GM_Fail("settings check failed", t);
        }
    }

    private static void GM_Shore_Refresh() {
        if (!GM_Shore_Pending || GM_Failed) {
            return;
        }
        GM_Shore_Pending = false;
        try {
            GM_Shore.GM_Refresh();
        } catch (Throwable t) {
            GM_Fail("shore refresh failed", t);
        }
    }

    private static void GM_ShoreFade_Find() {
        if (GM_ShoreFade_Option != null) {
            return;
        }
        try {
            GM_ShoreFade_Option = DebugOptions.instance.terrain.renderTiles.isoGridSquare.shoreFade;
        } catch (Throwable t) {
            GM_Fail("shore fade option not found", t);
        }
        GM_ShoreFade_Update();
    }

    private static void GM_ShoreFade_Update() {
        GM_ShoreFade = GM_Shore_On(GM_Settings.GM_Get()) ? GM_ShoreFade_Option : null;
    }

    public static void GM_Water_Check(Object self) {
        if (GM_Failed || GM_Missing || !(self instanceof IsoWater)) {
            return;
        }
        GM_Settings_Tick(false);
        try {
            IsoWater water = (IsoWater) self;
            if (!water.getShaderEnable()) {
                return;
            }
            int quality = PerformanceSettings.waterQuality;
            if (water.effect != GM_Effect) {
                GM_Vanilla_Effect = water.effect;
            }
            boolean on = GM_Settings.GM_Get().GM_On(GM_Features.GM_Water);
            if (GM_Effect != null && quality == GM_Effect_Quality) {
                Object wanted = on ? GM_Effect : GM_Vanilla_Effect;
                if (wanted != null && water.effect != wanted) {
                    water.effect = (Shader) wanted;
                }
                return;
            }
            if (!ZomboidFileSystem.instance.isKnownFile(GM_Shader_Check)) {
                GM_Missing = true;
                System.out.println("[GL_Java] GM_Environment off for this world: the GM_Environment mod is not enabled, so its water shaders are not loaded");
                return;
            }
            GM_ShoreFade_Find();
            RenderThread.invokeOnRenderContext(() -> GM_Water_Install(water, quality, on));
        } catch (Throwable t) {
            GM_Fail("water shader install failed", t);
        }
    }

    private static void GM_Water_Install(IsoWater water, int quality, boolean on) {
        try {
            WaterShader shader = new WaterShader(quality == 0 ? GM_Shader_High : GM_Shader_Medium);
            boolean logging = DebugLog.isEnabled(DebugType.Shader);
            if (!logging) {
                DebugLog.setLogEnabled(DebugType.Shader, true);
            }
            try {
                shader.Start();
                shader.End();
            } finally {
                if (!logging) {
                    DebugLog.setLogEnabled(DebugType.Shader, false);
                }
            }
            if (!shader.isCompiled()) {
                GM_Fail("water shader did not compile, keeping the game's water", new IllegalStateException(quality == 0 ? GM_Shader_High : GM_Shader_Medium));
                return;
            }
            if (on) {
                water.effect = shader;
            }
            GM_Effect = shader;
            GM_Effect_Quality = quality;
            System.out.println("[GL_Java] GM_Environment water ready, quality " + quality + (on ? ", on" : ", off"));
        } catch (Throwable t) {
            GM_Fail("water shader install failed on the render thread", t);
        }
    }

    public static void GM_Field_Tick() {
        if (GM_Failed) {
            return;
        }
        long start = GM_Timing.GM_Cpu_Start();
        try {
            if (!GM_Settings.GM_Get().field) {
                GM_Field_Idle = true;
                return;
            }
            if (GM_Field_Idle) {
                GM_Field_Idle = false;
                GM_Field.GM_Invalidate();
            }
            GM_Field.GM_Tick();
        } catch (Throwable t) {
            GM_Fail("shore field failed", t);
        }
        GM_Timing.GM_Cpu_Field(start);
    }

    public static void GM_Water_Bind() {
        if (GM_Failed) {
            return;
        }
        try {
            GM_Field.GM_Bind(GM_Settings.GM_Get().GM_Value(GM_Features.GM_Water_Style, Math.round(GM_Toon_Default * 100.0f)) / 100.0f);
        } catch (Throwable t) {
            GM_Fail("shader uniforms failed", t);
        }
    }

    public static void GM_Water_Geometry(boolean shore) {
        GM_Timing.GM_Water(true);
        if (GM_Failed) {
            return;
        }
        try {
            GM_Field.GM_Quad(shore);
        } catch (Throwable t) {
            GM_Fail("shore batch flag failed", t);
        }
    }

    public static void GM_Water_Geometry_Done() {
        GM_Timing.GM_Water(false);
    }

    public static IsoWaterGeometry GM_Shore_Init(Object self, IsoGridSquare square, IsoWaterGeometry result) {
        if (GM_Failed) {
            return result;
        }
        try {
            if (!GM_Shore_On(GM_Settings.GM_Get())) {
                return result;
            }
            return GM_Shore.GM_Init_Exit(self, square, result);
        } catch (Throwable t) {
            GM_Fail("extra shore tiles failed", t);
            return result;
        }
    }

    public static void GM_Screen_Check() {
        GM_Settings_Tick(false);
        GM_Shore_Refresh();
        if (GM_Shaders.GM_Off()) {
            return;
        }
        try {
            GM_Shaders.GM_Check();
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("screen shader check failed", t);
        }
    }

    public static void GM_Screen_Queue() {
        if (GM_Shaders.GM_Off()) {
            return;
        }
        long start = GM_Timing.GM_Cpu_Start();
        try {
            GM_Shaders.GM_Queue();
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("frame data failed", t);
        }
        GM_Timing.GM_Cpu_Queue(start);
    }

    public static void GM_Screen_Done() {
        GM_Timing.GM_Queue_End();
    }

    public static void GM_Screen_Mark(Object self, Object draw, int player) {
        if (GM_Shaders.GM_Off()) {
            return;
        }
        try {
            GM_Shaders.GM_Mark(self, draw, player);
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("frame mark failed", t);
        }
    }

    public static void GM_Screen_Bind(Object self, Object draw) {
        if (GM_Shaders.GM_Off()) {
            return;
        }
        try {
            GM_Shaders.GM_Bind(self, draw);
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("screen uniforms failed", t);
        }
    }

    public static void GM_Water_Depth(boolean shore, int count) {
        if (GM_Shaders.GM_Off()) {
            return;
        }
        try {
            GM_Shaders.GM_Water_Depth(shore, count);
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("water depth failed", t);
        }
    }

    public static boolean GM_Fog_Vanilla(boolean result) {
        if (GM_Shaders.GM_Off()) {
            return result;
        }
        try {
            return GM_Shaders.GM_Fog_Vanilla(result);
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("fog switch failed", t);
            return result;
        }
    }
}
