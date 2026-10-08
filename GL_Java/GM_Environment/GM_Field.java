package GL_Java.GM_Environment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;

final class GM_Field {
    static final int GM_Size = 160;
    static final int GM_Fetch_Radius = 6;
    static final float GM_Range = 8.0f;
    static final int GM_Rebuild_Ticks = 120;
    static final float GM_Rebuild_Move = 24.0f;
    static final int GM_Unit = 3;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;

    static final class GM_Data {
        final int x0;
        final int y0;
        final ByteBuffer pixels;

        GM_Data(int x0, int y0, ByteBuffer pixels) {
            this.x0 = x0;
            this.y0 = y0;
            this.pixels = pixels;
        }
    }

    private static final AtomicReference<GM_Data> GM_Pending = new AtomicReference<>();
    private static final ByteBuffer[] GM_Buffers = new ByteBuffer[3];
    private static int GM_Buffer_Next;
    private static final float[] GM_Dist = new float[GM_Size * GM_Size];
    private static final float[] GM_Corner = new float[GM_Size * GM_Size];
    private static final float[] GM_Scratch = new float[GM_Size * GM_Size];
    private static final float[] GM_Fetch = new float[GM_Size * GM_Size];
    private static int GM_Ticks;
    private static int GM_Built_Tick = Integer.MIN_VALUE;
    private static float GM_Center_X;
    private static float GM_Center_Y;
    private static boolean GM_Built;

    private static GM_Data GM_Current;
    private static int GM_Texture;
    private static int GM_Program;
    private static int GM_Loc_Field = -1;
    private static int GM_Loc_Rect = -1;
    private static int GM_Loc_On = -1;
    private static int GM_Loc_Toon = -1;
    private static int GM_Loc_Tile = -1;

    private GM_Field() {
    }

