package GL_Java.GM_Particles;

import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;

final class GM_Particles_Effect {
    static final int GM_Spot = 0;
    static final int GM_Attach = 1;
    static final int GM_Fly = 2;
    static final int GM_Shot = 3;
    private static final float[] GM_Out = new float[5];

    final int id;
    final int kind;
    final GM_Particles_Def def;
    float x;
    float y;
    float z;
    float dirX;
    float dirY;
    Object thing;
    Object item;
    int point;
    float dx;
    float dy;
    float dz;
    float fromX;
    float fromY;
    float fromZ;
    float toX;
    float toY;
    float toZ;
    float flight;
    String landing;
    private double age;
    private float carry;
    private boolean started;

    GM_Particles_Effect(int id, int kind, GM_Particles_Def def) {
        this.id = id;
        this.kind = kind;
        this.def = def;
    }

    boolean GM_Step(float dt) {
        GM_Particles_Def d = this.def;
        boolean endsWithLife = d.rate == 0.0f || this.kind == GM_Shot && d.duration == 0.0f;
        if (d.duration > 0.0f && this.age >= d.duration || this.kind != GM_Fly && endsWithLife && this.age >= d.lifeMax * GM_Particles.GM_Life_Scale()) {
            return false;
        }
        float lastX = this.x;
        float lastY = this.y;
        float lastZ = this.z;
        if (this.kind == GM_Attach) {
            if (!GM_Particles_Point.GM_Find(this.thing, this.point, this.item, this.dx, this.dy, this.dz, GM_Out)) {
                return false;
            }
            this.x = GM_Out[0];
            this.y = GM_Out[1];
            this.z = GM_Out[2];
            this.dirX = GM_Out[3];
            this.dirY = GM_Out[4];
        } else if (this.kind == GM_Fly) {
            float t = this.flight > 0.0f ? (float) Math.min(1.0, this.age / this.flight) : 1.0f;
            this.x = this.fromX + (this.toX - this.fromX) * t;
            this.y = this.fromY + (this.toY - this.fromY) * t;
            this.z = this.fromZ + (this.toZ - this.fromZ) * t + d.arc * 4.0f * t * (1.0f - t);
        }
        GM_Particles_Pool pool = GM_Particles.GM_Pools.get(d);
        if (d.follow && this.started && pool != null) {
            pool.GM_Follow(this.id, this.x - lastX, this.y - lastY, this.z - lastZ);
        }
        int count = 0;
        if (!this.started) {
            this.started = true;
            count = d.burst > 0 ? Math.max(1, Math.round(d.burst * GM_Particles.GM_Rate_Scale())) : 0;
        }
        this.carry += d.rate * GM_Particles.GM_Rate_Scale() * dt;
        int whole = (int) this.carry;
        this.carry -= whole;
        count += whole;
        if (count > 0) {
            this.GM_Spawn(pool != null ? pool : GM_Particles.GM_Pool(d), count);
        }
        this.age += dt;
        if (this.kind == GM_Fly && this.age >= this.flight) {
            if (this.landing != null) {
                GM_Particles.GM_Play(this.landing, this.toX, this.toY, this.toZ);
            }
            return false;
        }
        return true;
    }

    private void GM_Spawn(GM_Particles_Pool pool, int count) {
        IsoWorld world = IsoWorld.instance;
        IsoCell cell = world == null ? null : world.currentCell;
        IsoGridSquare square = cell == null ? null : cell.getGridSquare((int) Math.floor(this.x), (int) Math.floor(this.y), (int) Math.floor(this.z));
        ColorInfo light = this.def.lit && square != null ? square.getLightInfo(IsoCamera.frameState.playerIndex) : null;
        float r = light == null ? 1.0f : light.r;
        float g = light == null ? 1.0f : light.g;
        float b = light == null ? 1.0f : light.b;
        pool.GM_Spawn(count, this.x, this.y, this.z, this.dirX, this.dirY, r, g, b, square == null || square.isOutside(), this.id);
    }
}
