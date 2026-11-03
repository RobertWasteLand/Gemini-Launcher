package fmod;

public class javafmod {
    public static long FMOD_System_CreateSound(String file, long mode) {
        return 0L;
    }

    public static long FMOD_System_PlaySound(long sound, boolean paused) {
        return 0L;
    }

    public static int FMOD_Channel_SetVolume(long channel, float volume) {
        return 0;
    }

    public static int FMOD_Channel_SetPaused(long channel, boolean paused) {
        return 0;
    }

    public static int FMOD_Channel_Stop(long channel) {
        return 0;
    }

    public static boolean FMOD_Channel_IsPlaying(long channel) {
        return false;
    }

    public static long FMOD_Channel_GetPosition(long channel, int unit) {
        return 0L;
    }
}
