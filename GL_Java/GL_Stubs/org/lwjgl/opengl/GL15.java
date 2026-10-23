package org.lwjgl.opengl;

public final class GL15 {
    public static final int GL_ARRAY_BUFFER = 34962;
    public static final int GL_QUERY_RESULT = 34918;
    public static final int GL_QUERY_RESULT_AVAILABLE = 34919;
    public static final int GL_STATIC_DRAW = 35044;

    public static int glGenBuffers() {
        return 0;
    }

    public static void glBindBuffer(int target, int buffer) {
    }

    public static void glBufferData(int target, float[] data, int usage) {
    }

    public static void glGenQueries(int[] ids) {
    }

    public static int glGetQueryObjecti(int id, int pname) {
        return 0;
    }
}
