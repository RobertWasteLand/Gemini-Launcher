package org.lwjgl.opengl;

public final class GL11 {
    public static int glGenTextures() {
        return 0;
    }

    public static void glBindTexture(int target, int texture) {
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
    }

    public static void glTexParameteri(int target, int pname, int param) {
    }

    public static int glGetInteger(int pname) {
        return 0;
    }

    public static void glGetIntegerv(int pname, int[] params) {
    }

    public static void glDeleteTextures(int texture) {
    }

    public static void glDrawBuffer(int buf) {
    }

    public static void glReadBuffer(int src) {
    }

    public static void glViewport(int x, int y, int w, int h) {
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
    }

    public static void glDepthMask(boolean flag) {
    }

    public static void glDepthFunc(int func) {
    }

    public static void glPushAttrib(int mask) {
    }

    public static void glPopAttrib() {
    }

    public static void glPushClientAttrib(int mask) {
    }

    public static void glPopClientAttrib() {
    }

    public static void glEnable(int target) {
    }

    public static void glDisable(int target) {
    }

    public static void glDrawArrays(int mode, int first, int count) {
    }
}
