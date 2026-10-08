package GL_Java.GM_Environment;

import zombie.core.PerformanceSettings;
import zombie.core.opengl.RenderThread;
import zombie.debug.DebugOptions;
import zombie.iso.IsoWater;
import zombie.iso.WaterShader;

public final class GM_Environment {
    static final String GM_Shader_High = "GM_Environment/GM_Water_hq";
    static final String GM_Shader_Medium = "GM_Environment/GM_Water";
    static final float GM_Toon_Default = 0.55f;

    public static volatile Object GM_ShoreFade;
    private static volatile boolean GM_Failed;
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
        if (GM_Failed || !(self instanceof IsoWater)) {
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
            GM_ShoreFade_Find();
            RenderThread.invokeOnRenderContext(() -> GM_Water_Install(water, quality));
        } catch (Throwable t) {
            GM_Fail("water shader install failed", t);
        }
    }

    private static void GM_Water_Install(IsoWater water, int quality) {
        try {
            WaterShader shader = new WaterShader(quality == 0 ? GM_Shader_High : GM_Shader_Medium);
            shader.Start();
            shader.End();
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
}
