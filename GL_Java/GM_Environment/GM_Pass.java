package GL_Java.GM_Environment;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.ShaderProgram;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.core.textures.TextureFBO;

final class GM_Pass extends TextureDraw.GenericDrawer {
    static final String GM_Name_FogSim = "GM_Environment/GM_Shaders_FogSim";
    static final int GM_Fog_Size = 512;
    static final float GM_Fog_Jump = 40.0f;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_UNSIGNED_INT = 0x1405;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGBA16F = 0x881A;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DEPTH_BUFFER_BIT = 0x0100;
    private static final int GL_NONE = 0;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_VIEWPORT = 0x0BA2;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_STENCIL_TEST = 0x0B90;
    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_ALL_ATTRIB_BITS = 0xFFFFF;
    private static final int GL_NO_ERROR = 0;
    private static final int GL_OUT_OF_MEMORY = 0x0505;
    private static final int GM_Error_Max = 32;
    private static final int GM_Margin = 64;
    private static int GM_X0;
    private static int GM_Y0;
    private static int GM_X1;
    private static int GM_Y1;
    private static final int GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;

    private static ShaderProgram GM_Prog_FogSim;
    private static int GM_Depth_Tex;
    private static int GM_Depth_Fbo;
    private static final int[] GM_Fog_Tex = new int[2];
    private static final int[] GM_Fog_Fbo = new int[2];
    private static int GM_Fog_Current;
    private static float GM_Fog_Prev_X;
    private static float GM_Fog_Prev_Y;
    private static volatile boolean GM_Fog_Fresh = true;
    private static int GM_Vao;
    private static int GM_Vbo;
    private static int GM_W;
    private static int GM_H;
    private static boolean GM_Ready;
    private static final int[] GM_Viewport = new int[4];

    private static final GM_Shaders.GM_Locs GM_FogSim_Locs = new GM_Shaders.GM_Locs();
    private static int GM_FogSim_Loc_Prev = -1;
    private static int GM_FogSim_Loc_Sim = -1;

    private GM_Shaders.GM_Frame frame;

    GM_Pass GM_Use(GM_Shaders.GM_Frame frame) {
        this.frame = frame;
        return this;
    }

    static int GM_Depth_Texture() {
        return GM_Depth_Tex;
    }

    static int GM_Fog_Texture() {
        return GM_Fog_Tex[GM_Fog_Current];
    }

    static int GM_Width() {
        return GM_W;
    }

    static int GM_Height() {
        return GM_H;
    }

    static void GM_Fog_Reset() {
        GM_Fog_Fresh = true;
    }

    static boolean GM_Create() {
        GM_Prog_FogSim = GM_Program(GM_Name_FogSim);
        if (GM_Prog_FogSim == null) {
            return false;
        }
        GM_Locations();
        if (GM_Vao == 0) {
            int prevVao = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
            GM_Vao = GL30.glGenVertexArrays();
            GM_Vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(GM_Vao);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, GM_Vbo);
            GL15.glBufferData(GL_ARRAY_BUFFER, new float[] {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f}, GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
            GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
            SpriteRenderer.ringBuffer.restoreVbos = true;
        }
        if (GM_Fog_Tex[0] == 0) {
            GM_Memory_Clear();
            for (int i = 0; i < 2; i++) {
                GM_Fog_Tex[i] = GM_Texture_Make(GM_Fog_Size, GM_Fog_Size, false);
                GM_Fog_Fbo[i] = GM_Fbo_Make(GM_Fog_Tex[i], false);
            }
            GM_Memory_Check("fog");
            GM_Fog_Fresh = true;
        }
        GM_Ready = true;
        return true;
    }

    private static int GM_Id(ShaderProgram program, String name) {
        int id = program.getShaderID();
        if (id == 0) {
            throw new IllegalStateException(name + " stopped compiling");
        }
        return id;
    }

    private static void GM_Locations() {
        int sim = GM_Id(GM_Prog_FogSim, GM_Name_FogSim);
        if (sim != GM_FogSim_Locs.program) {
            GM_FogSim_Locs.GM_Find(sim);
            GM_FogSim_Loc_Prev = GL20.glGetUniformLocation(sim, "GM_FogRectPrev");
            GM_FogSim_Loc_Sim = GL20.glGetUniformLocation(sim, "GM_FogSim");
        }
    }

