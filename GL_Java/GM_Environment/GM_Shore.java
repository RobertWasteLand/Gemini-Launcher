package GL_Java.GM_Environment;

import java.lang.reflect.Field;

import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWaterFlow;
import zombie.iso.IsoWaterGeometry;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

final class GM_Shore {
    private static Field GM_Shore_Field;
    private static boolean GM_Shore_Failed;

    private GM_Shore() {
    }

    static boolean GM_Natural(String sprite) {
        return sprite != null && (sprite.startsWith("blends_natural_01") || sprite.startsWith("floors_exterior_natural"));
    }

    private static boolean GM_Water(IsoGridSquare square) {
        return square.has(IsoFlagType.water);
    }

    static IsoWaterGeometry GM_Init_Exit(Object self, IsoGridSquare square, IsoWaterGeometry result) {
        if (result == null || GM_Shore_Failed || square == null || !(self instanceof IsoWaterGeometry)) {
            return result;
        }
        try {
            IsoWaterGeometry geometry = (IsoWaterGeometry) self;
            if (geometry.hasWater() || geometry.isbShore()) {
                return result;
            }
            if (IsoWaterFlow.getShore(square.x, square.y) != 1) {
                return result;
            }
            IsoObject floor = square.getFloor();
            IsoSprite sprite = floor == null ? null : floor.getSprite();
            if (!GM_Natural(sprite == null ? null : sprite.getName())) {
                return result;
            }
            IsoGridSquare w = square.getAdjacentSquare(IsoDirections.W);
            IsoGridSquare nw = square.getAdjacentSquare(IsoDirections.NW);
            IsoGridSquare n = square.getAdjacentSquare(IsoDirections.N);
            IsoGridSquare sw = square.getAdjacentSquare(IsoDirections.SW);
            IsoGridSquare s = square.getAdjacentSquare(IsoDirections.S);
            IsoGridSquare se = square.getAdjacentSquare(IsoDirections.SE);
            IsoGridSquare e = square.getAdjacentSquare(IsoDirections.E);
            IsoGridSquare ne = square.getAdjacentSquare(IsoDirections.NE);
            if (w == null || nw == null || n == null || sw == null || s == null || se == null || e == null || ne == null) {
                return null;
            }
            boolean bw = GM_Water(w);
            boolean bnw = GM_Water(nw);
            boolean bn = GM_Water(n);
            boolean bsw = GM_Water(sw);
            boolean bs = GM_Water(s);
            boolean bse = GM_Water(se);
            boolean be = GM_Water(e);
            boolean bne = GM_Water(ne);
            float[] depth = geometry.depth;
            depth[0] = bw || bnw || bn ? 1.0f : 0.0f;
            depth[1] = bw || bsw || bs ? 1.0f : 0.0f;
            depth[2] = bs || bse || be ? 1.0f : 0.0f;
            depth[3] = be || bne || bn ? 1.0f : 0.0f;
            if (depth[0] + depth[1] + depth[2] + depth[3] == 0.0f) {
                return result;
            }
            if (GM_Shore_Field == null) {
                Field field = IsoWaterGeometry.class.getDeclaredField("shore");
                field.setAccessible(true);
                GM_Shore_Field = field;
            }
            GM_Shore_Field.setBoolean(geometry, true);
            return result;
        } catch (Throwable t) {
            GM_Shore_Failed = true;
            System.out.println("[GL_Java] GM_Environment: extra shore tiles off (" + t + ")");
            return result;
        }
    }
}
