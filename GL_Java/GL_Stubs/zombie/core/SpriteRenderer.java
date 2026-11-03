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

    public void render(zombie.core.textures.Texture tex, float x, float y, float width, float height, float r, float g, float b, float a, java.util.function.Consumer<zombie.core.textures.TextureDraw> texdModifier) {
    }

    public void render(zombie.core.textures.Texture tex, float x, float y, float width, float height, float r, float g, float b, float a, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4) {
    }

    public void renderi(zombie.core.textures.Texture tex, int x, int y, int width, int height, float r, float g, float b, float a, java.util.function.Consumer<zombie.core.textures.TextureDraw> texdModifier) {
    }
}
