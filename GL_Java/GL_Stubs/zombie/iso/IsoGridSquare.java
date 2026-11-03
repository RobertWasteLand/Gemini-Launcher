package zombie.iso;

public final class IsoGridSquare {
    public int x;
    public int y;
    public int z;

    public static void setBlendFunc() {
    }

    public IsoWaterGeometry getWater() {
        return null;
    }

    public boolean has(zombie.iso.SpriteDetails.IsoFlagType flag) {
        return false;
    }

    public boolean isOutside() {
        return false;
    }

    public IsoObject getFloor() {
        return null;
    }

    public zombie.util.list.PZArrayList<IsoObject> getObjects() {
        return null;
    }

    public zombie.core.textures.ColorInfo getLightInfo(int playerIndex) {
        return null;
    }
}
