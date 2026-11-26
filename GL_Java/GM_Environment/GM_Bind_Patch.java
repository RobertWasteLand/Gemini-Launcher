package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.WaterShader", methodName = "updateWaterParams")
public final class GM_Bind_Patch {
    private GM_Bind_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit() {
        GM_Environment.GM_Water_Bind();
    }
}
