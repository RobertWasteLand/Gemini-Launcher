package GL_Java.GM_Environment;

final class GM_Bank {
    static final int GM_Default = 0x664A32;
    private static final int[] GM_Blends = { 0xC8B187, 0x4E5B1D, 0x5E5F22, 0x6E6328, 0x5A3E13, 0x4D3A14, 0x664A32, 0x664A32 };
    private static final int[] GM_Floors = new int[64];

    static {
        java.util.Arrays.fill(GM_Floors, GM_Default);
        GM_Floors[0] = 0x4E5B1D;
        GM_Floors[1] = 0x5E5F22;
        GM_Floors[2] = 0x6E6328;
        GM_Floors[8] = 0x696845;
        GM_Floors[9] = 0x716B53;
        GM_Floors[10] = 0x807862;
        GM_Floors[11] = 0x756E58;
        GM_Floors[12] = 0x5B412F;
        GM_Floors[13] = 0x9F9883;
        GM_Floors[14] = 0x94979C;
        for (int i = 16; i <= 19; i++) {
            GM_Floors[i] = 0x573D14;
        }
        GM_Floors[20] = 0x412D21;
        GM_Floors[21] = 0x4E402C;
        for (int i = 24; i <= 35; i++) {
            GM_Floors[i] = 0xD2C299;
        }
        for (int i = 40; i <= 43; i++) {
            GM_Floors[i] = 0x998452;
        }
        for (int i = 44; i <= 51; i++) {
            GM_Floors[i] = 0x9F9883;
        }
    }

    private GM_Bank() {
    }

    static int GM_Colour(String sprite) {
        if (sprite == null) {
            return GM_Default;
        }
        if (sprite.startsWith("blends_natural_01_")) {
            int index = GM_Index(sprite, 18);
            return index < 0 ? GM_Default : GM_Blends[Math.min(index / 16, GM_Blends.length - 1)];
        }
        if (sprite.startsWith("floors_exterior_natural_01_")) {
            int index = GM_Index(sprite, 27);
            return index < 0 || index >= GM_Floors.length ? GM_Default : GM_Floors[index];
        }
        if (sprite.startsWith("floors_exterior_street") || sprite.startsWith("floors_exterior_tile") || sprite.startsWith("blends_street")) {
            return 0x6F6A60;
        }
        return GM_Default;
    }

    private static int GM_Index(String sprite, int from) {
        int value = 0;
        if (from >= sprite.length()) {
            return -1;
        }
        for (int i = from; i < sprite.length(); i++) {
            char c = sprite.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
