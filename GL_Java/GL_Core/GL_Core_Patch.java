package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.MainScreenState", methodName = "enter")
public final class GL_Core_Patch {
    private GL_Core_Patch() {
    }

    @Patch.OnEnter
    public static void GL_Enter() {
        GL_Core.GL_Marker_Print();
    }
}
