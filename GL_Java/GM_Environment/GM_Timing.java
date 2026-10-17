package GL_Java.GM_Environment;

import java.util.Arrays;
import java.util.Locale;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;

final class GM_Timing {
    static final boolean GM_On = GM_Wanted();
    static final int GM_Begin = 0;
    static final int GM_Blit = 1;
    static final int GM_Fog = 2;
    static final int GM_Rays = 3;
    static final int GM_Bloom = 4;
    static final int GM_End = 5;
    private static final int GM_Stamps = 6;
    private static final int GM_Water_Max = 32;
    private static final int GM_Queries = GM_Stamps + GM_Water_Max * 2;
    private static final int GM_Depth = 3;
    private static final long GM_Window = 5_000_000_000L;
    private static final int GL_TIMESTAMP = 0x8E28;
    private static final int GL_QUERY_RESULT = 0x8866;
    private static final int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    private static final String[] GM_Names = {"depth", "fog", "rays", "bloom", "screen"};

    private static final class GM_End_Draw extends TextureDraw.GenericDrawer {
        @Override
        public void render() {
            GM_Stamp(GM_End);
        }
    }

    private static final class GM_Cpu {
        private long sum;
        private long max;
        private int count;
        private long since;
        private volatile float avg = -1.0f;
        private volatile float peak = -1.0f;

        void GM_Add(long nanos) {
            sum += nanos;
            count++;
            if (nanos > max) {
                max = nanos;
            }
            long now = System.nanoTime();
            if (since == 0L) {
                since = now;
                return;
            }
            if (now - since < GM_Window) {
                return;
            }
            avg = sum / (float) count / 1.0e6f;
            peak = max / 1.0e6f;
            sum = 0L;
            max = 0L;
            count = 0;
            since = now;
        }

        String GM_Text() {
            float a = avg;
            float p = peak;
            if (a < 0.0f) {
                return "-";
            }
            return GM_Ms(a) + " (max " + GM_Ms(p) + ")";
        }
    }

    private static final GM_End_Draw GM_End_Drawer = new GM_End_Draw();
    private static final GM_Cpu GM_Cpu_Queue = new GM_Cpu();
    private static final GM_Cpu GM_Cpu_Field = new GM_Cpu();
    private static final int[][] GM_Ids = new int[GM_Depth][GM_Queries];
    private static final int[] GM_Mask = new int[GM_Depth];
    private static final int[] GM_Pairs = new int[GM_Depth];
    private static final long[] GM_Frame_Of = new long[GM_Depth];
    private static final long[] GM_Times = new long[GM_Stamps];
    private static final double[] GM_Sum = new double[GM_Stamps - 1];
    private static final int[] GM_Count = new int[GM_Stamps - 1];
    private static volatile boolean GM_Dead;
    private static boolean GM_Ready;
    private static boolean GM_Water_Open;
    private static int GM_Slot;
    private static long GM_Frame;
    private static long GM_Last_End;
    private static long GM_Last_End_Frame = -2L;
    private static long GM_Last_Report;
    private static double GM_Water_Sum;
    private static double GM_Period_Sum;
    private static int GM_Periods;
    private static int GM_Samples;
    private static int GM_Skipped;

    private GM_Timing() {
    }

    private static boolean GM_Wanted() {
        boolean dev = Boolean.getBoolean("GL_Dev");
        try {
            return dev || Core.debug;
        } catch (Throwable t) {
            return dev;
        }
    }

    static long GM_Cpu_Start() {
        return GM_On ? System.nanoTime() : 0L;
    }

    static void GM_Cpu_Queue(long start) {
        if (GM_On) {
            GM_Cpu_Queue.GM_Add(System.nanoTime() - start);
        }
    }

    static void GM_Cpu_Field(long start) {
        if (GM_On) {
            GM_Cpu_Field.GM_Add(System.nanoTime() - start);
        }
    }

    static void GM_Queue_End() {
        if (!GM_On || GM_Dead) {
            return;
        }
        try {
            SpriteRenderer.instance.drawGeneric(GM_End_Drawer);
        } catch (Throwable t) {
            GM_Kill(t);
        }
    }

    static void GM_Stamp(int stamp) {
        if (!GM_On || GM_Dead) {
            return;
        }
        try {
            if (!GM_Ready && !GM_Init()) {
                return;
            }
            GL33.glQueryCounter(GM_Ids[GM_Slot][stamp], GL_TIMESTAMP);
            GM_Mask[GM_Slot] |= 1 << stamp;
            if (stamp == GM_End) {
                GM_Next();
            }
        } catch (Throwable t) {
            GM_Kill(t);
        }
    }

    static void GM_Water(boolean begin) {
        if (!GM_On || GM_Dead) {
            return;
        }
        try {
            if (!GM_Ready && !GM_Init()) {
                return;
            }
            int pair = GM_Pairs[GM_Slot];
            if (pair >= GM_Water_Max) {
                return;
            }
            if (begin) {
                GL33.glQueryCounter(GM_Ids[GM_Slot][GM_Stamps + pair * 2], GL_TIMESTAMP);
                GM_Water_Open = true;
            } else if (GM_Water_Open) {
                GL33.glQueryCounter(GM_Ids[GM_Slot][GM_Stamps + pair * 2 + 1], GL_TIMESTAMP);
                GM_Pairs[GM_Slot] = pair + 1;
                GM_Water_Open = false;
            }
        } catch (Throwable t) {
            GM_Kill(t);
        }
    }

