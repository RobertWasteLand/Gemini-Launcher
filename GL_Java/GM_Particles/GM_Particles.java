package GL_Java.GM_Particles;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoCamera;

public final class GM_Particles {
    static final boolean GM_Fewer = Boolean.getBoolean("GM_Particles_Fewer");
    static final String GM_Ready_File = "media/ui/GM_Particles/GM_Particles.lua";
    static final String GM_Test_File = "media/ui/GM_Particles/GM_Particles_Test.lua";
    static final String[][] GM_Hooks = {
        {"zombie.core.skinnedmodel.model.ModelOutlines", "endFrameMain", "1"},
        {"zombie.EffectsManager", "startMuzzleFlash", "2"},
        {"zombie.Lua.LuaManager", "LoadDirBase", "0"},
    };
    static final Map<String, GM_Particles_Def> GM_Defs = new HashMap<>();
    static final Map<GM_Particles_Def, GM_Particles_Pool> GM_Pools = new HashMap<>();
    static final List<GM_Particles_Effect> GM_Effects = new ArrayList<>();
    static final Map<String, List<String>> GM_Links = new HashMap<>();
    static SplittableRandom GM_Random = new SplittableRandom();
    static int GM_Live;
    private static final Set<String> GM_Noted = new HashSet<>();
    private static final float[] GM_Shot_At = new float[5];
    private static volatile boolean GM_Off_Now;
    private static boolean GM_Started;
    private static int GM_Next_Id = 1;
    private static double GM_Time;
    private static final int GM_Ring = 8;
    private static final GM_Particles_Frame[] GM_Frames = new GM_Particles_Frame[GM_Ring];
    private static final GM_Particles_Draw[] GM_Draws = new GM_Particles_Draw[GM_Ring];
    private static int GM_Slot;
    private static int GM_Last_Frame = -1;
    private static long GM_Last_Nanos;

    private GM_Particles() {
    }

    public static void GM_Lua_Load() {
        try {
            if (!GM_Started) {
                GM_Started = true;
                System.out.println("[GL_Java] GM_Particles active (fewer particles " + (GM_Fewer ? "on" : "off") + ")");
                GM_Check(GM_Hooks);
            }
            GM_Clear();
            if (GM_Off_Now) {
                return;
            }
            if (!Boolean.TRUE.equals(LuaManager.RunLua(GM_Ready_File))) {
                GM_Note("GM_Particles.lua", "stopped with an error");
            }
            if (Core.debug && new File(GM_Test_File).exists()) {
                LuaManager.RunLua(GM_Test_File);
            }
        } catch (Throwable t) {
            GM_Off("the Lua load failed (" + t + ")");
        }
    }

    static boolean GM_Define(Object name, Object table) {
        if (GM_Off_Now) {
            return false;
        }
        if (!(name instanceof String text)) {
            GM_Note("GM_Effect_Define", "the name should be text");
            return false;
        }
        GM_Particles_Def def = GM_Particles_Def.GM_Read(text, table);
        if (def == null) {
            GM_Note(text, "the definition should be a table");
            return false;
        }
        GM_Defs.put(text, def);
        return true;
    }

    static String GM_Check(String[][] hooks) {
        for (String[] hook : hooks) {
            if (!GM_Has(hook[0], hook[1], Integer.parseInt(hook[2]))) {
                String why = hook[0] + "." + hook[1] + " is missing";
                GM_Off(why);
                return why;
            }
        }
        return null;
    }

    private static boolean GM_Has(String name, String method, int count) {
        try {
            for (Method m : Class.forName(name, false, GM_Particles.class.getClassLoader()).getDeclaredMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == count) {
                    return true;
                }
            }
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
        return false;
    }

    static void GM_Off(String why) {
        if (GM_Off_Now) {
            return;
        }
        GM_Off_Now = true;
        System.out.println("[GL_Java] GM_Particles off: " + why);
    }

    static boolean GM_Is_Off() {
        return GM_Off_Now;
    }

    static void GM_Note(String name, String problem) {
        if (GM_Noted.add(name + "\n" + problem)) {
            System.out.println("[GL_Java] GM_Particles: " + name + ": " + problem);
        }
    }

    static int GM_Ceiling() {
        return GM_Fewer ? 3000 : 8000;
    }

    static float GM_Rate_Scale() {
        return GM_Fewer ? 0.4f : 1.0f;
    }

    static float GM_Life_Scale() {
        return GM_Fewer ? 0.7f : 1.0f;
    }

    static GM_Particles_Pool GM_Pool(GM_Particles_Def def) {
        return GM_Pools.computeIfAbsent(def, GM_Particles_Pool::new);
    }

    static int GM_Alive() {
        return GM_Live;
    }

    static void GM_Seed(long seed) {
        GM_Random = new SplittableRandom(seed);
    }

    static void GM_Clear() {
        GM_Defs.clear();
        GM_Pools.clear();
        GM_Effects.clear();
        GM_Links.clear();
        GM_Live = 0;
    }

    static int GM_Play(String name, float x, float y, float z) {
        GM_Particles_Effect e = GM_Add(name, GM_Particles_Effect.GM_Spot);
        if (e == null) {
            return 0;
        }
        e.x = x;
        e.y = y;
        e.z = z;
        return e.id;
    }

