package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.Core", methodName = "RenderOffScreenBuffer")
public final class GM_Screen_Patch {
    private GM_Screen_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Environment.GM_Screen_Check();
    }
}
