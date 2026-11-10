package zombie.characters;

public abstract class IsoGameCharacter extends zombie.iso.IsoMovingObject {
    public zombie.core.skinnedmodel.model.ModelInstance primaryHandModel;

    public boolean isDead() {
        return false;
    }

    public boolean hasAnimationPlayer() {
        return false;
    }

    public zombie.core.skinnedmodel.animation.AnimationPlayer getAnimationPlayer() {
        return null;
    }

    public zombie.inventory.InventoryItem getPrimaryHandItem() {
        return null;
    }

    public float getForwardDirectionX() {
        return 0.0f;
    }

    public float getForwardDirectionY() {
        return 0.0f;
    }
}
