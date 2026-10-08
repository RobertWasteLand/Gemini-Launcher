package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.debug.BooleanDebugOption", methodName = "getValue")
public final class GM_ShoreFade_Patch {
    private GM_ShoreFade_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.This Object self, @Patch.Return(readOnly = false) boolean value) {
        if (value && self == GM_Environment.GM_ShoreFade) {
            value = false;
        }
    }
}
