package zombie.core;

public final class SpriteRenderer {
    public static final SpriteRenderer instance = null;
    public static final RingBuffer ringBuffer = null;

    public static final class RingBuffer {
        public boolean restoreVbos;
        public boolean restoreBoundTextures;
    }

    public zombie.core.textures.TextureDraw drawGeneric(zombie.core.textures.TextureDraw.GenericDrawer drawer) {
        return null;
    }

    public int getRenderingPlayerIndex() {
        return 0;
    }
}
