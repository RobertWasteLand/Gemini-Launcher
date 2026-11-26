package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.Lua.LuaManager", methodName = "LoadDirBase", strictMatch = true)
public final class GL_Menu_Lua_Patch {
    private GL_Menu_Lua_Patch() {
    }

    @Patch.OnExit
    public static void GL_Exit() {
        GL_Menu.GL_Lua_Run();
    }
}
