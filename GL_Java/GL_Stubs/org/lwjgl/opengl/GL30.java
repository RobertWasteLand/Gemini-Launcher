package org.lwjgl.opengl;

public final class GL30 {
    public static final int GL_COLOR_ATTACHMENT0 = 36064;
    public static final int GL_DEPTH_ATTACHMENT = 36096;
    public static final int GL_DRAW_FRAMEBUFFER = 36009;
    public static final int GL_FRAMEBUFFER = 36160;
    public static final int GL_FRAMEBUFFER_COMPLETE = 36053;
    public static final int GL_READ_FRAMEBUFFER = 36008;
    public static final int GL_RGBA16F = 34842;
    public static final int GL_VERTEX_ARRAY_BINDING = 34229;

    public static int glGenFramebuffers() {
        return 0;
    }

    public static void glDeleteFramebuffers(int framebuffer) {
    }

    public static void glBindFramebuffer(int target, int framebuffer) {
    }

    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
    }

    public static int glCheckFramebufferStatus(int target) {
        return 0;
    }

    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
    }

    public static int glGenVertexArrays() {
        return 0;
    }

    public static void glBindVertexArray(int array) {
    }
}
