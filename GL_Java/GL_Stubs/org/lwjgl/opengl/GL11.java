package org.lwjgl.opengl;

public final class GL11 {
    public static final int GL_ALL_ATTRIB_BITS = 1048575;
    public static final int GL_ALPHA_TEST = 3008;
    public static final int GL_BLEND = 3042;
    public static final int GL_CLIENT_ALL_ATTRIB_BITS = -1;
    public static final int GL_CULL_FACE = 2884;
    public static final int GL_DEPTH_BUFFER_BIT = 256;
    public static final int GL_DEPTH_COMPONENT = 6402;
    public static final int GL_DEPTH_TEST = 2929;
    public static final int GL_FLOAT = 5126;
    public static final int GL_LINEAR = 9729;
    public static final int GL_NEAREST = 9728;
    public static final int GL_NONE = 0;
    public static final int GL_NO_ERROR = 0;
    public static final int GL_OUT_OF_MEMORY = 1285;
    public static final int GL_RGBA = 6408;
    public static final int GL_RGBA8 = 32856;
    public static final int GL_SCISSOR_TEST = 3089;
    public static final int GL_STENCIL_TEST = 2960;
    public static final int GL_TEXTURE_2D = 3553;
    public static final int GL_TEXTURE_BINDING_2D = 32873;
    public static final int GL_TEXTURE_MAG_FILTER = 10240;
    public static final int GL_TEXTURE_MIN_FILTER = 10241;
    public static final int GL_TEXTURE_WRAP_S = 10242;
    public static final int GL_TEXTURE_WRAP_T = 10243;
    public static final int GL_TRIANGLES = 4;
    public static final int GL_UNSIGNED_BYTE = 5121;
    public static final int GL_UNSIGNED_INT = 5125;
    public static final int GL_VIEWPORT = 2978;

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

    public static void glPushAttrib(int mask) {
    }

    public static void glPopAttrib() {
    }

    public static void glPushClientAttrib(int mask) {
    }

    public static void glPopClientAttrib() {
    }

    public static void glDisable(int target) {
    }

    public static void glDrawArrays(int mode, int first, int count) {
    }

    public static int glGetError() {
        return 0;
    }

    public static void glClear(int mask) {
    }

    public static void glClearDepth(double depth) {
    }
}
