package GL_Java.GM_Environment;

import zombie.ZomboidFileSystem;
import zombie.core.PerformanceSettings;
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
    private static int GM_Effect_Quality = -1;

    private GM_Environment() {
    }

    public static synchronized void GM_Marker_Print() {
        if (GM_Shown) {
            return;
        }
        GM_Shown = true;
        System.out.println("[GL_Java] GM_Environment active");
        GM_ShoreFade_Find();
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
    }

    private static void GM_ShoreFade_Find() {
        if (GM_ShoreFade != null) {
            return;
        }
        try {
            GM_ShoreFade = DebugOptions.instance.terrain.renderTiles.isoGridSquare.shoreFade;
        } catch (Throwable t) {
            GM_Fail("shore fade option not found", t);
        }
    }

    public static void GM_Water_Check(Object self) {
        if (GM_Failed || GM_Missing || !(self instanceof IsoWater)) {
            return;
        }
        try {
            IsoWater water = (IsoWater) self;
            if (!water.getShaderEnable()) {
                return;
            }
            int quality = PerformanceSettings.waterQuality;
            if (water.effect == GM_Effect && quality == GM_Effect_Quality) {
                return;
            }
            if (!ZomboidFileSystem.instance.isKnownFile(GM_Shader_Check)) {
                GM_Missing = true;
                System.out.println("[GL_Java] GM_Environment off for this world: the GM_Environment mod is not enabled, so its water shaders are not loaded");
                return;
            }
            GM_ShoreFade_Find();
            RenderThread.invokeOnRenderContext(() -> GM_Water_Install(water, quality));
        } catch (Throwable t) {
            GM_Fail("water shader install failed", t);
        }
    }

    private static void GM_Water_Install(IsoWater water, int quality) {
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
            water.effect = shader;
            GM_Effect = shader;
            GM_Effect_Quality = quality;
            System.out.println("[GL_Java] GM_Environment water on, quality " + quality);
        } catch (Throwable t) {
            GM_Fail("water shader install failed on the render thread", t);
        }
    }

    public static void GM_Field_Tick() {
        if (GM_Failed) {
            return;
        }
        try {
            GM_Field.GM_Tick();
        } catch (Throwable t) {
            GM_Fail("shore field failed", t);
        }
    }

    public static void GM_Water_Bind() {
        if (GM_Failed) {
            return;
        }
        try {
            GM_Field.GM_Bind(GM_Toon_Default);
        } catch (Throwable t) {
            GM_Fail("shader uniforms failed", t);
        }
    }

    public static void GM_Shore_Quad(boolean shore) {
        if (GM_Failed) {
            return;
        }
        try {
            GM_Field.GM_Quad(shore);
        } catch (Throwable t) {
            GM_Fail("shore batch flag failed", t);
        }
    }

    public static IsoWaterGeometry GM_Shore_Init(Object self, IsoGridSquare square, IsoWaterGeometry result) {
        if (GM_Failed) {
            return result;
        }
        try {
            return GM_Shore.GM_Init_Exit(self, square, result);
        } catch (Throwable t) {
            GM_Fail("extra shore tiles failed", t);
            return result;
        }
    }
}
