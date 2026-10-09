package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.textures.MultiTextureFBO2", methodName = "render")
public final class GM_Queue_Patch {
    private GM_Queue_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Environment.GM_Screen_Queue();
    }
}
