package GL_Java.GM_Particles;

import java.util.Arrays;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.iso.IsoCamera;
import zombie.iso.IsoDepthHelper;
import zombie.iso.IsoUtils;

final class GM_Particles_Frame {
    final float[] matrix = new float[16];
    int player;
    int batches;
    GM_Particles_Def[] defs = new GM_Particles_Def[8];
    Texture[] pictures = new Texture[8];
    float[][] data = new float[8][];
    int[] counts = new int[8];
    int alive;
    float soft;
    long updateNanos;

    void GM_Fill() {
        IsoCamera.FrameState state = IsoCamera.frameState;
        this.player = state.playerIndex;
        float left = state.offX;
        float right = left + state.offscreenWidth;
        float top = state.offY;
        float bottom = top + state.offscreenHeight;
        float[] m = this.matrix;
        Arrays.fill(m, 0.0f);
        m[0] = 2.0f / (right - left);
        m[5] = 2.0f / (top - bottom);
        m[10] = -1.0f;
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[15] = 1.0f;
        this.soft = IsoDepthHelper.SQUARE_DEPTH * 0.5f / (64.0f * Core.tileScale);
        int camX = (int) Math.floor(state.camCharacterX);
        int camY = (int) Math.floor(state.camCharacterY);
        this.batches = 0;
        this.alive = 0;
        for (GM_Particles_Pool pool : GM_Particles.GM_Pools.values()) {
            int count = pool.count;
            if (count == 0) {
                continue;
            }
            int b = this.batches++;
            if (b == this.defs.length) {
                this.defs = Arrays.copyOf(this.defs, b * 2);
                this.pictures = Arrays.copyOf(this.pictures, b * 2);
                this.data = Arrays.copyOf(this.data, b * 2);
                this.counts = Arrays.copyOf(this.counts, b * 2);
            }
            float[] d = this.data[b];
            if (d == null || d.length < count * 8) {
                d = new float[Math.max(count, 64) * 8];
                this.data[b] = d;
            }
            int i = 0;
            for (int k = 0; k < count; k++) {
                float x = pool.x[k];
                float y = pool.y[k];
                float z = pool.z[k];
                d[i++] = IsoUtils.XToScreen(x, y, z, 0);
                d[i++] = IsoUtils.YToScreen(x, y, z, 0);
                d[i++] = IsoDepthHelper.getSquareDepthData(camX, camY, x, y, z).depthStart;
                d[i++] = pool.age[k] / pool.life[k];
                d[i++] = pool.angle[k];
                d[i++] = pool.r[k];
                d[i++] = pool.g[k];
                d[i++] = pool.b[k];
            }
            this.defs[b] = pool.def;
            this.pictures[b] = GM_Picture_Ready(pool.def) ? pool.def.texture : null;
            this.counts[b] = count;
            this.alive += count;
        }
    }

    private static boolean GM_Picture_Ready(GM_Particles_Def def) {
        if (def.picture == null || def.pictureMissing) {
            return false;
        }
        if (def.texture == null) {
            def.texture = Texture.getSharedTexture(def.picture, def.crisp ? 3 : 0);
        }
        if (def.texture == null || def.texture.isFailure()) {
            def.pictureMissing = true;
            GM_Particles.GM_Note(def.name, "picture " + def.picture + " not found");
            return false;
        }
        return def.texture.isReady();
    }
}
