package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.fboRenderChunk.FBORenderCell", methodName = "renderWater")
public final class GM_Cell_Patch {
    private GM_Cell_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Environment.GM_Cell_Water_Enter();
    }

    @Patch.OnExit
    public static void GM_Exit() {
        GM_Environment.GM_Cell_Water_Exit();
    }
}
