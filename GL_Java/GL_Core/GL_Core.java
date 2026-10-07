package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Watermark;

public final class GL_Core {
    private static final long GL_Watermark_Show = 5_000_000_000L;
    private static final long GL_Watermark_Fade = 1_000_000_000L;
    private static final float GL_Watermark_Alpha = 0.4f;

    private static boolean GL_Shown;
    private static boolean GL_Watermark_Started;
    private static boolean GL_Watermark_Done;
    private static long GL_Watermark_Start;

    private GL_Core() {
    }

    public static synchronized void GL_Marker_Print() {
        if (GL_Shown) {
            return;
        }
        GL_Shown = true;
        System.out.println("[GL_Java] GL_Core active");
    }

    public static void GL_Watermark_Tick() {
        if (GL_Watermark_Done) {
            return;
        }
        long now = System.nanoTime();
        if (!GL_Watermark_Started) {
            GL_Watermark_Started = true;
            GL_Watermark_Start = now;
            return;
        }
        long fading = now - GL_Watermark_Start - GL_Watermark_Show;
        if (fading <= 0) {
            return;
        }
        float left = 1.0f - (float) fading / GL_Watermark_Fade;
        if (left <= 0.0f) {
            Watermark.setAlpha(0.0f);
            GL_Watermark_Done = true;
            return;
        }
        Watermark.setAlpha(GL_Watermark_Alpha * left);
    }
}
