package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.sprite.shapers.FloorShaper", methodName = "setShore")
public final class GM_Floor_Patch {
    private GM_Floor_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter(@Patch.Argument(value = 0, readOnly = false) boolean shore) {
        shore = GM_Environment.GM_Shore_Fade(shore);
    }
}
