package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.weather.fog.ImprovedFog", methodName = "startRender")
public final class GM_Fog_Patch {
    private GM_Fog_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.Return(readOnly = false) boolean result) {
        result = GM_Environment.GM_Fog_Vanilla(result);
    }
}
