package GL_Java.GL_Core;

import org.lwjgl.opengl.GL11;
import zombie.IndieGL;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.ui.UIManager;

final class GL_Intro_Draw {
    static final float GL_Stage_W = 1920.0f;
    static final float GL_Stage_H = 1080.0f;
    static final float GL_Orange_R = 1.0f;
    static final float GL_Orange_G = 0.5647f;
    static final float GL_Orange_B = 0.0f;

    static int GL_Screen_W;
    static int GL_Screen_H;
    static float GL_Stage_Scale;
    static float GL_Stage_X;
    static float GL_Stage_Y;
    private static boolean GL_Ui_Fbo;

    private GL_Intro_Draw() {
    }

    static void GL_Frame_Begin() {
        Core core = Core.getInstance();
        core.StartFrame();
        core.EndFrame();
        GL_Ui_Fbo = UIManager.useUiFbo;
        UIManager.useUiFbo = false;
        core.StartFrameUI();
        GL_Measure();
        GL_Fill(0.0f, 0.0f, 0.0f);
        GL_Blend_Normal();
    }

    static void GL_Measure() {
        Core core = Core.getInstance();
        GL_Screen_W = core.getScreenWidth();
        GL_Screen_H = core.getScreenHeight();
        GL_Stage_Scale = GL_Sharp(Math.min(GL_Screen_W / GL_Stage_W, GL_Screen_H / GL_Stage_H));
        GL_Stage_X = Math.round((GL_Screen_W - GL_Stage_W * GL_Stage_Scale) / 2.0f);
        GL_Stage_Y = Math.round((GL_Screen_H - GL_Stage_H * GL_Stage_Scale) / 2.0f);
    }

    static float GL_Sharp(float fit) {
        return fit >= 1.0f ? (float) Math.floor(fit) : fit;
    }

    static void GL_Frame_Finish() {
        try {
            Core.getInstance().EndFrameUI();
        } finally {
            UIManager.useUiFbo = GL_Ui_Fbo;
        }
    }

    static void GL_Blend_Normal() {
        IndieGL.glEnable(GL11.GL_BLEND);
        IndieGL.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void GL_Blend_Add() {
        IndieGL.glEnable(GL11.GL_BLEND);
        IndieGL.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    static void GL_Fill(float r, float g, float b) {
        Core core = Core.getInstance();
        SpriteRenderer.instance.renderi(null, 0, 0, core.getScreenWidth(), core.getScreenHeight(), r, g, b, 1.0f, null);
    }

    static void GL_Cover_Bands(int from, int to, int bands) {
        Core core = Core.getInstance();
        int height = core.getScreenHeight();
        int band = (height + bands - 1) / bands;
        int top = Math.min(height, from * band);
        int bottom = Math.min(height, to * band);
        if (bottom > top) {
            SpriteRenderer.instance.renderi(null, 0, top, core.getScreenWidth(), bottom - top, 0.0f, 0.0f, 0.0f, 1.0f, null);
        }
    }

    static void GL_Slide(Texture texture, float alpha) {
        if (alpha <= 0.0f || !texture.isReady()) {
            return;
        }
        float scale = GL_Sharp(Math.min(GL_Screen_W / (float) texture.getWidth(), GL_Screen_H / (float) texture.getHeight()));
        float w = texture.getWidth() * scale;
        float h = texture.getHeight() * scale;
        SpriteRenderer.instance.render(texture, Math.round((GL_Screen_W - w) / 2.0f), Math.round((GL_Screen_H - h) / 2.0f), w, h, 1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha), null);
    }

    static void GL_Piece(Texture texture, float stageX, float stageY, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        float x = GL_Stage_X + stageX * GL_Stage_Scale;
        float y = GL_Stage_Y + stageY * GL_Stage_Scale;
        SpriteRenderer.instance.render(texture, x, y, texture.getWidth() * GL_Stage_Scale, texture.getHeight() * GL_Stage_Scale, 1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha), null);
    }

    static void GL_Piece_Rows(Texture texture, float stageX, float stageY, int rowFrom, int rowTo, float r, float g, float b, float alpha) {
        if (alpha <= 0.0f || rowTo <= rowFrom) {
            return;
        }
        float rows = texture.getHeight();
        float v0 = texture.getYStart() + (texture.getYEnd() - texture.getYStart()) * (rowFrom / rows);
        float v1 = texture.getYStart() + (texture.getYEnd() - texture.getYStart()) * (rowTo / rows);
        float u0 = texture.getXStart();
        float u1 = texture.getXEnd();
        float x = GL_Stage_X + stageX * GL_Stage_Scale;
        float y = GL_Stage_Y + (stageY + rowFrom) * GL_Stage_Scale;
        float w = texture.getWidth() * GL_Stage_Scale;
        float h = (rowTo - rowFrom) * GL_Stage_Scale;
        SpriteRenderer.instance.render(texture, x, y, w, h, r, g, b, Math.min(1.0f, alpha), u0, v0, u1, v0, u1, v1, u0, v1);
    }

    static void GL_Capsule(Texture texture, float left, float top, float right, float bottom, float radius, float r, float g, float b, float alpha) {
        if (alpha <= 0.0f || radius <= 0.0f) {
            return;
        }
        float half = (bottom - top) * 0.5f;
        float middle = top + half;
        float from = left + half;
        float to = Math.max(from, right - half);
        if (to - from < 1.0f) {
            from = (left + right) * 0.5f;
            to = from;
        }
        float scale = GL_Stage_Scale;
        float y = GL_Stage_Y + (middle - radius) * scale;
        float h = radius * 2.0f * scale;
        float x0 = GL_Stage_X + (from - radius) * scale;
        float x1 = GL_Stage_X + from * scale;
        float x2 = GL_Stage_X + to * scale;
        float u0 = texture.getXStart();
        float u2 = texture.getXEnd();
        float u1 = (u0 + u2) * 0.5f;
        float v0 = texture.getYStart();
        float v1 = texture.getYEnd();
        float a = Math.min(1.0f, alpha);
        float cap = radius * scale;
        SpriteRenderer.instance.render(texture, x0, y, cap, h, r, g, b, a, u0, v0, u1, v0, u1, v1, u0, v1);
        if (x2 > x1) {
            SpriteRenderer.instance.render(texture, x1, y, x2 - x1, h, r, g, b, a, u1, v0, u1, v0, u1, v1, u1, v1);
        }
        SpriteRenderer.instance.render(texture, x2, y, cap, h, r, g, b, a, u1, v0, u2, v0, u2, v1, u1, v1);
    }

    static void GL_Sprite(Texture texture, int x, int y, int unit, boolean mirror) {
        float w = texture.getWidth() * unit;
        float h = texture.getHeight() * unit;
        if (!mirror) {
            SpriteRenderer.instance.render(texture, x, y, w, h, 1.0f, 1.0f, 1.0f, 1.0f, null);
            return;
        }
        float u0 = texture.getXStart();
        float u1 = texture.getXEnd();
        float v0 = texture.getYStart();
        float v1 = texture.getYEnd();
        SpriteRenderer.instance.render(texture, x, y, w, h, 1.0f, 1.0f, 1.0f, 1.0f, u1, v0, u0, v0, u0, v1, u1, v1);
    }
}
