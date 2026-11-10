package GL_Java.GM_Particles;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.EffectsManager", methodName = "startMuzzleFlash")
public final class GM_Particles_Weapon_Patch {
    private GM_Particles_Weapon_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter(@Patch.Argument(0) Object character) {
        GM_Particles.GM_Weapon_Fired(character);
    }
}
