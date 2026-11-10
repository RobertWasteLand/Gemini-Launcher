package org.lwjgl.opengl;

public final class GL20 {
    public static final int GL_CURRENT_PROGRAM = 35725;

    public static int glGetUniformLocation(int program, CharSequence name) {
        return -1;
    }

    public static void glUniform1i(int location, int v0) {
    }

    public static void glUniform2f(int location, float v0, float v1) {
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
    }

    public static void glUniformMatrix4fv(int location, boolean transpose, float[] value) {
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        return 0;
    }

    public static void glUniform1f(int location, float v0) {
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
    }

    public static void glUniform1fv(int location, float[] value) {
    }

    public static void glUniform3fv(int location, float[] value) {
    }

    public static void glUniform4fv(int location, float[] value) {
    }

    public static void glUseProgram(int program) {
    }

    public static void glEnableVertexAttribArray(int index) {
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
    }
}
