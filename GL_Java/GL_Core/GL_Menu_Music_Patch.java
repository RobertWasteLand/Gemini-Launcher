package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.SoundManager", methodName = "updateMusic")
public final class GL_Menu_Music_Patch {
    private GL_Menu_Music_Patch() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean GL_Enter() {
        return GL_Menu.GL_Music_Hold();
    }
}
