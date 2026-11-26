package zombie.gameStates;

public final class GameStateMachine {
    public GameState current;

    public enum StateAction {
        Continue,
        Remain
    }
}
