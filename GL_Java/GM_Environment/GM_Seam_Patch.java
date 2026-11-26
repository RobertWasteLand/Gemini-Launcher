package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.tileDepth.TileSeamModifier", methodName = "setShore")
public final class GM_Seam_Patch {
    private GM_Seam_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter(@Patch.Argument(value = 0, readOnly = false) boolean shore) {
        shore = GM_Environment.GM_Shore_Fade(shore);
    }
}
