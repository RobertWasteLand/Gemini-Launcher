package GL_Java.GM_Particles;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.skinnedmodel.model.ModelOutlines", methodName = "endFrameMain")
public final class GM_Particles_Frame_Patch {
    private GM_Particles_Frame_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Particles.GM_Frame();
    }
}
