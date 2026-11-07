package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.Core", methodName = "EndFrameUI")
public final class GL_Menu_Frame_Patch {
    private GL_Menu_Frame_Patch() {
    }

    @Patch.OnEnter
    public static void GL_Enter() {
        GL_Menu.GL_Frame_End();
    }
}
