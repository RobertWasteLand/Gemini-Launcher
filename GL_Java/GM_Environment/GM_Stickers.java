package GL_Java.GM_Environment;

import java.util.ArrayList;
import java.util.IdentityHashMap;

import org.lwjgl.opengl.GL11;
import zombie.IndieGL;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWaterGeometry;
import zombie.iso.IsoWorld;
import zombie.iso.fboRenderChunk.FBORenderCell;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.util.list.PZArrayList;

final class GM_Stickers {
    static final int GM_Reach = 2;
    static final String GM_Ground = "blends_natural";

    private static final IdentityHashMap<IsoGridSquare, Boolean> GM_Seen = new IdentityHashMap<>(4096);

    private GM_Stickers() {
    }

    static void GM_Draw(ArrayList<IsoGridSquare> grid) {
        IsoWorld world = IsoWorld.instance;
        IsoCell cell = world == null ? null : world.currentCell;
        if (cell == null || FBORenderCell.instance == null || IsoCamera.frameState == null) {
            return;
        }
        int player = IsoCamera.frameState.playerIndex;
        boolean only = FBORenderCell.instance.renderTranslucentOnly;
        FBORenderCell.instance.renderTranslucentOnly = true;
        IndieGL.enableDepthTest();
        IndieGL.glDepthFunc(GL11.GL_LEQUAL);
        IndieGL.glDepthMask(false);
        IsoGridSquare.setBlendFunc();
        try {
            for (int i = 0; i < grid.size(); i++) {
                IsoGridSquare shore = grid.get(i);
                if (shore == null) {
                    continue;
                }
                for (int a = 0; a <= GM_Reach; a++) {
                    for (int b = 0; a + b <= GM_Reach; b++) {
                        IsoGridSquare square = a + b == 0 ? shore : cell.getGridSquare(shore.x + a, shore.y + b, shore.z);
                        if (square != null && GM_Seen.put(square, Boolean.TRUE) == null) {
                            GM_Square(square, player);
                        }
                    }
                }
            }
        } finally {
            GM_Seen.clear();
            FBORenderCell.instance.renderTranslucentOnly = only;
            IndieGL.glDepthMask(false);
            IndieGL.glDepthFunc(GL11.GL_ALWAYS);
        }
    }

    private static void GM_Square(IsoGridSquare square, int player) {
        IsoWaterGeometry water = square.z == 0 ? square.getWater() : null;
        if (water == null) {
            return;
        }
        boolean shore = water.isbShore();
        if (!shore && !water.hasWater()) {
            return;
        }
        ColorInfo light = square.getLightInfo(player);
        if (light == null) {
            return;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object == null ? null : object.getSprite();
            if (sprite == null || !sprite.solidfloor) {
                continue;
            }
            if (shore && GM_Built(sprite.getName())) {
                FBORenderCell.instance.renderFloor(object);
                IndieGL.glDepthMask(false);
                IsoGridSquare.setBlendFunc();
                continue;
            }
            ArrayList<IsoSpriteInstance> stickers = object.getAttachedAnimSprite();
            if ((stickers == null || stickers.isEmpty()) && object.getOverlaySprite() == null) {
                continue;
            }
            object.renderAttachedAndOverlaySprites(object.getForwardIsoDirection(), square.x, square.y, square.z, light, true, false, null, null);
            IndieGL.glDepthMask(false);
        }
    }

    static boolean GM_Built(String name) {
        return name != null && !name.startsWith(GM_Ground);
    }
}
