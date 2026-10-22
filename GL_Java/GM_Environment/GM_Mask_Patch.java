package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.IndieGL", methodName = "glDepthMask")
public final class GM_Mask_Patch {
    private GM_Mask_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter(@Patch.Argument(value = 0, readOnly = false) boolean write) {
        write = GM_Environment.GM_Depth_Write(write);
    }
}
