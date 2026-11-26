package zombie.core;

public final class PerformanceSettings {
    public static final PerformanceSettings instance = null;
    public static int waterQuality;
    public static boolean fboRenderChunk;

    public static int getLockFPS() {
        return 0;
    }

    public boolean isFramerateUncapped() {
        return false;
    }

    public void setFramerateUncapped(boolean uncappedFPS) {
    }
}
