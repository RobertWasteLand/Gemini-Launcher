package GL_Java.GM_Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.HexFormat;

import zombie.ZomboidFileSystem;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.core.opengl.Shader;
import zombie.core.opengl.RenderThread;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWater;
import zombie.iso.IsoWaterGeometry;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.WaterShader;
import zombie.iso.fboRenderChunk.ObjectRenderLayer;
import zombie.iso.sprite.IsoSprite;

public final class GM_Environment {
    static final String GM_Shader_High = "GM_Environment/GM_Water_hq";
    static final String GM_Shader_Medium = "GM_Environment/GM_Water";
    static final String GM_Shader_Check = "media/shaders/GM_Environment/GM_Water_hq.frag";
    static final float GM_Toon_Default = 0.55f;
    static final int GM_Plant_Reach = 6;

    public static volatile boolean GM_Solid_Bank;
    public static volatile boolean GM_Water_Plants;
    private static volatile Thread GM_Attach_Thread;
    private static boolean GM_Cell_Water;
    private static volatile boolean GM_Failed;
    private static volatile boolean GM_Missing;
    private static boolean GM_Shown;
    private static Object GM_Effect;
    private static Object GM_Vanilla_Effect;
    private static boolean GM_Field_Idle;
    private static volatile int GM_Shore_Live = 7;
    private static volatile boolean GM_Shore_Pending;
    private static int GM_Effect_Quality = -1;

    private GM_Environment() {
    }

    public static synchronized void GM_Marker_Print() {
        if (GM_Shown) {
            return;
        }
        GM_Shown = true;
        System.out.println("[GL_Java] GM_Environment active (" + GM_Build_Tag() + ")");
        GM_Settings_Tick(true);
        String changed = GM_Hooks.GM_Run();
        if (changed != null) {
            GM_Shaders.GM_Fail("game check failed", new IllegalStateException(changed));
            GM_Fail("game check failed", new IllegalStateException(changed));
        } else if (GM_Settings.GM_Test("opengl") || GM_Old_OpenGL()) {
            String card = "this graphics card or driver does not support OpenGL 3.3, so the game runs its older shader mode and the effects stay off";
            GM_Shaders.GM_Fail("older OpenGL", new IllegalStateException(card));
            GM_Fail("older OpenGL", new IllegalStateException(card));
        }
        GM_Layers_Apply();
    }