    private static boolean GM_Init() {
        GLCapabilities caps = GL.getCapabilities();
        if (caps == null || !(caps.OpenGL33 || caps.GL_ARB_timer_query)) {
            GM_Dead = true;
            System.out.println("[GL_Java] GM timing off: this driver has no GPU timer queries");
            return false;
        }
        for (int i = 0; i < GM_Depth; i++) {
            GL15.glGenQueries(GM_Ids[i]);
        }
        GM_Last_Report = System.nanoTime();
        GM_Ready = true;
        System.out.println("[GL_Java] GM timing on: one line every " + GM_Window / 1_000_000_000L + " s");
        return true;
    }

    private static void GM_Kill(Throwable t) {
        if (GM_Dead) {
            return;
        }
        GM_Dead = true;
        System.out.println("[GL_Java] GM timing off: " + t);
    }

    private static void GM_Next() {
        GM_Frame_Of[GM_Slot] = GM_Frame++;
        GM_Water_Open = false;
        GM_Slot = (GM_Slot + 1) % GM_Depth;
        GM_Collect(GM_Slot);
        GM_Mask[GM_Slot] = 0;
        GM_Pairs[GM_Slot] = 0;
        long now = System.nanoTime();
        if (now - GM_Last_Report >= GM_Window) {
            GM_Report();
            GM_Last_Report = now;
        }
    }

    private static boolean GM_Available(int id) {
        return GL15.glGetQueryObjecti(id, GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    private static void GM_Collect(int slot) {
        int mask = GM_Mask[slot];
        int pairs = GM_Pairs[slot];
        if ((mask & (1 << GM_End)) == 0) {
            return;
        }
        int[] ids = GM_Ids[slot];
        for (int k = 0; k < GM_Stamps; k++) {
            if ((mask & (1 << k)) != 0 && !GM_Available(ids[k])) {
                GM_Skipped++;
                return;
            }
        }
        for (int i = 0; i < pairs * 2; i++) {
            if (!GM_Available(ids[GM_Stamps + i])) {
                GM_Skipped++;
                return;
            }
        }
        long[] t = GM_Times;
        for (int k = 0; k < GM_Stamps; k++) {
            if ((mask & (1 << k)) != 0) {
                t[k] = GL33.glGetQueryObjecti64(ids[k], GL_QUERY_RESULT);
            }
        }
        for (int k = 0; k < GM_Stamps - 1; k++) {
            int both = (1 << k) | (1 << (k + 1));
            if ((mask & both) == both) {
                GM_Sum[k] += (t[k + 1] - t[k]) / 1.0e6;
                GM_Count[k]++;
            }
        }
        for (int p = 0; p < pairs; p++) {
            long a = GL33.glGetQueryObjecti64(ids[GM_Stamps + p * 2], GL_QUERY_RESULT);
            long b = GL33.glGetQueryObjecti64(ids[GM_Stamps + p * 2 + 1], GL_QUERY_RESULT);
            GM_Water_Sum += (b - a) / 1.0e6;
        }
        long end = t[GM_End];
        if (GM_Frame_Of[slot] == GM_Last_End_Frame + 1) {
            GM_Period_Sum += (end - GM_Last_End) / 1.0e6;
            GM_Periods++;
        }
        GM_Last_End = end;
        GM_Last_End_Frame = GM_Frame_Of[slot];
        GM_Samples++;
    }

    private static void GM_Report() {
        if (GM_Samples > 0) {
            StringBuilder line = new StringBuilder("[GL_Java] GM timing ms:");
            if (GM_Periods > 0) {
                line.append(" frame ").append(GM_Ms(GM_Period_Sum / GM_Periods)).append(" |");
            }
            double water = GM_Water_Sum / GM_Samples;
            double total = water;
            line.append(" gpu water ").append(GM_Ms(water));
            for (int k = 0; k < GM_Stamps - 1; k++) {
                if (GM_Count[k] > 0) {
                    double v = GM_Sum[k] / GM_Count[k];
                    total += v;
                    line.append(' ').append(GM_Names[k]).append(' ').append(GM_Ms(v));
                }
            }
            line.append(" total ").append(GM_Ms(total));
            line.append(" | cpu queue ").append(GM_Cpu_Queue.GM_Text());
            line.append(" field ").append(GM_Cpu_Field.GM_Text());
            line.append(" | ").append(GM_Samples).append(" frames");
            if (GM_Skipped > 0) {
                line.append(", ").append(GM_Skipped).append(" not ready");
            }
            System.out.println(line);
        }
        Arrays.fill(GM_Sum, 0.0);
        Arrays.fill(GM_Count, 0);
        GM_Water_Sum = 0.0;
        GM_Period_Sum = 0.0;
        GM_Periods = 0;
        GM_Samples = 0;
        GM_Skipped = 0;
    }

    private static String GM_Ms(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
