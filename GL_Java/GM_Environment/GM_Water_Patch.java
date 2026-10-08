package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.IsoWater", methodName = "render")
public final class GM_Water_Patch {
    private GM_Water_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter(@Patch.This Object self) {
        GM_Environment.GM_Water_Check(self);
    }
}
