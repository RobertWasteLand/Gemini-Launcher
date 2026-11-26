package GL_Java.GM_Particles;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.ShaderProgram;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.core.textures.TextureFBO;

final class GM_Particles_Draw extends TextureDraw.GenericDrawer {
    static final String GM_Shader = "GM_Particles/GM_Particles";
    private static final String[] GM_Uniforms = {
        "GM_Matrix", "GM_Size", "GM_Color0", "GM_Color1", "GM_Color2", "GM_Colors", "GM_Alpha", "GM_Fadein",
        "GM_Picture", "GM_Has_Picture", "GM_UV", "GM_Glow", "GM_Depth", "GM_Fbo", "GM_Soft",
    };
    private static final int GM_Matrix = 0;
    private static final int GM_Size = 1;
    private static final int GM_Color0 = 2;
    private static final int GM_Color1 = 3;
    private static final int GM_Color2 = 4;
    private static final int GM_Colors = 5;
    private static final int GM_Alpha = 6;
    private static final int GM_Fadein = 7;
    private static final int GM_Picture = 8;
    private static final int GM_Has_Picture = 9;
    private static final int GM_UV = 10;
    private static final int GM_Glow = 11;
    private static final int GM_Depth = 12;
    private static final int GM_Fbo = 13;
    private static final int GM_Soft = 14;
    private static final int[] GM_Locs = new int[GM_Uniforms.length];
    private static final int GM_Timer_Slots = 8;
    private static final long GM_Report_Every = 5_000_000_000L;

    private static ShaderProgram GM_Program;
    private static int GM_Located;
    private static int GM_Vao;
    private static int GM_Quad;
    private static int GM_Instances;
    private static FloatBuffer GM_Upload;
    private static int GM_Depth_Tex;
    private static int GM_Depth_Fbo;
    private static int GM_W;
    private static int GM_H;
    private static boolean GM_Ready;
    private static final int[] GM_Queries = new int[GM_Timer_Slots * 2];
    private static final boolean[] GM_Queried = new boolean[GM_Timer_Slots];
    private static int GM_Query_Slot;
    private static long GM_Report_At;
    private static long GM_Frames;
    private static long GM_Alive_Sum;
    private static long GM_Update_Sum;
    private static long GM_Gpu_Sum;
    private static long GM_Gpu_Count;

    GM_Particles_Frame frame;

    GM_Particles_Draw GM_Use(GM_Particles_Frame frame) {
        this.frame = frame;
        return this;
    }

    @Override
    public void render() {
        if (GM_Particles.GM_Is_Off() || this.frame == null) {
            return;
        }
        try {
            if (GM_Ready || GM_Create()) {
                this.GM_Run();
            }
        } catch (Throwable t) {
            GM_Particles.GM_Off("drawing failed (" + t + ")");
        }
    }

