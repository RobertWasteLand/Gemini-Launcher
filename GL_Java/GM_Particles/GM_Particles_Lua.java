package GL_Java.GM_Particles;

import se.krka.kahlua.integration.annotations.LuaMethod;

public final class GM_Particles_Lua {
    public GM_Particles_Lua() {
    }

    @LuaMethod(name = "GM_Effect_Define", global = true)
    public Object GM_Effect_Define(Object... args) {
        try {
            return GM_Particles.GM_Define(GM_Arg(args, 0), GM_Arg(args, 1)) ? Boolean.TRUE : null;
        } catch (Throwable t) {
            GM_Particles.GM_Off("GM_Effect_Define failed (" + t + ")");
            return null;
        }
    }

    @LuaMethod(name = "GM_Effect_Play", global = true)
    public Object GM_Effect_Play(Object... args) {
        try {
            String f = "GM_Effect_Play";
            if (GM_Particles.GM_Is_Off() || !GM_Text(f, args, 0, "name") || !GM_Numbers(f, args, 1, "x", "y", "z")) {
                return null;
            }
            return GM_Id(GM_Particles.GM_Play((String) args[0], GM_Float(args, 1), GM_Float(args, 2), GM_Float(args, 3)));
        } catch (Throwable t) {
            GM_Particles.GM_Off("GM_Effect_Play failed (" + t + ")");
            return null;
        }
    }

    @LuaMethod(name = "GM_Effect_Attach", global = true)
    public Object GM_Effect_Attach(Object... args) {
        try {
            String f = "GM_Effect_Attach";
            if (GM_Particles.GM_Is_Off() || !GM_Text(f, args, 0, "name")) {
                return null;
            }
            Object thing = GM_Arg(args, 1);
            int kind = GM_Particles_Point.GM_Point_Of(thing, null);
            if (kind < 0) {
                GM_Particles.GM_Note(f, "thing should be a character or a placed object");
                return null;
            }
            int point = GM_Particles_Point.GM_Point_Of(thing, GM_Arg(args, 2));
            if (point < 0) {
                GM_Particles.GM_Note(f, kind == GM_Particles_Point.GM_Object ? "point should be nil on a placed object" : "point should be \"feet\", \"head\", \"left hand\", \"right hand\" or \"tip\"");
                return null;
            }
            String[] offsets = {"dx", "dy", "dz"};
            for (int i = 0; i < offsets.length; i++) {
                Object value = GM_Arg(args, 3 + i);
                if (value != null && !(value instanceof Double)) {
                    GM_Particles.GM_Note(f, offsets[i] + " should be a number");
                    return null;
                }
            }
            return GM_Id(GM_Particles.GM_Attach((String) args[0], thing, point, GM_Float(args, 3), GM_Float(args, 4), GM_Float(args, 5)));
        } catch (Throwable t) {
            GM_Particles.GM_Off("GM_Effect_Attach failed (" + t + ")");
            return null;
        }
    }

    @LuaMethod(name = "GM_Effect_Fly", global = true)
    public Object GM_Effect_Fly(Object... args) {
        try {
            String f = "GM_Effect_Fly";
            if (GM_Particles.GM_Is_Off() || !GM_Text(f, args, 0, "name") || !GM_Numbers(f, args, 1, "x1", "y1", "z1", "x2", "y2", "z2")) {
                return null;
            }
            if (!(GM_Arg(args, 7) instanceof Double speed) || !(speed > 0.0)) {
                GM_Particles.GM_Note(f, "speed should be a number above 0");
                return null;
            }
            Object landing = GM_Arg(args, 8);
            if (landing != null && !(landing instanceof String)) {
                GM_Particles.GM_Note(f, "landing should be text");
                return null;
            }
            return GM_Id(GM_Particles.GM_Fly((String) args[0], GM_Float(args, 1), GM_Float(args, 2), GM_Float(args, 3), GM_Float(args, 4), GM_Float(args, 5), GM_Float(args, 6), speed.floatValue(), (String) landing));
        } catch (Throwable t) {
            GM_Particles.GM_Off("GM_Effect_Fly failed (" + t + ")");
            return null;
        }
    }

    @LuaMethod(name = "GM_Effect_Stop", global = true)
    public Object GM_Effect_Stop(Object... args) {
        try {
            if (!GM_Particles.GM_Is_Off() && GM_Numbers("GM_Effect_Stop", args, 0, "id")) {
                GM_Particles.GM_Stop(((Double) args[0]).intValue());
            }
        } catch (Throwable t) {
            GM_Particles.GM_Off("GM_Effect_Stop failed (" + t + ")");
        }
        return null;
    }

    @LuaMethod(name = "GM_Effect_Weapon", global = true)
    public Object GM_Effect_Weapon(Object... args) {
        try {
            String f = "GM_Effect_Weapon";
            if (GM_Particles.GM_Is_Off() || !GM_Text(f, args, 0, "item") || !GM_Text(f, args, 1, "name")) {
                return null;
            }
            return GM_Particles.GM_Weapon((String) args[0], (String) args[1]) ? Boolean.TRUE : null;
        } catch (Throwable t) {
            GM_Particles.GM_Off("GM_Effect_Weapon failed (" + t + ")");
            return null;
        }
    }

    static Object GM_Arg(Object[] args, int index) {
        return args != null && index < args.length ? args[index] : null;
    }

    private static boolean GM_Text(String function, Object[] args, int index, String label) {
        if (GM_Arg(args, index) instanceof String) {
            return true;
        }
        GM_Particles.GM_Note(function, label + " should be text");
        return false;
    }

    private static boolean GM_Numbers(String function, Object[] args, int from, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            if (!(GM_Arg(args, from + i) instanceof Double)) {
                GM_Particles.GM_Note(function, labels[i] + " should be a number");
                return false;
            }
        }
        return true;
    }

    private static float GM_Float(Object[] args, int index) {
        return GM_Arg(args, index) instanceof Double d ? d.floatValue() : 0.0f;
    }

    private static Object GM_Id(int id) {
        return id > 0 ? Double.valueOf(id) : null;
    }
}
