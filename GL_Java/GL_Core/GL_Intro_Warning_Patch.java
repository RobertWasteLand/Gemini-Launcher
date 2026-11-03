package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.GameWindow", methodName = "doEpilepsyWarningText")
public final class GL_Intro_Warning_Patch {
    private GL_Intro_Warning_Patch() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean GL_Enter() {
        return GL_Intro.GL_Warning_Draw();
    }
}
