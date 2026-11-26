package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.Core", methodName = "EndFrameUI")
public final class GL_Intro_Frame_Patch {
    private GL_Intro_Frame_Patch() {
    }

    @Patch.OnEnter
    public static void GL_Enter() {
        GL_Intro.GL_Frame_End();
    }
}
