package GL_Java.GL_Core;

import java.io.File;

import fmod.javafmod;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.gameStates.ChooseGameInfo;

final class GL_Intro_Art {
    static final String GL_Art_Mod = "GM_Main_Menu";
    static final String GL_Art_Pictures = "media/ui/GM_Main_Menu/";
    static final String GL_Art_Sounds = "media/sound/GM_Main_Menu/";
    static final int GL_Art_Smooth = 0;
    static final int GL_Art_Pixel = 3;
    static final long GL_Art_Sound_2D = 8L;
    static final int GL_Art_Syllables = 8;

    final String common;
    final Texture warning;
    final Texture logos;
    final Texture buddy;
    final Texture horse;
    final Texture title;
    final Texture text;
    final Texture glow;
    final Texture glare;
    final Texture[] syllables = new Texture[GL_Art_Syllables];
    final Texture knight;
    final Texture knightDither;
    final Texture swordOne;
    final Texture swordTwo;
    final Texture swordOneDither;
    final Texture swordTwoDither;
    final Texture skeleton;
    final Texture skeletonDither;
    final Texture cloud;
    final Texture cloudDither;
    final Texture bolt;
    final long jingle;
    final long attack;
    final long sword;
    final long flash;
    private final Texture[] all;

    private GL_Intro_Art(File dir) {
        common = dir.getAbsolutePath();
        int crisp = GL_Art_Crisp();
        warning = GL_Art_Picture(dir, "GM_Legal_Warning", crisp);
        logos = GL_Art_Picture(dir, "GM_Company_Logos", crisp);
        buddy = GL_Art_Picture(dir, "GM_Zombie_Buddy", crisp);
        horse = GL_Art_Picture(dir, "GM_Logo_Horse", crisp);
        title = GL_Art_Picture(dir, "GM_Logo_Title", crisp);
        text = GL_Art_Picture(dir, "GM_Logo_Text", crisp);
        glow = GL_Art_Picture(dir, "GM_Logo_Glow", GL_Art_Smooth);
        glare = GL_Art_Picture(dir, "GM_Logo_Glare", GL_Art_Smooth);
        for (int i = 0; i < GL_Art_Syllables; i++) {
            syllables[i] = GL_Art_Picture(dir, "GM_Logo_Syllable_" + (i + 1), crisp);
        }
        knight = GL_Art_Picture(dir, "GM_Knight", GL_Art_Pixel);
        knightDither = GL_Art_Picture(dir, "GM_Knight_In_Shadow", GL_Art_Pixel);
        swordOne = GL_Art_Picture(dir, "GM_Knight_Sword_Up_One", GL_Art_Pixel);
        swordTwo = GL_Art_Picture(dir, "GM_Knight_Sword_Up_Two", GL_Art_Pixel);
        swordOneDither = GL_Art_Picture(dir, "GM_Knight_Sword_Up_One_Shadow", GL_Art_Pixel);
        swordTwoDither = GL_Art_Picture(dir, "GM_Knight_Sword_Up_Two_Shadow", GL_Art_Pixel);
        skeleton = GL_Art_Picture(dir, "GM_Skeleton", GL_Art_Pixel);
        skeletonDither = GL_Art_Picture(dir, "GM_Skeleton_Shadow", GL_Art_Pixel);
        cloud = GL_Art_Picture(dir, "GM_Cloud", GL_Art_Pixel);
        cloudDither = GL_Art_Picture(dir, "GM_Cloud_Shadow", GL_Art_Pixel);
        bolt = GL_Art_Picture(dir, "GM_Lightning_Strike", GL_Art_Pixel);
        all = new Texture[] {warning, logos, buddy, horse, title, text, glow, glare, syllables[0], syllables[1], syllables[2], syllables[3], syllables[4], syllables[5], syllables[6], syllables[7], knight, knightDither, swordOne, swordTwo, swordOneDither, swordTwoDither, skeleton, skeletonDither, cloud, cloudDither, bolt};
        boolean quiet = Core.soundDisabled;
        jingle = quiet ? 0L : GL_Art_Sound(dir, "GM_Gemini_Jingle.ogg");
        attack = quiet ? 0L : GL_Art_Sound(dir, "GM_Knight_Attack_Effect.wav");
        sword = quiet ? 0L : GL_Art_Sound(dir, "GM_Knight_Sword_In_Sky.wav");
        flash = quiet ? 0L : GL_Art_Sound(dir, "GM_Flashs_To_Main_Menu.wav");
    }

    private static int GL_Art_Crisp() {
        Core core = Core.getInstance();
        float fit = Math.min(core.getScreenWidth() / GL_Intro_Draw.GL_Stage_W, core.getScreenHeight() / GL_Intro_Draw.GL_Stage_H);
        return fit >= 2.0f ? GL_Art_Pixel : GL_Art_Smooth;
    }

    static GL_Intro_Art GL_Art_Load() {
        ChooseGameInfo.Mod mod = ChooseGameInfo.getModDetails(GL_Art_Mod);
        if (mod == null || mod.getCommonDir() == null) {
            throw new IllegalStateException("the " + GL_Art_Mod + " mod is not on this PC");
        }
        return new GL_Intro_Art(new File(mod.getCommonDir()));
    }

    boolean GL_Art_Ready() {
        for (Texture texture : all) {
            if (texture.isFailure()) {
                throw new IllegalStateException("a picture from the " + GL_Art_Mod + " mod did not load");
            }
            if (!texture.isReady()) {
                return false;
            }
        }
        return true;
    }

    boolean GL_Art_Sound_On() {
        return jingle != 0L || attack != 0L || sword != 0L || flash != 0L;
    }

    static long GL_Art_Play(long sound, int volume) {
        if (sound == 0L) {
            return 0L;
        }
        long channel = javafmod.FMOD_System_PlaySound(sound, true);
        javafmod.FMOD_Channel_SetVolume(channel, volume / 10.0f);
        javafmod.FMOD_Channel_SetPaused(channel, false);
        return channel;
    }

    static void GL_Art_Effect(long sound) {
        GL_Art_Play(sound, Core.getInstance().getOptionSoundVolume());
    }

    private static Texture GL_Art_Picture(File dir, String name, int flags) {
        File file = new File(dir, GL_Art_Pictures + name + ".png");
        if (!file.isFile()) {
            throw new IllegalStateException(file.getName() + " is missing from " + dir);
        }
        Texture texture = Texture.getSharedTexture(file.getAbsolutePath(), flags);
        if (texture == null) {
            throw new IllegalStateException("the game would not load " + file.getName());
        }
        return texture;
    }

    private static long GL_Art_Sound(File dir, String name) {
        File file = new File(dir, GL_Art_Sounds + name);
        if (!file.isFile()) {
            throw new IllegalStateException(file.getName() + " is missing from " + dir);
        }
        return javafmod.FMOD_System_CreateSound(file.getAbsolutePath(), GL_Art_Sound_2D);
    }
}
