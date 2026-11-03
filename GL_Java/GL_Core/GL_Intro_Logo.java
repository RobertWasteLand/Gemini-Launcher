package GL_Java.GL_Core;

import zombie.core.textures.Texture;

final class GL_Intro_Logo {
    static final float GL_Logo_Start = 0.48f;
    static final float GL_Logo_Snap = 1.90f;
    static final float GL_Logo_Snap_Glare = 1.967f;
    static final float GL_Logo_Settle = 2.00f;
    static final float GL_Logo_Fade = 4.47f;
    static final float GL_Logo_Black = 4.74f;
    static final float GL_Logo_End = 4.95f;
    static final float GL_Logo_Glare_Time = 0.10f;
    static final float GL_Logo_Pop_Time = 0.135f;
    static final float GL_Logo_Cycle = 1.85f;
    static final int GL_Logo_Strip = 2;
    static final int GL_Logo_Pad = 2;
    static final float[] GL_Logo_Pops = {0.48f, 0.68f, 0.87f, 1.03f, 1.25f, 1.44f, 1.63f, 1.80f};
    static final float[] GL_Logo_Syllable_X = {406.0f, 500.0f, 604.0f, 707.0f, 921.0f, 1050.0f, 1196.0f, 1375.0f};
    static final float GL_Logo_Syllable_Y = 763.0f;
    static final float GL_Logo_Horse_X = 809.0f;
    static final float GL_Logo_Horse_Y = 268.0f;
    static final float GL_Logo_Title_X = 707.0f;
    static final float GL_Logo_Title_Y = 555.0f;
    static final float GL_Logo_Text_X = 548.0f;
    static final float GL_Logo_Text_Y = 762.0f;
    static final float GL_Logo_Halo_Radius = 250.0f;
    static final float GL_Logo_Glare_Radius = 60.0f;
    static final float GL_Logo_Bar_Radius = 250.0f;
    static final float GL_Logo_Halo_Full = 1.0f;
    static final float GL_Logo_Halo_Tail = 0.5f;
    static final float GL_Logo_Tail_Shrink = 0.75f;
    static final float GL_Logo_Hot_R = 1.0f;
    static final float GL_Logo_Hot_G = 0.93f;
    static final float GL_Logo_Hot_B = 0.78f;
    static final int GL_Logo_Rest_Stops = 12;
    static final int[] GL_Logo_Palette = {
        0x607ED7, 0x2C9FFF, 0x0BB7FF, 0x13D5E4, 0x2CE38D, 0x39E77E, 0xCAEC49, 0xF8E124, 0xF5C200, 0xF7AD00, 0xFA9500, 0xFD8700,
        0xFC6C00, 0xFE6F38, 0xD9646C, 0xC46185, 0xA15B7E, 0x7B5392
    };

    private GL_Intro_Logo() {
    }

    static void GL_Logo_Draw(GL_Intro_Art art, float t) {
        if (t >= GL_Logo_Black) {
            return;
        }
        float fade = t < GL_Logo_Fade ? 1.0f : 1.0f - (t - GL_Logo_Fade) / (GL_Logo_Black - GL_Logo_Fade);
        float pale = t < GL_Logo_Fade ? 0.0f : Math.min(1.0f, 2.0f * (t - GL_Logo_Fade) / (GL_Logo_Black - GL_Logo_Fade));
        float titleAlpha = t < GL_Logo_Start ? t / GL_Logo_Start : fade;
        GL_Intro_Draw.GL_Piece(art.title, GL_Logo_Title_X, GL_Logo_Title_Y, titleAlpha);
        if (t < GL_Logo_Start) {
            return;
        }
        if (t < GL_Logo_Snap) {
            for (int i = 0; i < GL_Logo_Pops.length; i++) {
                if (t >= GL_Logo_Pops[i]) {
                    GL_Intro_Draw.GL_Piece(art.syllables[i], GL_Logo_Syllable_X[i], GL_Logo_Syllable_Y, 1.0f);
                }
            }
        } else {
            GL_Intro_Draw.GL_Piece(art.text, GL_Logo_Text_X, GL_Logo_Text_Y, fade);
        }
        GL_Logo_Horse(art.horse, t, fade, pale);
        GL_Logo_Glows(art, t);
    }

