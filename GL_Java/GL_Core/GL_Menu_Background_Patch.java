package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.MainScreenState", methodName = "renderBackground")
public final class GL_Menu_Background_Patch {
    private GL_Menu_Background_Patch() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean GL_Enter() {
        return GL_Menu.GL_Background_Draw();
    }
}
