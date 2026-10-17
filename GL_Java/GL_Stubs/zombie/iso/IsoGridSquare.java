package zombie.iso;

public final class IsoGridSquare {
    public int x;
    public int y;

    public boolean has(zombie.iso.SpriteDetails.IsoFlagType flag) {
        return false;
    }

    public boolean isOutside() {
        return false;
    }

    public IsoObject getFloor() {
        return null;
    }

    public IsoGridSquare getAdjacentSquare(IsoDirections direction) {
        return null;
    }

    public void clearWater() {
    }

    public IsoPuddlesGeometry getPuddles() {
        return null;
    }
}
