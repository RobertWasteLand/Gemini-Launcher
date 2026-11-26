package GL_Java.GM_Particles;

import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.Vector3;
import zombie.scripting.objects.ModelAttachment;

final class GM_Particles_Point {
    static final int GM_Feet = 0;
    static final int GM_Head = 1;
    static final int GM_Left = 2;
    static final int GM_Right = 3;
    static final int GM_Tip = 4;
    static final int GM_Object = 5;
    private static final String[] GM_Names = {"feet", "head", "left hand", "right hand", "tip"};
    private static final String[] GM_Bones = {null, "Bip01_Head", "Bip01_L_Hand", "Bip01_R_Hand", "Bip01_R_Hand"};
    private static final Vector3 GM_Pos = new Vector3();
    private static final Vector3 GM_Dir = new Vector3();

    private GM_Particles_Point() {
    }

    static int GM_Point_Of(Object thing, Object name) {
        if (thing instanceof IsoGameCharacter) {
            if (name == null) {
                return GM_Feet;
            }
            for (int i = 0; i < GM_Names.length; i++) {
                if (GM_Names[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }
        return name == null && thing instanceof IsoObject && !(thing instanceof IsoMovingObject) ? GM_Object : -1;
    }

    static boolean GM_Find(Object thing, int point, Object item, float dx, float dy, float dz, float[] out) {
        if (point == GM_Object) {
            IsoObject object = (IsoObject) thing;
            IsoGridSquare square = object.getSquare();
            if (square == null || square.getChunk() == null || object.getObjectIndex() < 0) {
                return false;
            }
            out[0] = square.x + 0.5f + dx;
            out[1] = square.y + 0.5f + dy;
            out[2] = square.z + dz;
            out[3] = 0.0f;
            out[4] = 0.0f;
            return true;
        }
        IsoGameCharacter character = (IsoGameCharacter) thing;
        if (character.isDead() || character.getCurrentSquare() == null || point == GM_Tip && character.getPrimaryHandItem() != item) {
            return false;
        }
        out[3] = character.getForwardDirectionX();
        out[4] = character.getForwardDirectionY();
        boolean found = point != GM_Feet && (point == GM_Tip && GM_Muzzle(character, out) || GM_Bone(character, GM_Bones[point], out));
        if (!found) {
            out[0] = character.getX();
            out[1] = character.getY();
            out[2] = character.getZ() + (point == GM_Feet ? 0.0f : point == GM_Head ? 0.5f : 0.3f);
        }
        out[0] += dx;
        out[1] += dy;
        out[2] += dz;
        return true;
    }

    private static boolean GM_Bone(Object thing, String bone, float[] out) {
        IsoGameCharacter character = (IsoGameCharacter) thing;
        if (!character.hasAnimationPlayer()) {
            return false;
        }
        AnimationPlayer player = character.getAnimationPlayer();
        int index = player.isReady() ? player.getSkinningBoneIndex(bone, -1) : -1;
        if (index < 0) {
            return false;
        }
        Model.boneToWorldCoords(character, index, GM_Pos);
        out[0] = GM_Pos.x;
        out[1] = GM_Pos.y;
        out[2] = GM_Pos.z;
        return true;
    }

    private static boolean GM_Muzzle(Object thing, float[] out) {
        ModelInstance hand = ((IsoGameCharacter) thing).primaryHandModel;
        if (hand == null || hand.animPlayer == null || !hand.animPlayer.isReady()) {
            return false;
        }
        ModelAttachment muzzle = hand.getAttachmentById("muzzle");
        if (muzzle == null) {
            return false;
        }
        hand.getAttachmentWorldPosition(muzzle, GM_Pos, GM_Dir);
        out[0] = GM_Pos.x;
        out[1] = GM_Pos.y;
        out[2] = GM_Pos.z;
        float length = (float) Math.hypot(GM_Dir.x, GM_Dir.y);
        if (length > 1.0e-4f) {
            out[3] = GM_Dir.x / length;
            out[4] = GM_Dir.y / length;
        }
        return true;
    }
}
