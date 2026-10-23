package GL_Java.GM_Environment;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

final class GM_Settings {
    static final String GM_File_Name = "GM_Environment.cfg";
    private static final long GM_Poll_Nanos = 1_000_000_000L;
    private static final boolean GM_Dev = Boolean.getBoolean("GL_Dev");
    private static final GM_Settings GM_Default = new GM_Settings(0L, false, 0, GM_Defaults(), null);

    final long generation;
    final boolean bypass;
    final int debug;
    final boolean screen;
    final boolean depth;
    final boolean field;
    final String failTest;
    private final int[] values;

    private static volatile GM_Settings GM_Current = GM_Default;
    private static Path GM_Path;
    private static boolean GM_Path_Failed;
    private static long GM_Last_Poll;
    private static long GM_Last_Modified = Long.MIN_VALUE;
    private static long GM_Last_Size = Long.MIN_VALUE;

    private GM_Settings(long generation, boolean bypass, int debug, int[] values, String failTest) {
        this.generation = generation;
        this.bypass = bypass;
        this.debug = debug;
        this.values = values;
        this.failTest = failTest;
        this.screen = bypass || debug > 0 || GM_Any(values, GM_Features.GM_Fog);
        this.depth = debug > 0 || GM_Any(values, GM_Features.GM_Fog);
        this.field = debug > 0 || GM_Any(values, GM_Features.GM_Water, GM_Features.GM_Fog);
    }

    private static boolean GM_Any(int[] values, int... features) {
        for (int feature : features) {
            if (feature < 0 || feature >= values.length || values[feature] > 0) {
                return true;
            }
        }
        return false;
    }

    static GM_Settings GM_Get() {
        return GM_Current;
    }

    static boolean GM_Test(String spot) {
        String test = GM_Current.failTest;
        return test != null && test.equals(spot);
    }

    int GM_Value(int feature, int fallback) {
        return feature >= 0 && feature < values.length ? values[feature] : fallback;
    }

    boolean GM_On(int feature) {
        return GM_Value(feature, 1) > 0;
    }

    private static int[] GM_Defaults() {
        int[] values = new int[GM_Features.GM_List.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = GM_Features.GM_List[i].value;
        }
        return values;
    }

    static boolean GM_Poll(boolean force) {
        long now = System.nanoTime();
        if (!force && GM_Last_Poll != 0L && now - GM_Last_Poll < GM_Poll_Nanos) {
            return false;
        }
        GM_Last_Poll = now;
        Path path = GM_Path();
        if (path == null) {
            return false;
        }
        long modified;
        long size;
        try {
            if (!Files.isRegularFile(path)) {
                modified = -1L;
                size = -1L;
            } else {
                modified = Files.getLastModifiedTime(path).toMillis();
                size = Files.size(path);
            }
        } catch (Exception e) {
            return false;
        }
        if (modified == GM_Last_Modified && size == GM_Last_Size) {
            return false;
        }
        GM_Settings next = modified < 0L ? GM_Default : GM_Read(path);
        if (next == null) {
            return false;
        }
        GM_Last_Modified = modified;
        GM_Last_Size = size;
        GM_Settings previous = GM_Current;
        GM_Current = next;
        String changes = GM_Changes(previous, next);
        if (!changes.isEmpty() || next.generation != previous.generation) {
            System.out.println("[GL_Java] GM settings " + next.generation + ":" + (changes.isEmpty() ? " no changes" : changes));
        }
        return true;
    }

    private static String GM_Changes(GM_Settings previous, GM_Settings next) {
        StringBuilder text = new StringBuilder();
        if (next.bypass != previous.bypass) {
            text.append(" GM_Bypass ").append(next.bypass ? 1 : 0);
        }
        if (next.debug != previous.debug) {
            text.append(" GM_Debug ").append(next.debug);
        }
        if (!Objects.equals(next.failTest, previous.failTest)) {
            text.append(" GM_Fail_Test ").append(next.failTest == null ? "-" : next.failTest);
        }
        for (int i = 0; i < next.values.length; i++) {
            if (next.values[i] != previous.values[i]) {
                text.append(' ').append(GM_Features.GM_List[i].id).append(' ').append(next.values[i]);
            }
        }
        return text.toString();
    }

    private static Path GM_Path() {
        if (GM_Path != null || GM_Path_Failed) {
            return GM_Path;
        }
        try {
            Path jar = Path.of(GM_Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            GM_Path = jar.getParent().resolve(GM_File_Name);
        } catch (Exception e) {
            GM_Path_Failed = true;
            System.out.println("[GL_Java] GM settings off: settings file not found (" + e + ")");
        }
        return GM_Path;
    }

    private static GM_Settings GM_Read(Path path) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            return null;
        }
        int[] values = GM_Defaults();
        for (int i = 0; i < values.length; i++) {
            GM_Features.GM_Feature feature = GM_Features.GM_List[i];
            long value = GM_Long(properties, feature.id, values[i]);
            values[i] = (int) Math.max(feature.min, Math.min(feature.max, value));
        }
        long debug = GM_Long(properties, "GM_Debug", 0L);
        String test = GM_Dev ? properties.getProperty("GM_Fail_Test") : null;
        test = test == null || test.isBlank() ? null : test.trim();
        return new GM_Settings(GM_Long(properties, "GM_Generation", 0L), GM_Long(properties, "GM_Bypass", 0L) > 0L, debug == 1L || debug == 4L || debug == 6L ? (int) debug : 0, values, test);
    }

    private static long GM_Long(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
