package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.Core", methodName = "EndFrameUI")
public final class GL_Core_Watermark {
    private GL_Core_Watermark() {
    }

    @Patch.OnEnter
    public static void GL_Enter() {
        GL_Core.GL_Watermark_Tick();
    }
}
