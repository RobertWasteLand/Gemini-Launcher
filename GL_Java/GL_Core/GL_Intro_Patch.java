package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.TISLogoState", methodName = "render")
public final class GL_Intro_Patch {
    private GL_Intro_Patch() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean GL_Enter() {
        return GL_Intro.GL_Logo_Render();
    }
}
