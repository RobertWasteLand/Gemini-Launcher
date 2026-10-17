package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.IsoWater", methodName = "waterGeometry")
public final class GM_Quad_Patch {
    private GM_Quad_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter(@Patch.Argument(2) boolean shore) {
        GM_Environment.GM_Water_Geometry(shore);
    }

    @Patch.OnExit
    public static void GM_Exit() {
        GM_Environment.GM_Water_Geometry_Done();
    }
}