    private static void GL_Logo_Horse(Texture horse, float t, float fade, float pale) {
        int rows = horse.getHeight();
        int reached = 0;
        while (reached < GL_Logo_Pops.length && t >= GL_Logo_Pops[reached]) {
            reached++;
        }
        int shown = rows * reached / GL_Logo_Pops.length;
        float phase = t > GL_Logo_Settle ? (t - GL_Logo_Settle) / GL_Logo_Cycle : 0.0f;
        int stops = GL_Logo_Palette.length;
        for (int row = 0; row < shown; row += GL_Logo_Strip) {
            int end = Math.min(row + GL_Logo_Strip, shown);
            float along = (row + (end - row) * 0.5f) / rows;
            float stop = along * (GL_Logo_Rest_Stops - 1) - phase * stops;
            stop -= (float) Math.floor(stop / stops) * stops;
            int from = Math.min((int) stop, stops - 1);
            int to = (from + 1) % stops;
            float mix = stop - from;
            float r = GL_Logo_Channel(GL_Logo_Palette[from], GL_Logo_Palette[to], 16, mix);
            float g = GL_Logo_Channel(GL_Logo_Palette[from], GL_Logo_Palette[to], 8, mix);
            float b = GL_Logo_Channel(GL_Logo_Palette[from], GL_Logo_Palette[to], 0, mix);
            float luma = 0.299f * r + 0.587f * g + 0.114f * b;
            r += (luma - r) * pale;
            g += (luma - g) * pale;
            b += (luma - b) * pale;
            GL_Intro_Draw.GL_Piece_Rows(horse, GL_Logo_Horse_X, GL_Logo_Horse_Y, row, end, r, g, b, fade);
        }
    }

    private static float GL_Logo_Channel(int from, int to, int shift, float mix) {
        float a = (from >> shift & 255) / 255.0f;
        float b = (to >> shift & 255) / 255.0f;
        return a + (b - a) * mix;
    }

    private static void GL_Logo_Glows(GL_Intro_Art art, float t) {
        GL_Intro_Draw.GL_Blend_Add();
        for (int i = 0; i < GL_Logo_Pops.length; i++) {
            float age = t - GL_Logo_Pops[i];
            if (age >= 0.0f && age < GL_Logo_Pop_Time) {
                boolean hot = age < GL_Logo_Glare_Time;
                GL_Logo_Halo(art, art.syllables[i], GL_Logo_Syllable_X[i], GL_Logo_Syllable_Y, GL_Logo_Halo_Radius, hot);
            }
        }
        if (t >= GL_Logo_Snap && t < GL_Logo_Settle) {
            GL_Logo_Halo(art, art.text, GL_Logo_Text_X, GL_Logo_Text_Y, GL_Logo_Bar_Radius, t < GL_Logo_Snap_Glare);
        }
        GL_Intro_Draw.GL_Blend_Normal();
        for (int i = 0; i < GL_Logo_Pops.length; i++) {
            float age = t - GL_Logo_Pops[i];
            if (age >= 0.0f && age < GL_Logo_Glare_Time) {
                GL_Logo_Glare(art, art.syllables[i], GL_Logo_Syllable_X[i], GL_Logo_Syllable_Y);
            }
        }
        if (t >= GL_Logo_Snap && t < GL_Logo_Snap_Glare) {
            GL_Logo_Glare(art, art.text, GL_Logo_Text_X, GL_Logo_Text_Y);
        }
    }

    private static void GL_Logo_Halo(GL_Intro_Art art, Texture piece, float x, float y, float size, boolean hot) {
        float radius = size * (hot ? 1.0f : GL_Logo_Tail_Shrink);
        float strength = hot ? GL_Logo_Halo_Full : GL_Logo_Halo_Tail;
        GL_Intro_Draw.GL_Capsule(art.glow, x + GL_Logo_Pad, y + GL_Logo_Pad, x + piece.getWidth() - GL_Logo_Pad, y + piece.getHeight() - GL_Logo_Pad, radius, GL_Intro_Draw.GL_Orange_R, GL_Intro_Draw.GL_Orange_G, GL_Intro_Draw.GL_Orange_B, strength);
    }

    private static void GL_Logo_Glare(GL_Intro_Art art, Texture piece, float x, float y) {
        GL_Intro_Draw.GL_Capsule(art.glare, x + GL_Logo_Pad, y + GL_Logo_Pad, x + piece.getWidth() - GL_Logo_Pad, y + piece.getHeight() - GL_Logo_Pad, GL_Logo_Glare_Radius, GL_Logo_Hot_R, GL_Logo_Hot_G, GL_Logo_Hot_B, 1.0f);
    }
}
