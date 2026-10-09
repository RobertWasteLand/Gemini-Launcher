package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.weather.WeatherShader", methodName = "startMainThread")
public final class GM_Frame_Patch {
    private GM_Frame_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.This Object self, @Patch.Argument(0) Object draw, @Patch.Argument(1) int player) {
        GM_Environment.GM_Screen_Mark(self, draw, player);
    }
}
