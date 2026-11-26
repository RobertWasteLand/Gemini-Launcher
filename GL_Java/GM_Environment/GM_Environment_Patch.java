package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.MainScreenState", methodName = "enter")
public final class GM_Environment_Patch {
    private GM_Environment_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Environment.GM_Marker_Print();
        GM_Environment.GM_World_Reset();
    }
}
