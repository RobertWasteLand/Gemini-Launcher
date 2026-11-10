package GL_Java.GM_Particles;

import java.util.Arrays;
import java.util.SplittableRandom;

final class GM_Particles_Pool {
    private static final float GM_Turn = (float) (Math.PI * 2.0);

    final GM_Particles_Def def;
    int count;
    float[] x = new float[64];
    float[] y = new float[64];
    float[] z = new float[64];
    float[] vx = new float[64];
    float[] vy = new float[64];
    float[] vz = new float[64];
    float[] age = new float[64];
    float[] life = new float[64];
    float[] angle = new float[64];
    float[] spin = new float[64];
    float[] r = new float[64];
    float[] g = new float[64];
    float[] b = new float[64];
    boolean[] outside = new boolean[64];
    int[] owner = new int[64];

    GM_Particles_Pool(GM_Particles_Def def) {
        this.def = def;
    }

    int GM_Spawn(int want, float px, float py, float pz, float dirX, float dirY, float tr, float tg, float tb, boolean out, int who) {
        int made = Math.max(0, Math.min(want, GM_Particles.GM_Ceiling() - GM_Particles.GM_Alive()));
        SplittableRandom random = GM_Particles.GM_Random;
        GM_Particles_Def d = this.def;
        for (int i = 0; i < made; i++) {
            if (this.count == this.x.length) {
                this.GM_Grow();
            }
            int k = this.count++;
            float around = random.nextFloat() * GM_Turn;
            float reach = d.spread * (float) Math.sqrt(random.nextFloat());
            this.x[k] = px + (float) Math.cos(around) * reach;
            this.y[k] = py + (float) Math.sin(around) * reach;
            this.z[k] = pz;
            this.vx[k] = (random.nextFloat() * 2.0f - 1.0f) * d.drift + dirX * d.push;
            this.vy[k] = (random.nextFloat() * 2.0f - 1.0f) * d.drift + dirY * d.push;
            this.vz[k] = d.riseMin + (d.riseMax - d.riseMin) * random.nextFloat();
            this.age[k] = 0.0f;
            this.life[k] = (d.lifeMin + (d.lifeMax - d.lifeMin) * random.nextFloat()) * GM_Particles.GM_Life_Scale();
            this.angle[k] = random.nextFloat() * GM_Turn;
            this.spin[k] = d.spin * GM_Turn * (random.nextBoolean() ? 1.0f : -1.0f);
            this.r[k] = tr;
            this.g[k] = tg;
            this.b[k] = tb;
            this.outside[k] = out;
            this.owner[k] = who;
        }
        GM_Particles.GM_Live += made;
        return made;
    }

    void GM_Step(float dt, float windX, float windY) {
        GM_Particles_Def d = this.def;
        int k = 0;
        while (k < this.count) {
            this.vz[k] -= d.gravity * dt;
            this.x[k] += this.vx[k] * dt;
            this.y[k] += this.vy[k] * dt;
            this.z[k] += this.vz[k] * dt;
            if (this.outside[k]) {
                this.x[k] += windX * d.wind * dt;
                this.y[k] += windY * d.wind * dt;
            }
            this.angle[k] += this.spin[k] * dt;
            this.age[k] += dt;
            if (this.age[k] >= this.life[k]) {
                this.GM_Remove(k);
            } else {
                k++;
            }
        }
    }

    void GM_Follow(int who, float dx, float dy, float dz) {
        for (int k = 0; k < this.count; k++) {
            if (this.owner[k] == who) {
                this.x[k] += dx;
                this.y[k] += dy;
                this.z[k] += dz;
            }
        }
    }

    int GM_Count() {
        return this.count;
    }

    private void GM_Remove(int k) {
        int last = --this.count;
        this.x[k] = this.x[last];
        this.y[k] = this.y[last];
        this.z[k] = this.z[last];
        this.vx[k] = this.vx[last];
        this.vy[k] = this.vy[last];
        this.vz[k] = this.vz[last];
        this.age[k] = this.age[last];
        this.life[k] = this.life[last];
        this.angle[k] = this.angle[last];
        this.spin[k] = this.spin[last];
        this.r[k] = this.r[last];
        this.g[k] = this.g[last];
        this.b[k] = this.b[last];
        this.outside[k] = this.outside[last];
        this.owner[k] = this.owner[last];
        GM_Particles.GM_Live--;
    }

    private void GM_Grow() {
        int size = this.x.length * 2;
        this.x = Arrays.copyOf(this.x, size);
        this.y = Arrays.copyOf(this.y, size);
        this.z = Arrays.copyOf(this.z, size);
        this.vx = Arrays.copyOf(this.vx, size);
        this.vy = Arrays.copyOf(this.vy, size);
        this.vz = Arrays.copyOf(this.vz, size);
        this.age = Arrays.copyOf(this.age, size);
        this.life = Arrays.copyOf(this.life, size);
        this.angle = Arrays.copyOf(this.angle, size);
        this.spin = Arrays.copyOf(this.spin, size);
        this.r = Arrays.copyOf(this.r, size);
        this.g = Arrays.copyOf(this.g, size);
        this.b = Arrays.copyOf(this.b, size);
        this.outside = Arrays.copyOf(this.outside, size);
        this.owner = Arrays.copyOf(this.owner, size);
    }
}
