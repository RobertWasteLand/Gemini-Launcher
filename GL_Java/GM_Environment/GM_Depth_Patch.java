package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.IsoWater", methodName = "renderSome")
public final class GM_Depth_Patch {
    private GM_Depth_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.Argument(2) boolean shore, @Patch.Return int count) {
        GM_Environment.GM_Water_Depth(shore, count);
    }
}