    private static ShaderProgram GM_Program(String name) {
        ShaderProgram program = ShaderProgram.createShaderProgram(name, false, false, true);
        if (program == null || !program.isCompiled()) {
            System.out.println("[GL_Java] GM_Environment shaders: " + name + " did not compile");
            return null;
        }
        return program;
    }

    private static int GM_Texture_Make(int width, int height, boolean depth) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        if (depth) {
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, (ByteBuffer) null);
        } else {
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        }
        int filter = depth ? GL_NEAREST : GL_LINEAR;
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int GM_Fbo_Make(int texture, boolean depth) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        if (depth) {
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, texture, 0);
            GL11.glDrawBuffer(GL_NONE);
            GL11.glReadBuffer(GL_NONE);
        } else {
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        }
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer status " + status);
        }
        return fbo;
    }

    private static void GM_Release() {
        if (GM_Depth_Fbo != 0) GL30.glDeleteFramebuffers(GM_Depth_Fbo);
        if (GM_Depth_Tex != 0) GL11.glDeleteTextures(GM_Depth_Tex);
        GM_Depth_Fbo = 0;
        GM_Depth_Tex = 0;
    }

    private static void GM_Memory_Clear() {
        for (int i = 0; i < GM_Error_Max && GL11.glGetError() != GL_NO_ERROR; i++) {
        }
    }

    private static void GM_Memory_Check(String what) {
        boolean out = false;
        for (int i = 0; i < GM_Error_Max; i++) {
            int error = GL11.glGetError();
            if (error == GL_NO_ERROR) {
                break;
            }
            out |= error == GL_OUT_OF_MEMORY;
        }
        if (out || GM_Settings.GM_Test("memory")) {
            throw new IllegalStateException("out of video memory while making the " + what + " images");
        }
    }

    private static long GM_Bytes(int width, int height) {
        long full = (long) width * height * 4L;
        long fog = (long) GM_Fog_Size * GM_Fog_Size * 8L * 2L;
        long field = (long) GM_Field.GM_Size * GM_Field.GM_Size * 4L * 2L;
        return full + fog + field;
    }

    private static void GM_Resize(int width, int height) {
        GM_Release();
        GM_Memory_Clear();
        GM_W = width;
        GM_H = height;
        GM_Depth_Tex = GM_Texture_Make(width, height, true);
        GM_Depth_Fbo = GM_Fbo_Make(GM_Depth_Tex, true);
        if (GM_Settings.GM_Test("resize")) {
            throw new IllegalStateException("test failure: resize");
        }
        GM_Clear_Depth(GM_Depth_Fbo);
        GM_Memory_Check("screen effect");
        System.out.println("[GL_Java] GM_Environment effects use " + (GM_Bytes(width, height) + 524288L) / 1048576L + " MB of video memory at " + width + "x" + height);
    }

    @Override
    public void render() {
        if (!GM_Ready || GM_Shaders.GM_Off()) {
            return;
        }
        try {
            GM_Run();
        } catch (Throwable t) {
            GM_Shaders.GM_Fail("render passes failed", t);
        }
    }

    private void GM_Run() {
        TextureFBO world = Core.getInstance().getOffscreenBuffer(this.frame.player);
        if (world == null || world.getTexture() == null) {
            return;
        }
        int width = world.getWidth();
        int height = world.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        long start = GM_Timing.GM_Cpu_Start();
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
        GM_Timing.GM_Cpu_Push(start);
        long query = GM_Timing.GM_Cpu_Start();
        int prevFbo = TextureFBO.getCurrentID();
        int prevProgram = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int prevUnit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        GL11.glGetIntegerv(GL_VIEWPORT, GM_Viewport);
        GM_Timing.GM_Cpu_Query(query);
        try {
            if (width != GM_W || height != GM_H || GM_Settings.GM_Test("resize")) {
                try {
                    GM_Resize(width, height);
                } catch (Throwable t) {
                    GM_Release();
                    GM_W = 0;
                    GM_H = 0;
                    throw t;
                }
            }
            GM_Locations();
            GM_Used(width, height);
            GM_Timing.GM_Stamp(GM_Timing.GM_Begin);
            GL11.glDisable(GL_SCISSOR_TEST);
            if (this.frame.depth) {
                GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, world.getBufferId());
                GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, GM_Depth_Fbo);
                GL30.glBlitFramebuffer(GM_X0, GM_Y0, GM_X1, GM_Y1, GM_X0, GM_Y0, GM_X1, GM_Y1, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
            }
            GM_Timing.GM_Stamp(GM_Timing.GM_Blit);
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDisable(GL_BLEND);
            GL11.glDisable(GL_STENCIL_TEST);
            GL11.glDisable(GL_ALPHA_TEST);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL30.glBindVertexArray(GM_Vao);
            if (GM_Settings.GM_Test("pass")) {
                throw new IllegalStateException("test failure: pass");
            }
            if (this.frame.fx[GM_Shaders.GM_Fx_Fog] > 0.5f) {
                GM_Fog_Draw();
            }
            GM_Timing.GM_Stamp(GM_Timing.GM_Fog);
        } finally {
            GL30.glBindVertexArray(prevVao);
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
            GL11.glViewport(GM_Viewport[0], GM_Viewport[1], GM_Viewport[2], GM_Viewport[3]);
            GL20.glUseProgram(prevProgram);
            GL13.glActiveTexture(prevUnit);
            long pop = GM_Timing.GM_Cpu_Start();
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GM_Timing.GM_Cpu_Pop(pop);
            Texture.lastTextureID = -1;
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
            SpriteRenderer.ringBuffer.restoreVbos = true;
            GM_Timing.GM_Cpu_Pass(start);
        }
    }

    private void GM_Used(int width, int height) {
        int x0 = Math.max(0, this.frame.usedX - GM_Margin);
        int y0 = Math.max(0, this.frame.usedY - GM_Margin);
        int x1 = Math.min(width, this.frame.usedX + this.frame.usedW + GM_Margin);
        int y1 = Math.min(height, this.frame.usedY + this.frame.usedH + GM_Margin);
        if (this.frame.usedW <= 0 || this.frame.usedH <= 0 || x1 <= x0 || y1 <= y0) {
            x0 = 0;
            y0 = 0;
            x1 = width;
            y1 = height;
        }
        GM_X0 = x0;
        GM_Y0 = y0;
        GM_X1 = x1;
        GM_Y1 = y1;
    }

    private static void GM_Clear_Depth(int fbo) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glDepthMask(true);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_DEPTH_BUFFER_BIT);
    }

    private void GM_Fog_Draw() {
        int next = 1 - GM_Fog_Current;
        boolean reset = GM_Fog_Fresh
            || Math.abs(this.frame.fogX - GM_Fog_Prev_X) > GM_Fog_Jump
            || Math.abs(this.frame.fogY - GM_Fog_Prev_Y) > GM_Fog_Jump;
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, GM_Fog_Fbo[next]);
        GL11.glViewport(0, 0, GM_Fog_Size, GM_Fog_Size);
        GL20.glUseProgram(GM_Prog_FogSim.getShaderID());
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Fog_Tex[GM_Fog_Current]);
        GL20.glUniform1i(GM_FogSim_Locs.fogTex, 0);
        GM_Shaders.GM_Uniforms(this.frame, null, GM_FogSim_Locs);
        GL20.glUniform4f(GM_FogSim_Loc_Prev, GM_Fog_Prev_X, GM_Fog_Prev_Y, 1.0f / GM_Shaders.GM_Fog_Window, GM_Shaders.GM_Fog_Window);
        GL20.glUniform4f(GM_FogSim_Loc_Sim, this.frame.dt, 0.0f, reset ? 1.0f : 0.0f, GM_Fog_Size);
        GL11.glDrawArrays(GL_TRIANGLES, 0, 3);
        GM_Fog_Current = next;
        GM_Fog_Prev_X = this.frame.fogX;
        GM_Fog_Prev_Y = this.frame.fogY;
        GM_Fog_Fresh = false;
    }

}
