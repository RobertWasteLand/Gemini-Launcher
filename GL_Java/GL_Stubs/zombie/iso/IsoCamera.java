package zombie.iso;

public final class IsoCamera {
    public static final FrameState frameState = null;

    public static final class FrameState {
        public int playerIndex;
        public float camCharacterX;
        public float camCharacterY;
        public float camCharacterZ;
        public float offX;
        public float offY;
        public float zoom;
    }

    public static float getOffX(int playerIndex) {
        return 0.0f;
    }

    public static float getOffY(int playerIndex) {
        return 0.0f;
    }

    public static int getOffscreenLeft(int playerIndex) {
        return 0;
    }

    public static int getOffscreenTop(int playerIndex) {
        return 0;
    }

    public static int getScreenWidth(int playerIndex) {
        return 0;
    }

    public static int getScreenHeight(int playerIndex) {
        return 0;
    }
}
