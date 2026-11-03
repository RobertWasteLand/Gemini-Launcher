package zombie.core;

public final class Core {
    public static int tileScale;
    public static boolean debug;
    public static boolean soundDisabled;

    public static Core getInstance() {
        return null;
    }

    public void StartFrame() {
    }

    public void EndFrame() {
    }

    public boolean StartFrameUI() {
        return false;
    }

    public void EndFrameUI() {
    }

    public int getScreenWidth() {
        return 0;
    }

    public int getScreenHeight() {
        return 0;
    }

    public int getOptionSoundVolume() {
        return 0;
    }

    public int getOptionMusicVolume() {
        return 0;
    }

    public zombie.core.textures.TextureFBO getOffscreenBuffer(int nPlayer) {
        return null;
    }

    public float getZoom(int playerIndex) {
        return 1.0f;
    }

    public String getVersion() {
        return null;
    }

    public boolean getUseOpenGL21() {
        return false;
    }
}
