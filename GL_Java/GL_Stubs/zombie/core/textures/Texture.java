package zombie.core.textures;

public class Texture {
    public static int lastTextureID;

    public static Texture getSharedTexture(String name, int flags) {
        return null;
    }

    public int getID() {
        return 0;
    }

    public boolean isReady() {
        return false;
    }

    public boolean isFailure() {
        return false;
    }

    public int getWidth() {
        return 0;
    }

    public int getHeight() {
        return 0;
    }

    public float getXStart() {
        return 0.0f;
    }

    public float getYStart() {
        return 0.0f;
    }

    public float getXEnd() {
        return 1.0f;
    }

    public float getYEnd() {
        return 1.0f;
    }
}