    static void GM_Tick() {
        GM_Ticks++;
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null) {
            return;
        }
        float px = player.getX();
        float py = player.getY();
        if (GM_Built && GM_Ticks - GM_Built_Tick < GM_Rebuild_Ticks && Math.abs(px - GM_Center_X) < GM_Rebuild_Move && Math.abs(py - GM_Center_Y) < GM_Rebuild_Move) {
            return;
        }
        IsoWorld world = IsoWorld.instance;
        if (world == null || world.currentCell == null) {
            return;
        }
        GM_Build(world.currentCell, (int) Math.floor(px), (int) Math.floor(py));
        GM_Built = true;
        GM_Built_Tick = GM_Ticks;
        GM_Center_X = px;
        GM_Center_Y = py;
    }

    private static void GM_Build(IsoCell cell, int cx, int cy) {
        int s = GM_Size;
        int x0 = cx - s / 2;
        int y0 = cy - s / 2;
        float far = GM_Range * 4.0f;
        float[] d = GM_Dist;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                IsoGridSquare square = cell.getGridSquare(x0 + i, y0 + j, 0);
                boolean water = square == null || square.has(IsoFlagType.water);
                d[j * s + i] = water ? far : 0.0f;
            }
        }
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                int k = j * s + i;
                float v = d[k];
                if (v == 0.0f) {
                    continue;
                }
                if (i > 0) {
                    v = Math.min(v, d[k - 1] + 1.0f);
                }
                if (j > 0) {
                    v = Math.min(v, d[k - s] + 1.0f);
                    if (i > 0) {
                        v = Math.min(v, d[k - s - 1] + 1.4142f);
                    }
                    if (i < s - 1) {
                        v = Math.min(v, d[k - s + 1] + 1.4142f);
                    }
                }
                d[k] = v;
            }
        }
        for (int j = s - 1; j >= 0; j--) {
            for (int i = s - 1; i >= 0; i--) {
                int k = j * s + i;
                float v = d[k];
                if (v == 0.0f) {
                    continue;
                }
                if (i < s - 1) {
                    v = Math.min(v, d[k + 1] + 1.0f);
                }
                if (j < s - 1) {
                    v = Math.min(v, d[k + s] + 1.0f);
                    if (i < s - 1) {
                        v = Math.min(v, d[k + s + 1] + 1.4142f);
                    }
                    if (i > 0) {
                        v = Math.min(v, d[k + s - 1] + 1.4142f);
                    }
                }
                d[k] = v;
            }
        }
        float[] c = GM_Corner;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                float sum = 0.0f;
                int n = 0;
                boolean touchesLand = false;
                for (int dj = -1; dj <= 0; dj++) {
                    int tj = j + dj;
                    if (tj < 0 || tj >= s) {
                        continue;
                    }
                    for (int di = -1; di <= 0; di++) {
                        int ti = i + di;
                        if (ti < 0 || ti >= s) {
                            continue;
                        }
                        float v = d[tj * s + ti];
                        if (v == 0.0f) {
                            touchesLand = true;
                        }
                        sum += Math.min(v, GM_Range + 1.0f);
                        n++;
                    }
                }
                float v = n > 0 ? sum / n - 0.5f : GM_Range;
                c[j * s + i] = touchesLand ? 0.0f : Math.max(0.15f, v);
            }
        }
        float[] t = GM_Scratch;
        for (int pass = 0; pass < 2; pass++) {
            System.arraycopy(c, 0, t, 0, s * s);
            for (int j = 0; j < s; j++) {
                for (int i = 0; i < s; i++) {
                    int k = j * s + i;
                    if (t[k] <= 0.0f) {
                        continue;
                    }
                    float sum = t[k] * 2.0f;
                    int w = 2;
                    if (i > 0) {
                        sum += t[k - 1];
                        w++;
                    }
                    if (i < s - 1) {
                        sum += t[k + 1];
                        w++;
                    }
                    if (j > 0) {
                        sum += t[k - s];
                        w++;
                    }
                    if (j < s - 1) {
                        sum += t[k + s];
                        w++;
                    }
                    c[k] = Math.max(0.15f, sum / w);
                }
            }
        }
        float[] f = GM_Fetch;
        int r = GM_Fetch_Radius;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                float m = 0.0f;
                int a = Math.max(0, i - r);
                int b = Math.min(s - 1, i + r);
                for (int ti = a; ti <= b; ti++) {
                    m = Math.max(m, d[j * s + ti]);
                }
                t[j * s + i] = Math.min(m, GM_Range);
            }
        }
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                float m = 0.0f;
                int a = Math.max(0, j - r);
                int b = Math.min(s - 1, j + r);
                for (int tj = a; tj <= b; tj++) {
                    m = Math.max(m, t[tj * s + i]);
                }
                f[j * s + i] = m;
            }
        }
        ByteBuffer pixels = GM_Buffers[GM_Buffer_Next];
        if (pixels == null) {
            pixels = ByteBuffer.allocateDirect(s * s * 4).order(ByteOrder.nativeOrder());
            GM_Buffers[GM_Buffer_Next] = pixels;
        }
        GM_Buffer_Next = (GM_Buffer_Next + 1) % GM_Buffers.length;
        pixels.clear();
        for (int k = 0; k < s * s; k++) {
            float dist = Math.min(c[k], GM_Range - 0.03f) / GM_Range;
            float fetch = Math.min(f[k], GM_Range) / GM_Range;
            pixels.put((byte) Math.round(dist * 255.0f));
            pixels.put((byte) Math.round(fetch * 255.0f));
            pixels.put((byte) 0);
            pixels.put((byte) 255);
        }
        pixels.flip();
        GM_Pending.set(new GM_Data(x0, y0, pixels));
    }

    static void GM_Bind(float toon) {
        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }
        if (program != GM_Program) {
            GM_Program = program;
            GM_Loc_Field = GL20.glGetUniformLocation(program, "GM_Field");
            GM_Loc_Rect = GL20.glGetUniformLocation(program, "GM_FieldRect");
            GM_Loc_On = GL20.glGetUniformLocation(program, "GM_FieldOn");
            GM_Loc_Toon = GL20.glGetUniformLocation(program, "GM_Toon");
            GM_Loc_Tile = GL20.glGetUniformLocation(program, "GM_TileScale");
        }
        if (GM_Loc_On < 0) {
            return;
        }
        GM_Data pending = GM_Pending.getAndSet(null);
        int unit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL_TEXTURE0 + GM_Unit);
        if (GM_Texture == 0) {
            GM_Texture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, GM_Texture);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else {
            GL11.glBindTexture(GL_TEXTURE_2D, GM_Texture);
        }
        if (pending != null) {
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, GM_Size, GM_Size, 0, GL_RGBA, GL_UNSIGNED_BYTE, pending.pixels);
            GM_Current = pending;
        }
        Texture.lastTextureID = -1;
        GL13.glActiveTexture(unit);
        GM_Data current = GM_Current;
        if (GM_Loc_Field >= 0) {
            GL20.glUniform1i(GM_Loc_Field, GM_Unit);
        }
        if (GM_Loc_Rect >= 0 && current != null) {
            GL20.glUniform4f(GM_Loc_Rect, current.x0, current.y0, 1.0f / GM_Size, 1.0f / GM_Size);
        }
        GL20.glUniform1f(GM_Loc_On, current != null ? 1.0f : 0.0f);
        if (GM_Loc_Toon >= 0) {
            GL20.glUniform1f(GM_Loc_Toon, toon);
        }
        if (GM_Loc_Tile >= 0) {
            GL20.glUniform1f(GM_Loc_Tile, Core.tileScale);
        }
    }
}
