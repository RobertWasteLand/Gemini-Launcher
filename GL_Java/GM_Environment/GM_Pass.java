package GL_Java.GM_Environment;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.ShaderProgram;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.core.textures.TextureFBO;

final class GM_Pass extends TextureDraw.GenericDrawer {
    static final String GM_Name_Rays = "GM_Environment/GM_Shaders_Rays";
    static final String GM_Name_Bright = "GM_Environment/GM_Shaders_Bright";
    static final String GM_Name_Blur = "GM_Environment/GM_Shaders_Blur";
    static final String GM_Name_Depth = "GM_Environment/GM_Shaders_Depth";
    static final String GM_Name_FogSim = "GM_Environment/GM_Shaders_FogSim";
    static final float GM_Bloom_Threshold = 0.92f;
    static final float GM_Ray_Pixels = 7.0f;
    static final int GM_Fog_Size = 512;
    static final float GM_Fog_Jump = 40.0f;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_UNSIGNED_INT = 0x1405;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_UNSIGNED_SHORT = 0x1403;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGBA8 = 0x8058;
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
    private static final int GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;
    private static final int GL_LESS = 0x0201;

    private static ShaderProgram GM_Prog_Rays;
    private static ShaderProgram GM_Prog_Bright;
    private static ShaderProgram GM_Prog_Blur;
    private static ShaderProgram GM_Prog_Depth;
    private static ShaderProgram GM_Prog_FogSim;
    private static int GM_Depth_Tex;
    private static int GM_Depth_Fbo;
    private static int GM_Rays_Tex;
    private static int GM_Rays_Fbo;
    private static int GM_Bright_Tex;
    private static int GM_Bright_Fbo;
    private static int GM_BlurA_Tex;
    private static int GM_BlurA_Fbo;
    private static int GM_BlurB_Tex;
    private static int GM_BlurB_Fbo;
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
    private static int GM_Half_W;
    private static int GM_Half_H;
    private static int GM_Quarter_W;
    private static int GM_Quarter_H;
    private static boolean GM_Ready;
    private static final int[] GM_Viewport = new int[4];

    private static final GM_Shaders.GM_Locs GM_Rays_Locs = new GM_Shaders.GM_Locs();
    private static final GM_Shaders.GM_Locs GM_FogSim_Locs = new GM_Shaders.GM_Locs();
    private static int GM_Rays_Loc_Dir = -1;
    private static int GM_Rays_Loc_Res = -1;
    private static int GM_FogSim_Loc_Prev = -1;
    private static int GM_FogSim_Loc_Sim = -1;
    private static int GM_Bright_Program;
    private static int GM_Blur_Program;
    private static int GM_Bright_Loc_Scene = -1;
    private static int GM_Bright_Loc_Threshold = -1;
    private static int GM_Blur_Loc_Source = -1;
    private static int GM_Blur_Loc_Delta = -1;

    private final GM_Shaders.GM_Frame frame;

    GM_Pass(GM_Shaders.GM_Frame frame) {
        this.frame = frame;
    }

    static int GM_Depth_Texture() {
        return GM_Depth_Tex;
    }

    static int GM_Rays_Texture() {
        return GM_Rays_Tex;
    }

    static int GM_Bloom_Texture() {
        return GM_BlurB_Tex;
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
        GM_Prog_Rays = GM_Program(GM_Name_Rays);
        GM_Prog_Bright = GM_Program(GM_Name_Bright);
        GM_Prog_Blur = GM_Program(GM_Name_Blur);
        GM_Prog_Depth = GM_Program(GM_Name_Depth);
        GM_Prog_FogSim = GM_Program(GM_Name_FogSim);
        if (GM_Prog_Rays == null || GM_Prog_Bright == null || GM_Prog_Blur == null || GM_Prog_Depth == null || GM_Prog_FogSim == null) {
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
            for (int i = 0; i < 2; i++) {
                GM_Fog_Tex[i] = GM_Texture_Make(GM_Fog_Size, GM_Fog_Size, false, true);
                GM_Fog_Fbo[i] = GM_Fbo_Make(GM_Fog_Tex[i], false);
            }
            GM_Fog_Fresh = true;
        }
        GM_Ready = true;
        return true;
    }

