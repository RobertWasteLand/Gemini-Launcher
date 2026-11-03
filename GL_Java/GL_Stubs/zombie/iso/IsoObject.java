package zombie.iso;

public class IsoObject {
    public zombie.iso.sprite.IsoSprite getSprite() {
        return null;
    }

    public IsoGridSquare getSquare() {
        return null;
    }

    public java.util.ArrayList<zombie.iso.sprite.IsoSpriteInstance> getAttachedAnimSprite() {
        return null;
    }

    public zombie.iso.sprite.IsoSprite getOverlaySprite() {
        return null;
    }

    public IsoDirections getForwardIsoDirection() {
        return null;
    }

    public void renderAttachedAndOverlaySprites(IsoDirections dir, float x, float y, float z, zombie.core.textures.ColorInfo col, boolean bDoAttached, boolean bWallLightingPass, zombie.core.opengl.Shader shader, java.util.function.Consumer<zombie.core.textures.TextureDraw> texdModifier) {
    }
}
