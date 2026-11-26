package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.weather.WeatherShader", methodName = "startRenderThread")
public final class GM_Uniform_Patch {
    private GM_Uniform_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.This Object self, @Patch.Argument(0) Object draw) {
        GM_Environment.GM_Screen_Bind(self, draw);
    }
}