    private static boolean GM_Old_OpenGL() {
        try {
            return Core.getInstance().getUseOpenGL21();
        } catch (Throwable t) {
            return false;
        }
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
        GM_Layers_Apply();
        System.out.println("[GL_Java] GM_Environment off: " + what + " (" + t + ")");
        GM_Shaders.GM_Fail("the water side stopped", t);
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

    private static int GM_Shore_State(GM_Settings settings) {
        if (GM_Failed || GM_Missing || !settings.GM_On(GM_Features.GM_Water)) {
            return 0;
        }
        if (!settings.GM_On(GM_Features.GM_Water_Shore)) {
            return 4;
        }
        return 4 | (GM_Settings.GM_Test("shore_fade") ? 0 : 1) | (GM_Settings.GM_Test("shore_tiles") ? 0 : 2);
    }

    private static void GM_Layers_Apply() {
        int state = GM_Shore_State(GM_Settings.GM_Get());
        GM_Solid_Bank = (state & 1) != 0;
        GM_Water_Plants = (state & 4) != 0;
        if (state != GM_Shore_Live) {
            GM_Shore_Live = state;
            GM_Shore_Pending = true;
        }
    }

    private static void GM_Settings_Tick(boolean force) {
        if (GM_Failed) {
            return;
        }
        try {
            if (!GM_Settings.GM_Poll(force)) {
                return;
            }
            GM_Layers_Apply();
        } catch (Throwable t) {
            GM_Fail("settings check failed", t);
        }
    }

    private static void GM_Shore_Refresh() {
        if (!GM_Shore_Pending) {
            return;
        }
        GM_Shore_Pending = false;
        try {
            GM_Shore.GM_Refresh();
        } catch (Throwable t) {
            if (GM_Failed) {
                System.out.println("[GL_Java] GM_Environment shore refresh failed (" + t + ")");
            } else {
                GM_Fail("shore refresh failed", t);
            }
        }
    }

    public static boolean GM_Shore_Fade(boolean shore) {
        return shore && !GM_Solid_Bank;
    }

    public static ObjectRenderLayer GM_Water_Layer(IsoObject object, ObjectRenderLayer layer) {
        if (!GM_Water_Plants || object == null || (layer != ObjectRenderLayer.MinusFloor && layer != ObjectRenderLayer.Vegetation)) {
            return layer;
        }
        try {
            IsoSprite sprite = object.getSprite();
            IsoGridSquare square = object.getSquare();
            if (sprite == null || square == null || square.z != 0 || sprite.solidfloor || sprite.cutN || sprite.cutW
                || !(sprite.isBush || sprite.canBeRemoved || sprite.attachedFloor || object.getClass() == IsoObject.class)) {
                return layer;
            }
            IsoWaterGeometry water = square.getWater();
            if (water != null && (water.hasWater() || water.isbShore())) {
                return ObjectRenderLayer.Translucent;
            }
            return GM_Water_Behind(square) ? ObjectRenderLayer.Translucent : layer;
        } catch (Throwable t) {
            GM_Fail("water plant layer failed", t);
            return layer;
        }
    }

    private static boolean GM_Water_Behind(IsoGridSquare square) {
        IsoWorld world = IsoWorld.instance;
        IsoCell cell = world == null ? null : world.currentCell;
        if (cell == null) {
            return false;
        }
        for (int a = 0; a <= GM_Plant_Reach; a++) {
            for (int b = 0; b <= GM_Plant_Reach; b++) {
                IsoGridSquare behind = cell.getGridSquare(square.x - a, square.y - b, 0);
                if (behind != null && behind.has(IsoFlagType.water)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void GM_Cell_Water_Enter() {
        GM_Cell_Water = true;
        GM_Attach_Thread = null;
    }

    public static void GM_Cell_Water_Exit() {
        GM_Cell_Water = false;
        GM_Attach_Thread = null;
    }

    public static void GM_Water_Drawn() {
        if (GM_Cell_Water && !GM_Shaders.GM_Off() && GM_Settings.GM_Get().screen) {
            GM_Attach_Thread = Thread.currentThread();
        }
    }

    public static boolean GM_Depth_Write(boolean write) {
        return write || GM_Attach_Thread == Thread.currentThread();
    }

    public static void GM_Water_Check(Object self) {
        if (!(self instanceof IsoWater)) {
            return;
        }
        if (GM_Failed || GM_Missing) {
            GM_Water_Restore((IsoWater) self);
            return;
        }
        GM_Settings_Tick(false);
        try {
            IsoWater water = (IsoWater) self;
            if (GM_Settings.GM_Test("water")) {
                throw new IllegalStateException("test failure: water");
            }
            if (!water.getShaderEnable()) {
                return;
            }
            int quality = PerformanceSettings.waterQuality;
            if (water.effect != GM_Effect) {
                GM_Vanilla_Effect = water.effect;
            }
            boolean on = GM_Settings.GM_Get().GM_On(GM_Features.GM_Water);
            if (GM_Effect != null && quality == GM_Effect_Quality) {
                if (water.effect == GM_Effect && !((Shader) GM_Effect).isCompiled()) {
                    GM_Fail("the water shader stopped compiling", new IllegalStateException(quality == 0 ? GM_Shader_High : GM_Shader_Medium));
                    GM_Water_Restore(water);
                    return;
                }
                Object wanted = on ? GM_Effect : GM_Vanilla_Effect;
                if (wanted != null && water.effect != wanted) {
                    water.effect = (Shader) wanted;
                }
                return;
            }
            if (!ZomboidFileSystem.instance.isKnownFile(GM_Shader_Check)) {
                GM_Missing = true;
                GM_Layers_Apply();
                System.out.println("[GL_Java] GM_Environment off for this world: the GM_Environment mod is not enabled, so its water shaders are not loaded");
                return;
            }
            RenderThread.invokeOnRenderContext(() -> GM_Water_Install(water, quality, on));
        } catch (Throwable t) {
            GM_Fail("water check failed", t);
        }
    }

    private static void GM_Water_Restore(IsoWater water) {
        try {
            if (GM_Effect != null && GM_Vanilla_Effect != null && water.effect == GM_Effect) {
                water.effect = (Shader) GM_Vanilla_Effect;
                System.out.println("[GL_Java] GM_Environment water back to vanilla");
            }
        } catch (Throwable t) {
            System.out.println("[GL_Java] GM_Environment water restore failed (" + t + ")");
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
            if (GM_Settings.GM_Test("field")) {
                throw new IllegalStateException("test failure: field");
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
            if ((GM_Shore_State(GM_Settings.GM_Get()) & 2) == 0) {
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