    private static void GM_Locations() {
        int rays = GM_Prog_Rays.getShaderID();
        if (rays != GM_Rays_Locs.program) {
            GM_Rays_Locs.GM_Find(rays);
            GM_Rays_Loc_Dir = GL20.glGetUniformLocation(rays, "GM_RayDir");
            GM_Rays_Loc_Res = GL20.glGetUniformLocation(rays, "GM_RayRes");
        }
        int sim = GM_Prog_FogSim.getShaderID();
        if (sim != GM_FogSim_Locs.program) {
            GM_FogSim_Locs.GM_Find(sim);
            GM_FogSim_Loc_Prev = GL20.glGetUniformLocation(sim, "GM_FogRectPrev");
            GM_FogSim_Loc_Sim = GL20.glGetUniformLocation(sim, "GM_FogSim");
        }
        int bright = GM_Prog_Bright.getShaderID();
        if (bright != GM_Bright_Program) {
            GM_Bright_Program = bright;
            GM_Bright_Loc_Scene = GL20.glGetUniformLocation(bright, "GM_Scene");
            GM_Bright_Loc_Threshold = GL20.glGetUniformLocation(bright, "GM_Threshold");
        }
        int blur = GM_Prog_Blur.getShaderID();
        if (blur != GM_Blur_Program) {
            GM_Blur_Program = blur;
            GM_Blur_Loc_Source = GL20.glGetUniformLocation(blur, "GM_Source");
            GM_Blur_Loc_Delta = GL20.glGetUniformLocation(blur, "GM_Delta");
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

    private static int GM_Texture_Make(int width, int height, boolean depth, boolean half) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        if (depth) {
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, (ByteBuffer) null);
        } else if (half) {
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        } else {
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
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
        if (GM_Rays_Fbo != 0) GL30.glDeleteFramebuffers(GM_Rays_Fbo);
        if (GM_Bright_Fbo != 0) GL30.glDeleteFramebuffers(GM_Bright_Fbo);
        if (GM_BlurA_Fbo != 0) GL30.glDeleteFramebuffers(GM_BlurA_Fbo);
        if (GM_BlurB_Fbo != 0) GL30.glDeleteFramebuffers(GM_BlurB_Fbo);
        if (GM_Depth_Tex != 0) GL11.glDeleteTextures(GM_Depth_Tex);
        if (GM_Rays_Tex != 0) GL11.glDeleteTextures(GM_Rays_Tex);
        if (GM_Bright_Tex != 0) GL11.glDeleteTextures(GM_Bright_Tex);
        if (GM_BlurA_Tex != 0) GL11.glDeleteTextures(GM_BlurA_Tex);
        if (GM_BlurB_Tex != 0) GL11.glDeleteTextures(GM_BlurB_Tex);
        GM_Depth_Fbo = GM_Rays_Fbo = GM_Bright_Fbo = GM_BlurA_Fbo = GM_BlurB_Fbo = 0;
        GM_Depth_Tex = GM_Rays_Tex = GM_Bright_Tex = GM_BlurA_Tex = GM_BlurB_Tex = 0;
    }

    private static void GM_Resize(int width, int height) {
        GM_Release();
        GM_W = width;
        GM_H = height;
        GM_Half_W = Math.max(1, width / 2);
        GM_Half_H = Math.max(1, height / 2);
        GM_Quarter_W = Math.max(1, width / 4);
        GM_Quarter_H = Math.max(1, height / 4);
        GM_Depth_Tex = GM_Texture_Make(width, height, true, false);
        GM_Depth_Fbo = GM_Fbo_Make(GM_Depth_Tex, true);
        GM_Rays_Tex = GM_Texture_Make(GM_Half_W, GM_Half_H, false, false);
        GM_Rays_Fbo = GM_Fbo_Make(GM_Rays_Tex, false);
        GM_Bright_Tex = GM_Texture_Make(GM_Half_W, GM_Half_H, false, false);
        GM_Bright_Fbo = GM_Fbo_Make(GM_Bright_Tex, false);
        GM_BlurA_Tex = GM_Texture_Make(GM_Quarter_W, GM_Quarter_H, false, false);
        GM_BlurA_Fbo = GM_Fbo_Make(GM_BlurA_Tex, false);
        GM_BlurB_Tex = GM_Texture_Make(GM_Quarter_W, GM_Quarter_H, false, false);
        GM_BlurB_Fbo = GM_Fbo_Make(GM_BlurB_Tex, false);
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
        if (width != GM_W || height != GM_H) {
            GM_Resize(width, height);
        }
        GM_Locations();
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
        int prevFbo = TextureFBO.getCurrentID();
        int prevProgram = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int prevUnit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        GL11.glGetIntegerv(GL_VIEWPORT, GM_Viewport);
        try {
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, world.getBufferId());
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, GM_Depth_Fbo);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDisable(GL_BLEND);
            GL11.glDisable(GL_SCISSOR_TEST);
            GL11.glDisable(GL_STENCIL_TEST);
            GL11.glDisable(GL_ALPHA_TEST);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL30.glBindVertexArray(GM_Vao);
            GM_Fog_Draw();
            GM_Rays_Draw();
            GM_Bloom_Draw(world.getTexture().getID());
        } finally {
            GL30.glBindVertexArray(prevVao);
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
            GL11.glViewport(GM_Viewport[0], GM_Viewport[1], GM_Viewport[2], GM_Viewport[3]);
            GL20.glUseProgram(prevProgram);
            GL13.glActiveTexture(prevUnit);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            Texture.lastTextureID = -1;
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
            SpriteRenderer.ringBuffer.restoreVbos = true;
        }
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

