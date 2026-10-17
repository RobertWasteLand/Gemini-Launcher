package GL_Java.GM_Environment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.SpriteDetails.IsoFlagType;

final class GM_Field {
    static final int GM_Size = 160;
    static final int GM_Fetch_Radius = 6;
    static final float GM_Range = 8.0f;
    static final int GM_Rebuild_Ticks = 120;
    static final int GM_Rows_Per_Tick = 10;
    static final float GM_Rebuild_Move = 24.0f;
    static final int GM_Unit = 3;
    static final int GM_Unit_Bank = 4;

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
        final ByteBuffer bank;

        GM_Data(int x0, int y0, ByteBuffer pixels, ByteBuffer bank) {
            this.x0 = x0;
            this.y0 = y0;
            this.pixels = pixels;
            this.bank = bank;
        }
    }

    private static final AtomicReference<GM_Data> GM_Pending = new AtomicReference<>();
    private static final ByteBuffer[] GM_Buffers = new ByteBuffer[3];
    private static final ByteBuffer[] GM_Bank_Buffers = new ByteBuffer[3];
    private static int GM_Buffer_Next;
    private static final float[] GM_Dist = new float[GM_Size * GM_Size];
    private static final int[] GM_Source = new int[GM_Size * GM_Size];
    private static final int[] GM_Colour = new int[GM_Size * GM_Size];
    private static final float[] GM_Corner = new float[GM_Size * GM_Size];
    private static final float[] GM_Scratch = new float[GM_Size * GM_Size];
    private static final float[] GM_Fetch = new float[GM_Size * GM_Size];
    private static final float[] GM_Land = new float[GM_Size * GM_Size];
    private static final IsoGridSquare[] GM_Squares = new IsoGridSquare[GM_Rows_Per_Tick * GM_Size];
    private static final int GM_Water = -1;
    private static final int GM_Unknown = -2;
    private static final Semaphore GM_Go = new Semaphore(0);
    private static volatile boolean GM_Busy;
    private static volatile Throwable GM_Worker_Error;
    private static Thread GM_Worker;
    private static int GM_Job_X0;
    private static int GM_Job_Y0;
    static final float GM_Near_Range = 7.0f;
    private static int GM_Ticks;
    private static int GM_Built_Tick = Integer.MIN_VALUE;
    private static float GM_Center_X;
    private static float GM_Center_Y;
    private static boolean GM_Built;
    private static int GM_Row = -1;
    private static int GM_Gather_X0;
    private static int GM_Gather_Y0;
    private static int GM_Why;
    private static boolean GM_Same;
    private static int GM_Missing;
    private static long GM_Gather_Spent;
    private static long GM_Gather_Slowest;

    private static GM_Data GM_Current;
    private static int GM_Texture;
    private static int GM_Texture_Bank;
    private static int GM_Program;
    private static int GM_Loc_Field = -1;
    private static int GM_Loc_Bank = -1;
    private static int GM_Loc_Rect = -1;
    private static int GM_Loc_On = -1;
    private static int GM_Loc_Toon = -1;
    private static int GM_Loc_Tile = -1;
    private static int GM_Loc_Quad = -1;

    private GM_Field() {
    }

    static void GM_Invalidate() {
        GM_Built = false;
        GM_Row = -1;
    }

    static void GM_Tick() {
        Throwable failed = GM_Worker_Error;
        if (failed != null) {
            GM_Worker_Error = null;
            throw new IllegalStateException("field worker failed", failed);
        }
        GM_Ticks++;
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null) {
            return;
        }
        IsoWorld world = IsoWorld.instance;
        if (world == null || world.currentCell == null) {
            return;
        }
        if (GM_Row < 0) {
            float px = player.getX();
            float py = player.getY();
            boolean moved = Math.abs(px - GM_Center_X) >= GM_Rebuild_Move || Math.abs(py - GM_Center_Y) >= GM_Rebuild_Move;
            if (GM_Built && !moved && GM_Ticks - GM_Built_Tick < GM_Rebuild_Ticks) {
                return;
            }
            if (GM_Busy) {
                return;
            }
            GM_Begin(px, py, !GM_Built ? GM_Timing.GM_Why_Reset : moved ? GM_Timing.GM_Why_Moved : GM_Timing.GM_Why_Timer);
        }
        GM_Slice(world.currentCell);
    }

    private static void GM_Begin(float px, float py, int why) {
        int x0 = (int) Math.floor(px) - GM_Size / 2;
        int y0 = (int) Math.floor(py) - GM_Size / 2;
        GM_Gather_X0 = x0;
        GM_Gather_Y0 = y0;
        GM_Why = why;
        GM_Same = GM_Built && x0 == GM_Job_X0 && y0 == GM_Job_Y0;
        GM_Missing = 0;
        GM_Gather_Spent = 0L;
        GM_Gather_Slowest = 0L;
        GM_Row = 0;
        GM_Built_Tick = GM_Ticks;
        GM_Center_X = px;
        GM_Center_Y = py;
    }

    private static void GM_Slice(IsoCell cell) {
        int s = GM_Size;
        int first = GM_Row;
        int last = Math.min(s, first + GM_Rows_Per_Tick);
        int x0 = GM_Gather_X0;
        int y0 = GM_Gather_Y0;
        int[] colour = GM_Colour;
        IsoGridSquare[] squares = GM_Squares;
        long start = GM_Timing.GM_Cpu_Start();
        for (int j = first; j < last; j++) {
            for (int i = 0; i < s; i++) {
                squares[(j - first) * s + i] = cell.getGridSquare(x0 + i, y0 + j, 0);
            }
        }
        long mark = GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Squares, start);
        int base = first * s;
        int count = (last - first) * s;
        for (int n = 0; n < count; n++) {
            IsoGridSquare square = squares[n];
            squares[n] = null;
            if (square == null) {
                GM_Missing++;
            }
            int value = square == null ? GM_Unknown : square.has(IsoFlagType.water) ? GM_Water : GM_Bank.GM_Colour(GM_Floor_Sprite(square));
            if (value != colour[base + n]) {
                colour[base + n] = value;
                GM_Same = false;
            }
        }
        long spent = GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Floors, mark) - start;
        GM_Gather_Spent += spent;
        GM_Gather_Slowest = Math.max(GM_Gather_Slowest, spent);
        GM_Row = last;
        if (last < s) {
            return;
        }
        GM_Row = -1;
        GM_Built = true;
        GM_Timing.GM_Field_Built(GM_Why, GM_Gather_Spent, GM_Gather_Slowest, GM_Same, GM_Missing);
        if (GM_Same) {
            return;
        }
        GM_Job_X0 = x0;
        GM_Job_Y0 = y0;
        if (GM_Worker == null) {
            Thread worker = new Thread(GM_Field::GM_Work_Loop, "GM_Field");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
            GM_Worker = worker;
        }
        GM_Busy = true;
        GM_Go.release();
    }

    private static void GM_Work_Loop() {
        while (true) {
            GM_Go.acquireUninterruptibly();
            try {
                GM_Work(GM_Job_X0, GM_Job_Y0);
            } catch (Throwable t) {
                GM_Worker_Error = t;
            }
            GM_Busy = false;
        }
    }

    private static void GM_Work(int x0, int y0) {
        int s = GM_Size;
        float far = GM_Range * 4.0f;
        float[] d = GM_Dist;
        int[] src = GM_Source;
        int[] colour = GM_Colour;
        long start = GM_Timing.GM_Cpu_Start();
        if (GM_Settings.GM_Test("worker")) {
            throw new IllegalStateException("test failure: worker");
        }
        for (int k = 0; k < s * s; k++) {
            boolean water = colour[k] < 0;
            d[k] = water ? far : 0.0f;
            src[k] = water ? -1 : k;
        }
        long mark = start;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                int k = j * s + i;
                if (d[k] == 0.0f) {
                    continue;
                }
                if (i > 0) {
                    GM_Relax(k, k - 1, 1.0f);
                }
                if (j > 0) {
                    GM_Relax(k, k - s, 1.0f);
                    if (i > 0) {
                        GM_Relax(k, k - s - 1, 1.4142f);
                    }
                    if (i < s - 1) {
                        GM_Relax(k, k - s + 1, 1.4142f);
                    }
                }
            }
        }
        for (int j = s - 1; j >= 0; j--) {
            for (int i = s - 1; i >= 0; i--) {
                int k = j * s + i;
                if (d[k] == 0.0f) {
                    continue;
                }
                if (i < s - 1) {
                    GM_Relax(k, k + 1, 1.0f);
                }
                if (j < s - 1) {
                    GM_Relax(k, k + s, 1.0f);
                    if (i < s - 1) {
                        GM_Relax(k, k + s + 1, 1.4142f);
                    }
                    if (i > 0) {
                        GM_Relax(k, k + s - 1, 1.4142f);
                    }
                }
            }
        }
        mark = GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Spread, mark);
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
        mark = GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Corner, mark);
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
        mark = GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Reach, mark);
        float[] land = GM_Land;
        for (int k = 0; k < s * s; k++) {
            land[k] = colour[k] == GM_Water ? 0.0f : far;
        }
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                int k = j * s + i;
                if (land[k] == 0.0f) {
                    continue;
                }
                if (i > 0) {
                    land[k] = Math.min(land[k], land[k - 1] + 1.0f);
                }
                if (j > 0) {
                    land[k] = Math.min(land[k], land[k - s] + 1.0f);
                    if (i > 0) {
                        land[k] = Math.min(land[k], land[k - s - 1] + 1.4142f);
                    }
                    if (i < s - 1) {
                        land[k] = Math.min(land[k], land[k - s + 1] + 1.4142f);
                    }
                }
            }
        }
        for (int j = s - 1; j >= 0; j--) {
            for (int i = s - 1; i >= 0; i--) {
                int k = j * s + i;
                if (land[k] == 0.0f) {
                    continue;
                }
                if (i < s - 1) {
                    land[k] = Math.min(land[k], land[k + 1] + 1.0f);
                }
                if (j < s - 1) {
                    land[k] = Math.min(land[k], land[k + s] + 1.0f);
                    if (i < s - 1) {
                        land[k] = Math.min(land[k], land[k + s + 1] + 1.4142f);
                    }
                    if (i > 0) {
                        land[k] = Math.min(land[k], land[k + s - 1] + 1.4142f);
                    }
                }
            }
        }
        mark = GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Land, mark);
        ByteBuffer pixels = GM_Buffers[GM_Buffer_Next];
        ByteBuffer bank = GM_Bank_Buffers[GM_Buffer_Next];
        if (pixels == null) {
            pixels = ByteBuffer.allocateDirect(s * s * 4).order(ByteOrder.nativeOrder());
            bank = ByteBuffer.allocateDirect(s * s * 4).order(ByteOrder.nativeOrder());
            GM_Buffers[GM_Buffer_Next] = pixels;
            GM_Bank_Buffers[GM_Buffer_Next] = bank;
        }
        GM_Buffer_Next = (GM_Buffer_Next + 1) % GM_Buffers.length;
        pixels.clear();
        bank.clear();
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                int k = j * s + i;
                float dist = Math.min(c[k], GM_Range - 0.03f) / GM_Range;
                float fetch = Math.min(f[k], GM_Range) / GM_Range;
                pixels.put((byte) Math.round(dist * 255.0f));
                pixels.put((byte) Math.round(fetch * 255.0f));
                float near = 0.0f;
                int known = 0;
                int cornerN = 0;
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
                        near += Math.max(0.0f, 1.0f - Math.min(land[tj * s + ti], GM_Near_Range) / GM_Near_Range);
                        if (colour[tj * s + ti] != GM_Unknown) {
                            known++;
                        }
                        cornerN++;
                    }
                }
                pixels.put((byte) Math.round((cornerN > 0 ? near / cornerN : 0.0f) * 255.0f));
                pixels.put((byte) Math.round((cornerN > 0 ? known / (float) cornerN : 0.0f) * 255.0f));
                float red = 0.0f;
                float green = 0.0f;
                float blue = 0.0f;
                float weightSum = 0.0f;
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
                        int tile = tj * s + ti;
                        int from = src[tile] >= 0 ? src[tile] : tile;
                        int rgb = colour[from] < 0 ? GM_Bank.GM_Default : colour[from];
                        float weight = 1.0f / (d[tile] + 0.5f);
                        red += ((rgb >> 16) & 0xFF) * weight;
                        green += ((rgb >> 8) & 0xFF) * weight;
                        blue += (rgb & 0xFF) * weight;
                        weightSum += weight;
                    }
                }
                if (weightSum <= 0.0f) {
                    weightSum = 1.0f;
                    red = (GM_Bank.GM_Default >> 16) & 0xFF;
                    green = (GM_Bank.GM_Default >> 8) & 0xFF;
                    blue = GM_Bank.GM_Default & 0xFF;
                }
                bank.put((byte) Math.round(red / weightSum));
                bank.put((byte) Math.round(green / weightSum));
                bank.put((byte) Math.round(blue / weightSum));
                bank.put((byte) 255);
            }
        }
        pixels.flip();
        bank.flip();
        GM_Timing.GM_Field_Part(GM_Timing.GM_Part_Encode, mark);
        GM_Timing.GM_Field_Worked(start);
        GM_Pending.set(new GM_Data(x0, y0, pixels, bank));
    }

    private static void GM_Relax(int k, int n, float step) {
        float v = GM_Dist[n] + step;
        if (v < GM_Dist[k]) {
            GM_Dist[k] = v;
            GM_Source[k] = GM_Source[n] >= 0 ? GM_Source[n] : n;
        }
    }

    private static String GM_Floor_Sprite(IsoGridSquare square) {
        if (square == null) {
            return null;
        }
        IsoObject floor = square.getFloor();
        if (floor == null) {
            return null;
        }
        IsoSprite sprite = floor.getSprite();
        return sprite == null ? null : sprite.getName();
    }

    static void GM_Bind(float toon) {
        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }
        if (program != GM_Program) {
            GM_Program = program;
            GM_Loc_Field = GL20.glGetUniformLocation(program, "GM_Field");
            GM_Loc_Bank = GL20.glGetUniformLocation(program, "GM_Bank");
            GM_Loc_Rect = GL20.glGetUniformLocation(program, "GM_FieldRect");
            GM_Loc_On = GL20.glGetUniformLocation(program, "GM_FieldOn");
            GM_Loc_Toon = GL20.glGetUniformLocation(program, "GM_Toon");
            GM_Loc_Tile = GL20.glGetUniformLocation(program, "GM_TileScale");
            GM_Loc_Quad = GL20.glGetUniformLocation(program, "GM_ShoreQuad");
        }
        if (GM_Loc_On < 0) {
            return;
        }
        GM_Data current = GM_Upload(GM_Unit, GM_Unit_Bank);
        if (GM_Loc_Field >= 0) {
            GL20.glUniform1i(GM_Loc_Field, GM_Unit);
        }
        if (GM_Loc_Bank >= 0) {
            GL20.glUniform1i(GM_Loc_Bank, GM_Unit_Bank);
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

    static GM_Data GM_Upload(int unitField, int unitBank) {
        GM_Data pending = GM_Pending.getAndSet(null);
        int unit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL_TEXTURE0 + unitField);
            GM_Texture = GM_Texture_Bind(GM_Texture);
            if (GM_Settings.GM_Test("upload")) {
                throw new IllegalStateException("test failure: upload");
            }
            if (pending != null) {
                GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, GM_Size, GM_Size, 0, GL_RGBA, GL_UNSIGNED_BYTE, pending.pixels);
            }
            GL13.glActiveTexture(GL_TEXTURE0 + unitBank);
            GM_Texture_Bank = GM_Texture_Bind(GM_Texture_Bank);
            if (pending != null) {
                GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, GM_Size, GM_Size, 0, GL_RGBA, GL_UNSIGNED_BYTE, pending.bank);
                GM_Current = pending;
            }
        } finally {
            Texture.lastTextureID = -1;
            GL13.glActiveTexture(unit);
        }
        return GM_Current;
    }

    static void GM_Quad(boolean shore) {
        if (GM_Loc_Quad < 0 || GM_Loc_On < 0) {
            return;
        }
        if (GL11.glGetInteger(GL_CURRENT_PROGRAM) != GM_Program) {
            return;
        }
        GL20.glUniform1f(GM_Loc_Quad, shore ? 1.0f : -1.0f);
    }

    private static int GM_Texture_Bind(int texture) {
        if (texture != 0) {
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            return texture;
        }
        int created = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, created);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return created;
    }
}
