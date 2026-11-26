package GL_Java.GM_Particles;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.Lua.LuaManager", methodName = "LoadDirBase", strictMatch = true)
public final class GM_Particles_Lua_Patch {
    private GM_Particles_Lua_Patch() {
    }

    @Patch.OnEnter
    public static void GM_Enter() {
        GM_Particles.GM_Lua_Load();
    }
}
