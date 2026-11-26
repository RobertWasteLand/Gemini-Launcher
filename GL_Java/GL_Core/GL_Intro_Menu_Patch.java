package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.MainScreenState", methodName = "enter")
public final class GL_Intro_Menu_Patch {
    private GL_Intro_Menu_Patch() {
    }

    @Patch.OnEnter
    public static void GL_Enter() {
        GL_Intro.GL_Menu_Enter();
    }
}
