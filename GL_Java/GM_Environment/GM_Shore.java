package GL_Java.GM_Environment;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.FBORenderChunk;

final class GM_Shore {
    private GM_Shore() {
    }

    static void GM_Refresh() {
        IsoWorld world = IsoWorld.instance;
        IsoCell cell = world == null ? null : world.currentCell;
        if (cell == null || cell.chunkMap == null) {
            return;
        }
        long start = System.nanoTime();
        Set<IsoChunk> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IsoChunk> redraw = Collections.newSetFromMap(new IdentityHashMap<>());
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
                    if (chunk == null) {
                        continue;
                    }
                    seen.add(chunk);
                    if (GM_Wet(chunk)) {
                        GM_Redraw_Around(map, cx, cy, width, redraw);
                    }
                }
            }
        }
        for (IsoChunk chunk : redraw) {
            chunk.invalidateRenderChunkLevel(0, FBORenderChunk.DIRTY_REDRAW);
        }
        System.out.println("[GL_Java] GM_Environment shore refreshed, " + redraw.size() + " chunks redrawn (" + seen.size() + " chunks checked on " + maps + " of " + IsoPlayer.numPlayers + " player maps, grid " + width + "), "
            + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1.0e6) + " ms");
    }

    private static void GM_Redraw_Around(IsoChunkMap map, int cx, int cy, int width, Set<IsoChunk> redraw) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= width || y >= width) {
                    continue;
                }
                IsoChunk near = map.getChunk(x, y);
                if (near != null) {
                    redraw.add(near);
                }
            }
        }
    }

    private static boolean GM_Wet(IsoChunk chunk) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                IsoGridSquare square = chunk.getGridSquare(x, y, 0);
                if (square != null && square.has(IsoFlagType.water)) {
                    return true;
                }
            }
        }
        return false;
    }
}
