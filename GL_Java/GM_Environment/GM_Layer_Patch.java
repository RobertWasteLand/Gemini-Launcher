package GL_Java.GM_Environment;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;
import zombie.iso.fboRenderChunk.ObjectRenderLayer;

@Patch(className = "zombie.iso.fboRenderChunk.FBORenderCell", methodName = "calculateObjectRenderLayer")
public final class GM_Layer_Patch {
    private GM_Layer_Patch() {
    }

    @Patch.OnExit
    public static void GM_Exit(@Patch.Argument(0) IsoObject object, @Patch.Return(readOnly = false) ObjectRenderLayer layer) {
        layer = GM_Environment.GM_Water_Layer(object, layer);
    }
}
