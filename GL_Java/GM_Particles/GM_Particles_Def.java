package GL_Java.GM_Particles;

import java.util.Arrays;
import java.util.Set;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;

final class GM_Particles_Def {
    private static final Set<String> GM_Settings = Set.of(
        "burst", "rate", "duration", "life", "size", "color", "alpha", "fadein", "spin", "spread", "rise",
        "drift", "gravity", "wind", "push", "glow", "lit", "picture", "crisp", "follow", "arc");
    private static final float GM_Any = Float.MAX_VALUE;

    String name;
    int burst;
    float rate;
    float duration;
    float lifeMin;
    float lifeMax;
    float sizeStart;
    float sizeEnd;
    final float[] colors = {1, 1, 1, 1, 1, 1, 1, 1, 1};
    int colorCount = 1;
    float alpha;
    float fadein;
    float spin;
    float spread;
    float riseMin;
    float riseMax;
    float drift;
    float gravity;
    float wind;
    float push;
    float arc;
    boolean glow;
    boolean lit;
    boolean crisp;
    boolean follow;
    String picture;
    zombie.core.textures.Texture texture;
    boolean pictureMissing;

    private GM_Particles_Def() {
    }

    static GM_Particles_Def GM_Read(String name, Object table) {
        if (!(table instanceof KahluaTable t)) {
            return null;
        }
        GM_Particles_Def d = new GM_Particles_Def();
        d.name = name;
        KahluaTableIterator it = t.iterator();
        while (it.advance()) {
            Object key = it.getKey();
            if (!(key instanceof String) || !GM_Settings.contains(key)) {
                GM_Particles.GM_Note(name, "unknown setting \"" + key + "\"");
            }
        }
        d.burst = Math.round(d.GM_Number(t, "burst", 0, 0, 8000));
        d.rate = d.GM_Number(t, "rate", 0, 0, 2000);
        d.duration = d.GM_Number(t, "duration", 0, 0, GM_Any);
        float[] pair = d.GM_Pair(t, "life", "{min, max}", 1, 0, 30);
        d.lifeMin = Math.min(pair[0], pair[1]);
        d.lifeMax = Math.max(pair[0], pair[1]);
        pair = d.GM_Pair(t, "size", "{start, end}", 8, 0, 512);
        d.sizeStart = pair[0];
        d.sizeEnd = pair[1];
        pair = d.GM_Pair(t, "rise", "{min, max}", 0, -GM_Any, GM_Any);
        d.riseMin = Math.min(pair[0], pair[1]);
        d.riseMax = Math.max(pair[0], pair[1]);
        d.alpha = d.GM_Number(t, "alpha", 1, 0, 1);
        d.fadein = d.GM_Number(t, "fadein", 0.1f, 0, 1);
        d.spin = d.GM_Number(t, "spin", 0, -GM_Any, GM_Any);
        d.spread = d.GM_Number(t, "spread", 0, 0, GM_Any);
        d.drift = d.GM_Number(t, "drift", 0, 0, GM_Any);
        d.gravity = d.GM_Number(t, "gravity", 0, -GM_Any, GM_Any);
        d.wind = d.GM_Number(t, "wind", 0, 0, 1);
        d.push = d.GM_Number(t, "push", 0, -GM_Any, GM_Any);
        d.arc = d.GM_Number(t, "arc", 0, -GM_Any, GM_Any);
        d.glow = d.GM_Flag(t, "glow");
        d.lit = d.GM_Flag(t, "lit");
        d.crisp = d.GM_Flag(t, "crisp");
        d.follow = d.GM_Flag(t, "follow");
        Object picture = t.rawget("picture");
        if (picture != null && !(picture instanceof String)) {
            GM_Particles.GM_Note(name, "picture should be text, using the soft dot");
        }
        d.picture = picture instanceof String text ? text : null;
        d.GM_Colors(t.rawget("color"));
        return d;
    }

    private float GM_Number(KahluaTable t, String key, float fallback, float min, float max) {
        Object value = t.rawget(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Double number)) {
            GM_Particles.GM_Note(this.name, key + " should be a number, using " + GM_Text(fallback));
            return fallback;
        }
        return this.GM_Clamp(key, number.floatValue(), min, max);
    }

    private float[] GM_Pair(KahluaTable t, String key, String pair, float fallback, float min, float max) {
        Object value = t.rawget(key);
        if (value == null) {
            return new float[] {fallback, fallback};
        }
        if (value instanceof Double number) {
            float one = this.GM_Clamp(key, number.floatValue(), min, max);
            return new float[] {one, one};
        }
        if (value instanceof KahluaTable both && both.rawget(1) instanceof Double a && both.rawget(2) instanceof Double b) {
            return new float[] {this.GM_Clamp(key, a.floatValue(), min, max), this.GM_Clamp(key, b.floatValue(), min, max)};
        }
        GM_Particles.GM_Note(this.name, key + " should be a number or " + pair + ", using " + GM_Text(fallback));
        return new float[] {fallback, fallback};
    }

    private float GM_Clamp(String key, float value, float min, float max) {
        if (value > max) {
            GM_Particles.GM_Note(this.name, key + " " + GM_Text(value) + " is above " + GM_Text(max) + ", using " + GM_Text(max));
            return max;
        }
        if (value < min) {
            GM_Particles.GM_Note(this.name, key + " " + GM_Text(value) + " is below " + GM_Text(min) + ", using " + GM_Text(min));
            return min;
        }
        return value;
    }

    private boolean GM_Flag(KahluaTable t, String key) {
        Object value = t.rawget(key);
        if (value != null && !(value instanceof Boolean)) {
            GM_Particles.GM_Note(this.name, key + " should be true or false, using false");
        }
        return Boolean.TRUE.equals(value);
    }

    private void GM_Colors(Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof KahluaTable list && this.GM_Color(list, 0)) {
            this.colorCount = 1;
            return;
        }
        if (value instanceof KahluaTable list && list.len() >= 1 && list.len() <= 3) {
            int count = list.len();
            for (int i = 0; i < count; i++) {
                if (!(list.rawget(i + 1) instanceof KahluaTable one) || !this.GM_Color(one, i)) {
                    count = 0;
                    break;
                }
            }
            if (count > 0) {
                this.colorCount = count;
                return;
            }
        }
        Arrays.fill(this.colors, 1);
        this.colorCount = 1;
        GM_Particles.GM_Note(this.name, "color should be a colour {r, g, b} or up to three colours, using white");
    }

    private boolean GM_Color(KahluaTable one, int slot) {
        if (!(one.rawget(1) instanceof Double r) || !(one.rawget(2) instanceof Double g) || !(one.rawget(3) instanceof Double b)) {
            return false;
        }
        this.colors[slot * 3] = r.floatValue();
        this.colors[slot * 3 + 1] = g.floatValue();
        this.colors[slot * 3 + 2] = b.floatValue();
        return true;
    }

    private static String GM_Text(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0e9 ? Long.toString((long) value) : Float.toString((float) value);
    }
}
