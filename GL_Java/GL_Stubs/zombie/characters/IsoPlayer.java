package zombie.characters;

public class IsoPlayer extends zombie.iso.IsoMovingObject {
    public static final IsoPlayer[] players = null;
    public static int numPlayers;

    public static IsoPlayer getInstance() {
        return null;
    }

    public boolean isLocalPlayer() {
        return false;
    }

    public boolean isDead() {
        return false;
    }

    public boolean isInvisible() {
        return false;
    }

    public zombie.vehicles.BaseVehicle getVehicle() {
        return null;
    }

    public float getAlpha(int playerIndex) {
        return 0.0f;
    }
}