    private static boolean GM_Create() {
        if (Core.getInstance().getUseOpenGL21()) {
            GM_Particles.GM_Off("this graphics card or driver does not support OpenGL 3.3");
            return false;
        }
        GM_Program = ShaderProgram.createShaderProgram(GM_Shader, false, false, true);
        if (GM_Program == null || !GM_Program.isCompiled()) {
            GM_Particles.GM_Off(GM_Shader + " did not compile");
            return false;
        }
        GM_Vao = GL30.glGenVertexArrays();
        GM_Quad = GL15.glGenBuffers();
        GM_Instances = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, GM_Quad);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f}, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        SpriteRenderer.ringBuffer.restoreVbos = true;
        GM_Ready = true;
        return true;
    }

    private static void GM_Locations(int program) {
        if (program == GM_Located) {
            return;
        }
        for (int i = 0; i < GM_Uniforms.length; i++) {
            GM_Locs[i] = GL20.glGetUniformLocation(program, GM_Uniforms[i]);
        }
        GL30.glBindVertexArray(GM_Vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, GM_Quad);
        GM_Attribute(program, "aCorner", 2, 0, 0, 0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, GM_Instances);
        GM_Attribute(program, "aPlace", 3, 32, 0, 1);
        GM_Attribute(program, "aLife", 2, 32, 12, 1);
        GM_Attribute(program, "aTint", 3, 32, 20, 1);
        GM_Located = program;
    }

    private static void GM_Attribute(int program, String name, int size, int stride, long offset, int divisor) {
        int location = GL20.glGetAttribLocation(program, name);
        if (location < 0) {
            return;
        }
        GL20.glEnableVertexAttribArray(location);
        GL20.glVertexAttribPointer(location, size, GL11.GL_FLOAT, false, stride, offset);
        GL33.glVertexAttribDivisor(location, divisor);
    }

    private static void GM_Resize(int width, int height) {
        if (GM_Depth_Fbo != 0) {
            GL30.glDeleteFramebuffers(GM_Depth_Fbo);
            GL11.glDeleteTextures(GM_Depth_Tex);
        }
        GM_Depth_Fbo = 0;
        GM_Depth_Tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, GM_Depth_Tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GM_Depth_Fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, GM_Depth_Fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, GM_Depth_Tex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("depth copy framebuffer status " + status);
        }
        GM_W = width;
        GM_H = height;
    }

    private void GM_Run() {
        GM_Particles_Frame f = this.frame;
        TextureFBO world = Core.getInstance().getOffscreenBuffer(f.player);
        if (world == null) {
            return;
        }
        int width = world.getWidth();
        int height = world.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int program = GM_Program.getShaderID();
        if (program == 0) {
            throw new IllegalStateException(GM_Shader + " stopped compiling");
        }
        boolean timing = Core.debug;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        int prevFbo = TextureFBO.getCurrentID();
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            if (timing) {
                GM_Timer(true);
            }
            if (width != GM_W || height != GM_H) {
                GM_Resize(width, height);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, GM_Depth_Fbo);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL20.glUseProgram(program);
            GM_Locations(program);
            GL30.glBindVertexArray(GM_Vao);
            GL20.glUniformMatrix4fv(GM_Locs[GM_Matrix], false, f.matrix);
            GL20.glUniform1i(GM_Locs[GM_Picture], 0);
            GL20.glUniform1i(GM_Locs[GM_Depth], 1);
            GL20.glUniform2f(GM_Locs[GM_Fbo], width, height);
            GL20.glUniform1f(GM_Locs[GM_Soft], f.soft);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, GM_Depth_Tex);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, GM_Instances);
            for (int b = 0; b < f.batches; b++) {
                GM_Batch(f, b);
            }
            if (timing) {
                GM_Timer(false);
                GM_Report(f);
            }
        } finally {
            GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL20.glUseProgram(prevProgram);
            GL13.glActiveTexture(prevUnit);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            Texture.lastTextureID = -1;
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
            SpriteRenderer.ringBuffer.restoreVbos = true;
        }
    }

    private static void GM_Batch(GM_Particles_Frame f, int b) {
        GM_Particles_Def d = f.defs[b];
        Texture picture = f.pictures[b];
        int floats = f.counts[b] * 8;
        if (GM_Upload == null || GM_Upload.capacity() < floats) {
            GM_Upload = ByteBuffer.allocateDirect(Math.max(floats, 4096) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        GM_Upload.clear();
        GM_Upload.put(f.data[b], 0, floats);
        GM_Upload.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, GM_Upload, GL15.GL_STREAM_DRAW);
        GL20.glUniform2f(GM_Locs[GM_Size], d.sizeStart, d.sizeEnd);
        GL20.glUniform3f(GM_Locs[GM_Color0], d.colors[0], d.colors[1], d.colors[2]);
        GL20.glUniform3f(GM_Locs[GM_Color1], d.colors[3], d.colors[4], d.colors[5]);
        GL20.glUniform3f(GM_Locs[GM_Color2], d.colors[6], d.colors[7], d.colors[8]);
        GL20.glUniform1f(GM_Locs[GM_Colors], d.colorCount);
        GL20.glUniform1f(GM_Locs[GM_Alpha], d.alpha);
        GL20.glUniform1f(GM_Locs[GM_Fadein], d.fadein);
        GL20.glUniform1f(GM_Locs[GM_Glow], d.glow ? 1.0f : 0.0f);
        if (picture != null) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, picture.getID());
            GL20.glUniform1f(GM_Locs[GM_Has_Picture], 1.0f);
            GL20.glUniform4f(GM_Locs[GM_UV], picture.getXStart(), picture.getYStart(), picture.getXEnd(), picture.getYEnd());
        } else {
            GL20.glUniform1f(GM_Locs[GM_Has_Picture], 0.0f);
        }
        GL11.glBlendFunc(GL11.GL_ONE, d.glow ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, f.counts[b]);
    }

    private static void GM_Timer(boolean begin) {
        int slot = GM_Query_Slot;
        if (begin) {
            if (GM_Queries[0] == 0) {
                GL15.glGenQueries(GM_Queries);
            }
            if (GM_Queried[slot] && GL15.glGetQueryObjecti(GM_Queries[slot * 2 + 1], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                GM_Gpu_Sum += GL33.glGetQueryObjecti64(GM_Queries[slot * 2 + 1], GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjecti64(GM_Queries[slot * 2], GL15.GL_QUERY_RESULT);
                GM_Gpu_Count++;
            }
            GM_Queried[slot] = false;
            GL33.glQueryCounter(GM_Queries[slot * 2], GL33.GL_TIMESTAMP);
        } else {
            GL33.glQueryCounter(GM_Queries[slot * 2 + 1], GL33.GL_TIMESTAMP);
            GM_Queried[slot] = true;
            GM_Query_Slot = (slot + 1) % GM_Timer_Slots;
        }
    }

    private static void GM_Report(GM_Particles_Frame f) {
        GM_Frames++;
        GM_Alive_Sum += f.alive;
        GM_Update_Sum += f.updateNanos;
        long now = System.nanoTime();
        if (GM_Report_At == 0) {
            GM_Report_At = now;
        }
        if (now - GM_Report_At < GM_Report_Every) {
            return;
        }
        String draw = GM_Gpu_Count > 0 ? String.format(Locale.ROOT, "%.2f", GM_Gpu_Sum / 1.0e6 / GM_Gpu_Count) : "n/a";
        System.out.println(String.format(Locale.ROOT, "[GL_Java] GM_Particles: %d alive, update %.2f ms, draw %s ms",
            GM_Alive_Sum / GM_Frames, GM_Update_Sum / 1.0e6 / GM_Frames, draw));
        GM_Report_At = now;
        GM_Frames = 0;
        GM_Alive_Sum = 0;
        GM_Update_Sum = 0;
        GM_Gpu_Sum = 0;
        GM_Gpu_Count = 0;
    }
}
