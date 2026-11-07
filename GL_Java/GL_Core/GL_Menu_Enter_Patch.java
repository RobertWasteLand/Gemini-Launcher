package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.MainScreenState", methodName = "enter")
public final class GL_Menu_Enter_Patch {
    private GL_Menu_Enter_Patch() {
    }

    @Patch.OnEnter
    public static void GL_Enter() {
        GL_Menu.GL_Theme_Start();
    }
}
