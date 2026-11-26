package GL_Java.GM_Environment;

import java.util.ArrayList;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoGridSquare;

@Patch(className = "zombie.iso.IsoWater", methodName = "renderShore")
public final class GM_Sticker_Patch {
    private GM_Sticker_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.This Object self, @Patch.Argument(0) ArrayList<IsoGridSquare> grid, @Patch.Argument(1) int z) {
        GM_Environment.GM_Shore_Drawn(self, grid, z);
    }
}