    private void GM_Rays_Draw() {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, GM_Rays_Fbo);
        GL11.glViewport(0, 0, GM_Half_W, GM_Half_H);
        GL20.glUseProgram(GM_Prog_Rays.getShaderID());
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Depth_Tex);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL20.glUniform1i(GM_Rays_Locs.depth, 0);
        GL20.glUniform1i(GM_Rays_Locs.field, 1);
        GL20.glUniform1i(GM_Rays_Locs.fogTex, 1);
        GM_Shaders.GM_Uniforms(this.frame, null, GM_Rays_Locs);
        float step = GM_Ray_Pixels * this.frame.sunStep * (this.frame.tileScale / 2.0f) / Math.max(0.25f, this.frame.zoom) * 1.5f;
        GL20.glUniform4f(GM_Rays_Loc_Dir, this.frame.sunDirX, this.frame.sunDirY, step, 0.0f);
        GL20.glUniform2f(GM_Rays_Loc_Res, GM_Half_W, GM_Half_H);
        GL11.glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private void GM_Bloom_Draw(int sceneTexture) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, GM_Bright_Fbo);
        GL11.glViewport(0, 0, GM_Half_W, GM_Half_H);
        GL20.glUseProgram(GM_Prog_Bright.getShaderID());
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneTexture);
        GL20.glUniform1i(GM_Bright_Loc_Scene, 0);
        GL20.glUniform1f(GM_Bright_Loc_Threshold, GM_Bloom_Threshold);
        GL11.glDrawArrays(GL_TRIANGLES, 0, 3);
        GL20.glUseProgram(GM_Prog_Blur.getShaderID());
        GL20.glUniform1i(GM_Blur_Loc_Source, 0);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, GM_BlurA_Fbo);
        GL11.glViewport(0, 0, GM_Quarter_W, GM_Quarter_H);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_Bright_Tex);
        GL20.glUniform2f(GM_Blur_Loc_Delta, 1.0f / GM_Quarter_W, 0.0f);
        GL11.glDrawArrays(GL_TRIANGLES, 0, 3);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, GM_BlurB_Fbo);
        GL11.glBindTexture(GL_TEXTURE_2D, GM_BlurA_Tex);
        GL20.glUniform2f(GM_Blur_Loc_Delta, 0.0f, 1.0f / GM_Quarter_H);
        GL11.glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    static void GM_Water_Depth(int count) {
        if (!GM_Ready || GM_Prog_Depth == null) {
            return;
        }
        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        GL20.glUseProgram(GM_Prog_Depth.getShaderID());
        VertexBufferObject.setModelViewProjection(GM_Prog_Depth);
        GL20.glEnableVertexAttribArray(6);
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL_LESS);
        GL12.glDrawRangeElements(GL_TRIANGLES, 0, count * 4, count * 6, GL_UNSIGNED_SHORT, 0L);
        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);
        GL20.glDisableVertexAttribArray(6);
        GL20.glUseProgram(program);
    }
}
