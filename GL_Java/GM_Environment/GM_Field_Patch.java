package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.IsoWater", methodName = "update")
public final class GM_Field_Patch {
    private GM_Field_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Environment.GM_Field_Tick();
    }
}
