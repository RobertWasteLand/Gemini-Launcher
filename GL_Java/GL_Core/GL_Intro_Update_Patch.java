package GL_Java.GL_Core;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.gameStates.GameStateMachine;

@Patch(className = "zombie.gameStates.TISLogoState", methodName = "update")
public final class GL_Intro_Update_Patch {
    private GL_Intro_Update_Patch() {
    }

    @Patch.OnExit
    public static void GL_Exit(@Patch.Return(readOnly = false) GameStateMachine.StateAction result) {
        result = GL_Intro.GL_Logo_Update(result);
    }
}