    static int GM_Attach(String name, Object thing, int point, float dx, float dy, float dz) {
        GM_Particles_Effect e = GM_Add(name, GM_Particles_Effect.GM_Attach);
        if (e == null) {
            return 0;
        }
        e.thing = thing;
        e.point = point;
        e.item = point == GM_Particles_Point.GM_Tip ? ((IsoGameCharacter) thing).getPrimaryHandItem() : null;
        e.dx = dx;
        e.dy = dy;
        e.dz = dz;
        return e.id;
    }

    static int GM_Fly(String name, float x1, float y1, float z1, float x2, float y2, float z2, float speed, String landing) {
        GM_Particles_Effect e = GM_Add(name, GM_Particles_Effect.GM_Fly);
        if (e == null) {
            return 0;
        }
        e.x = e.fromX = x1;
        e.y = e.fromY = y1;
        e.z = e.fromZ = z1;
        e.toX = x2;
        e.toY = y2;
        e.toZ = z2;
        e.flight = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1)) / speed;
        float flat = (float) Math.hypot(x2 - x1, y2 - y1);
        if (flat > 1.0e-4f) {
            e.dirX = (x2 - x1) / flat;
            e.dirY = (y2 - y1) / flat;
        }
        e.landing = landing;
        return e.id;
    }

    private static GM_Particles_Effect GM_Add(String name, int kind) {
        if (GM_Off_Now) {
            return null;
        }
        GM_Particles_Def def = GM_Defs.get(name);
        if (def == null) {
            GM_Note(name, "no such effect");
            return null;
        }
        if (GM_Effects.size() >= GM_Ceiling()) {
            GM_Note(name, "too many effects running");
            return null;
        }
        GM_Particles_Effect e = new GM_Particles_Effect(GM_Next_Id++, kind, def);
        GM_Effects.add(e);
        return e;
    }

    static void GM_Stop(int id) {
        GM_Effects.removeIf(e -> e.id == id);
    }

    static boolean GM_Weapon(String item, String name) {
        if (GM_Off_Now) {
            return false;
        }
        if (name.isEmpty()) {
            GM_Links.remove(item);
            return true;
        }
        if (!GM_Defs.containsKey(name)) {
            GM_Note(name, "no such effect");
            return false;
        }
        List<String> names = GM_Links.computeIfAbsent(item, k -> new ArrayList<>());
        if (!names.contains(name)) {
            names.add(name);
        }
        return true;
    }

    public static void GM_Weapon_Fired(Object character) {
        try {
            if (GM_Off_Now || GM_Links.isEmpty() || !(character instanceof IsoGameCharacter shooter)) {
                return;
            }
            InventoryItem item = shooter.getPrimaryHandItem();
            List<String> names = item == null ? null : GM_Links.get(item.getFullType());
            if (names == null || !GM_Particles_Point.GM_Find(shooter, GM_Particles_Point.GM_Tip, item, 0.0f, 0.0f, 0.0f, GM_Shot_At)) {
                return;
            }
            for (String name : names) {
                GM_Particles_Effect e = GM_Add(name, GM_Particles_Effect.GM_Shot);
                if (e != null) {
                    e.x = GM_Shot_At[0];
                    e.y = GM_Shot_At[1];
                    e.z = GM_Shot_At[2];
                    e.dirX = GM_Shot_At[3];
                    e.dirY = GM_Shot_At[4];
                }
            }
        } catch (Throwable t) {
            GM_Off("the shot hook failed (" + t + ")");
        }
    }

    static void GM_Step(float dt) {
        GM_Time += dt;
        GM_Particles_Wind.GM_Update((float) GM_Time);
        int i = 0;
        while (i < GM_Effects.size()) {
            if (GM_Effects.get(i).GM_Step(dt)) {
                i++;
            } else {
                int last = GM_Effects.size() - 1;
                GM_Effects.set(i, GM_Effects.get(last));
                GM_Effects.remove(last);
            }
        }
        float windX = GM_Particles_Wind.GM_X();
        float windY = GM_Particles_Wind.GM_Y();
        Iterator<GM_Particles_Pool> pools = GM_Pools.values().iterator();
        while (pools.hasNext()) {
            GM_Particles_Pool pool = pools.next();
            pool.GM_Step(dt, windX, windY);
            if (pool.count == 0 && GM_Defs.get(pool.def.name) != pool.def) {
                pools.remove();
            }
        }
    }

    static int GM_Running() {
        return GM_Effects.size();
    }

    public static void GM_Frame() {
        if (GM_Off_Now) {
            return;
        }
        try {
            long start = System.nanoTime();
            IsoCamera.FrameState state = IsoCamera.frameState;
            if (state.frameCount != GM_Last_Frame) {
                GM_Last_Frame = state.frameCount;
                float dt = GM_Last_Nanos == 0 ? 0.016f : Math.min(0.1f, (start - GM_Last_Nanos) / 1.0e9f);
                GM_Last_Nanos = start;
                GM_Step(GameTime.isGamePaused() ? 0.0f : dt);
            }
            if (GM_Live == 0) {
                return;
            }
            GM_Slot = (GM_Slot + 1) % GM_Ring;
            if (GM_Frames[GM_Slot] == null) {
                GM_Frames[GM_Slot] = new GM_Particles_Frame();
                GM_Draws[GM_Slot] = new GM_Particles_Draw();
            }
            GM_Particles_Frame frame = GM_Frames[GM_Slot];
            frame.GM_Fill();
            frame.updateNanos = System.nanoTime() - start;
            SpriteRenderer.instance.drawGeneric(GM_Draws[GM_Slot].GM_Use(frame));
        } catch (Throwable t) {
            GM_Off("the frame failed (" + t + ")");
        }
    }
}
