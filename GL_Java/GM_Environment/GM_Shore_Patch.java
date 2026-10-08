package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWaterGeometry;

@Patch(className = "zombie.iso.IsoWaterGeometry", methodName = "init")
public final class GM_Shore_Patch {
    private GM_Shore_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.This Object self, @Patch.Argument(0) IsoGridSquare square, @Patch.Return(readOnly = false) IsoWaterGeometry result) {
        result = GM_Shore.GM_Init_Exit(self, square, result);
    }
}
