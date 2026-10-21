package GL_Java.GM_Environment;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoPuddlesGeometry;
import zombie.iso.IsoWaterFlow;
import zombie.iso.IsoWaterGeometry;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.sprite.IsoSprite;

final class GM_Shore {
    private static final IsoDirections[] GM_Around = IsoDirections.values();
    private static Field GM_Shore_Field;
    private static Field GM_Recalc_Field;
    private static boolean GM_Shore_Failed;
    private static boolean GM_Recalc_Failed;
    private static int GM_Marked;

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
            GM_Marked++;
            return result;
        } catch (Throwable t) {
            GM_Shore_Failed = true;
            System.out.println("[GL_Java] GM_Environment: extra shore tiles off (" + t + ")");
            return result;
        }
    }

    static void GM_Refresh() {
        IsoWorld world = IsoWorld.instance;
        IsoCell cell = world == null ? null : world.currentCell;
        if (cell == null || cell.chunkMap == null) {
            return;
        }
        long start = System.nanoTime();
        Set<IsoChunk> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int squares = 0;
        int chunks = 0;
        int checked = 0;
        int maps = 0;
        int width = IsoChunkMap.chunkGridWidth;
        int players = Math.min(IsoPlayer.numPlayers, cell.chunkMap.length);
        for (int n = 0; n < players; n++) {
            IsoChunkMap map = cell.chunkMap[n];
            if (map == null || map.ignore) {
                continue;
            }
            maps++;
            for (int cy = 0; cy < width; cy++) {
                for (int cx = 0; cx < width; cx++) {
                    IsoChunk chunk = map.getChunk(cx, cy);
                    if (chunk == null || !seen.add(chunk)) {
                        continue;
                    }
                    checked++;
                    int count = GM_Refresh_Chunk(chunk);
                    if (count > 0) {
                        chunk.invalidateRenderChunkLevel(0, FBORenderChunk.DIRTY_REDRAW);
                        squares += count;
                        chunks++;
                    }
                }
            }
        }
        System.out.println("[GL_Java] GM_Environment shore refreshed, " + squares + " squares in " + chunks + " chunks (" + checked + " chunks checked on " + maps + " of " + IsoPlayer.numPlayers + " player maps, grid " + width + "), "
            + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1.0e6) + " ms, " + GM_Marked + " extra shore squares marked before it");
        GM_Marked = 0;
    }

    private static int GM_Refresh_Chunk(IsoChunk chunk) {
        int count = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                IsoGridSquare square = chunk.getGridSquare(x, y, 0);
                if (square == null || !GM_Candidate(square)) {
                    continue;
                }
                square.clearWater();
                GM_Puddles_Recalc(square);
                count++;
            }
        }
        return count;
    }

    private static boolean GM_Candidate(IsoGridSquare square) {
        if (GM_Water(square)) {
            return false;
        }
        IsoObject floor = square.getFloor();
        IsoSprite sprite = floor == null ? null : floor.getSprite();
        String name = sprite == null ? null : sprite.getName();
        if (name == null || !(name.startsWith("blends_natural") || name.startsWith("floors_exterior_natural"))) {
            return false;
        }
        for (IsoDirections direction : GM_Around) {
            IsoGridSquare next = square.getAdjacentSquare(direction);
            if (next != null && GM_Water(next)) {
                return true;
            }
        }
        return false;
    }

    private static void GM_Puddles_Recalc(IsoGridSquare square) {
        if (GM_Recalc_Failed) {
            return;
        }
        try {
            IsoPuddlesGeometry puddles = square.getPuddles();
            if (puddles == null) {
                return;
            }
            if (GM_Recalc_Field == null) {
                Field field = IsoPuddlesGeometry.class.getDeclaredField("recalc");
                field.setAccessible(true);
                GM_Recalc_Field = field;
            }
            GM_Recalc_Field.setBoolean(puddles, true);
        } catch (Throwable t) {
            GM_Recalc_Failed = true;
            System.out.println("[GL_Java] GM_Environment: puddles on refreshed shore keep their old shape until the area reloads (" + t + ")");
        }
    }
}
