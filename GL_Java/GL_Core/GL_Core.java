package GL_Java.GL_Core;

public final class GL_Core {
    private static boolean GL_Shown;

    private GL_Core() {
    }

    public static synchronized void GL_Marker_Print() {
        if (GL_Shown) {
            return;
        }
        GL_Shown = true;
        System.out.println("[GL_Java] GL_Core active");
    }
}
