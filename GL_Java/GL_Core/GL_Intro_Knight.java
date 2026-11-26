package GL_Java.GL_Core;

import zombie.core.textures.Texture;

final class GL_Intro_Knight {
    static final int GL_Knight_Rate = 12;
    static final int GL_Knight_Grid = 360;
    static final float GL_Knight_Ground = 0.62f;
    static final float GL_Knight_In_Dither = 0.25f;
    static final float GL_Knight_In_Hold = 0.3f;
    static final float GL_Knight_Speed = 0.5f;
    static final int GL_Knight_Reach = 26;
    static final float GL_Knight_Spawn = 2.0f / GL_Knight_Rate;
    static final float GL_Knight_Lunge_Out = 0.06f;
    static final float GL_Knight_Lunge_Hold = 0.06f;
    static final float GL_Knight_Lunge_Back = 0.1f;
    static final int GL_Knight_Lunge = 5;
    static final float GL_Knight_Die = 4.0f / GL_Knight_Rate;
    static final float GL_Knight_Wave_Gap = 0.3f;
    static final float GL_Knight_Sword_Time = 1.0f;
    static final float GL_Knight_Cloud_Time = 1.0f;
    static final int GL_Knight_Sword_Swap = 3;
    static final int GL_Knight_Cloud_Dither = 3;
    static final int GL_Knight_Cloud_Step = 3;
    static final int[] GL_Knight_Cloud_Drift = {0, 1, 0, -1};
    static final int[] GL_Knight_Cloud_X = {-110, 0, 110};
    static final int[] GL_Knight_Cloud_Y = {12, 0, 20};
    static final int[] GL_Knight_Bolt = {1, 1, 0, 1, 1, 1, 0, 1, 1};
    static final int[][] GL_Knight_Wave_Side = {{1}, {-1, 1}, {1, -1, 1, -1}};
    static final float[][] GL_Knight_Wave_From = {{0.42f}, {0.42f, 0.6f}, {0.4f, 0.52f, 0.66f, 0.8f}};
    static final float[][] GL_Knight_Wave_Delay = {{0.0f}, {0.0f, 0.1f}, {0.0f, 0.05f, 0.1f, 0.15f}};
    static final int GL_Knight_Width = 24;
    static final int GL_Knight_Height = 48;
    static final int GL_Knight_Sword_Height = 63;
    static final int GL_Knight_Sword_Shift = 2;
    static final int GL_Knight_Tip_X = 3;
    static final int GL_Knight_Bolt_Tip_X = 24;
    static final int GL_Knight_Bolt_Tip_Y = 123;
    static final int GL_Knight_Most = 4;

    static final int GL_Phase_In = 0;
    static final int GL_Phase_Waves = 1;
    static final int GL_Phase_Sword = 2;
    static final int GL_Phase_Clouds = 3;
    static final int GL_Phase_Strike = 4;
    static final int GL_Phase_Done = 5;

    static final int GL_None = 0;
    static final int GL_Queued = 1;
    static final int GL_Spawn = 2;
    static final int GL_Walk = 3;
    static final int GL_Ready = 4;
    static final int GL_Die = 5;

    private final GL_Intro_Art art;
    private final int[] state = new int[GL_Knight_Most];
    private final int[] side = new int[GL_Knight_Most];
    private final float[] distance = new float[GL_Knight_Most];
    private final float[] timer = new float[GL_Knight_Most];
    private final float[] readyAt = new float[GL_Knight_Most];
    private int phase = GL_Phase_In;
    private float phaseTime;
    private float time;
    private float swordTime;
    private int wave = -1;
    private float gap;
    private int target = -1;
    private float strike;
    private boolean facingRight;

    GL_Intro_Knight(GL_Intro_Art art) {
        this.art = art;
    }

    boolean GL_Knight_Update(float dt) {
        time += dt;
        phaseTime += dt;
        if (phase >= GL_Phase_Sword && phase <= GL_Phase_Strike) {
            swordTime += dt;
        }
        if (phase == GL_Phase_In) {
            if (phaseTime >= GL_Knight_In_Dither + GL_Knight_In_Hold) {
                GL_Knight_Phase(GL_Phase_Waves);
                GL_Knight_Wave_Start();
            }
        } else if (phase == GL_Phase_Waves) {
            GL_Knight_Waves_Update(dt);
        } else if (phase == GL_Phase_Sword) {
            if (phaseTime >= GL_Knight_Sword_Time) {
                GL_Knight_Phase(GL_Phase_Clouds);
            }
        } else if (phase == GL_Phase_Clouds) {
            if (phaseTime >= GL_Knight_Cloud_Time) {
                GL_Knight_Phase(GL_Phase_Strike);
            }
        } else if (phase == GL_Phase_Strike) {
            if (GL_Knight_Tick(phaseTime) >= GL_Knight_Bolt.length) {
                GL_Knight_Phase(GL_Phase_Done);
            }
        }
        return phase == GL_Phase_Done;
    }

    private void GL_Knight_Phase(int next) {
        phase = next;
        phaseTime = 0.0f;
        if (next == GL_Phase_Sword) {
            facingRight = false;
            GL_Intro_Art.GL_Art_Effect(art.sword);
        }
    }

    private void GL_Knight_Wave_Start() {
        wave++;
        gap = 0.0f;
        for (int i = 0; i < GL_Knight_Most; i++) {
            state[i] = GL_None;
        }
        float half = GL_Knight_Half();
        for (int i = 0; i < GL_Knight_Wave_Side[wave].length; i++) {
            state[i] = GL_Queued;
            side[i] = GL_Knight_Wave_Side[wave][i];
            distance[i] = Math.max(GL_Knight_Reach + 1.0f, GL_Knight_Wave_From[wave][i] * half);
            timer[i] = -GL_Knight_Wave_Delay[wave][i];
        }
    }

    private void GL_Knight_Waves_Update(float dt) {
        float speed = GL_Knight_Speed * GL_Knight_Half();
        boolean busy = target >= 0;
        for (int i = 0; i < GL_Knight_Most; i++) {
            GL_Knight_Skeleton_Update(i, dt, speed);
            busy |= state[i] != GL_None;
        }
        GL_Knight_Fight(dt);
        if (busy) {
            return;
        }
        gap += dt;
        if (gap < GL_Knight_Wave_Gap) {
            return;
        }
        if (wave + 1 < GL_Knight_Wave_Side.length) {
            GL_Knight_Wave_Start();
        } else {
            GL_Knight_Phase(GL_Phase_Sword);
        }
    }

    private void GL_Knight_Skeleton_Update(int i, float dt, float speed) {
        if (state[i] == GL_Queued) {
            timer[i] += dt;
            if (timer[i] >= 0.0f) {
                state[i] = GL_Spawn;
                timer[i] = 0.0f;
            }
        } else if (state[i] == GL_Spawn) {
            timer[i] += dt;
            if (timer[i] >= GL_Knight_Spawn) {
                state[i] = GL_Walk;
            }
        } else if (state[i] == GL_Walk) {
            distance[i] -= speed * dt;
            if (distance[i] <= GL_Knight_Reach) {
                distance[i] = GL_Knight_Reach;
                state[i] = GL_Ready;
                readyAt[i] = time;
            }
        } else if (state[i] == GL_Die) {
            timer[i] += dt;
            if (timer[i] >= GL_Knight_Die) {
                state[i] = GL_None;
            }
        }
    }

    private void GL_Knight_Fight(float dt) {
        if (target < 0) {
            for (int i = 0; i < GL_Knight_Most; i++) {
                if (state[i] == GL_Ready && (target < 0 || readyAt[i] < readyAt[target])) {
                    target = i;
                }
            }
            if (target < 0) {
                return;
            }
            strike = 0.0f;
            facingRight = side[target] > 0;
            GL_Intro_Art.GL_Art_Effect(art.attack);
            return;
        }
        strike += dt;
        if (strike >= GL_Knight_Lunge_Out && state[target] == GL_Ready) {
            state[target] = GL_Die;
            timer[target] = 0.0f;
        }
        if (strike >= GL_Knight_Lunge_Out + GL_Knight_Lunge_Hold + GL_Knight_Lunge_Back) {
            target = -1;
        }
    }

    private float GL_Knight_Lunge_Now() {
        if (target < 0) {
            return 0.0f;
        }
        float reach;
        if (strike < GL_Knight_Lunge_Out) {
            reach = strike / GL_Knight_Lunge_Out;
        } else if (strike < GL_Knight_Lunge_Out + GL_Knight_Lunge_Hold) {
            reach = 1.0f;
        } else {
            reach = Math.max(0.0f, 1.0f - (strike - GL_Knight_Lunge_Out - GL_Knight_Lunge_Hold) / GL_Knight_Lunge_Back);
        }
        return reach * GL_Knight_Lunge * (facingRight ? 1.0f : -1.0f);
    }

    private static int GL_Knight_Tick(float seconds) {
        return (int) (seconds * GL_Knight_Rate);
    }

    static int GL_Knight_Unit() {
        return Math.max(2, GL_Intro_Draw.GL_Screen_H / GL_Knight_Grid);
    }

    private static float GL_Knight_Half() {
        return GL_Intro_Draw.GL_Screen_W / (float) GL_Knight_Unit() / 2.0f - GL_Knight_Width / 2.0f;
    }

    void GL_Knight_Draw() {
        int unit = GL_Knight_Unit();
        int ground = (int) (GL_Intro_Draw.GL_Screen_H * GL_Knight_Ground / unit) * unit;
        int center = GL_Intro_Draw.GL_Screen_W / 2 / unit * unit;
        int standX = center - GL_Knight_Width / 2 * unit;
        int standY = ground - GL_Knight_Height * unit;
        int swordX = standX - GL_Knight_Sword_Shift * unit;
        int swordY = ground - GL_Knight_Sword_Height * unit;
        int boltX = swordX + (GL_Knight_Tip_X - GL_Knight_Bolt_Tip_X) * unit;
        int boltY = swordY - GL_Knight_Bolt_Tip_Y * unit;
        int tick = GL_Knight_Tick(phaseTime);
        if (phase >= GL_Phase_Clouds) {
            Texture cloud = phase == GL_Phase_Clouds && tick < GL_Knight_Cloud_Dither ? art.cloudDither : art.cloud;
            int drift = GL_Knight_Cloud_Drift[GL_Knight_Tick(time) / GL_Knight_Cloud_Step % GL_Knight_Cloud_Drift.length];
            for (int c = 0; c < GL_Knight_Cloud_X.length; c++) {
                int sway = c % 2 == 0 ? drift : -drift;
                GL_Intro_Draw.GL_Sprite(cloud, boltX + (GL_Knight_Cloud_X[c] + sway) * unit, boltY + GL_Knight_Cloud_Y[c] * unit, unit, false);
            }
        }
        if (phase == GL_Phase_Strike && tick < GL_Knight_Bolt.length && GL_Knight_Bolt[tick] == 1) {
            GL_Intro_Draw.GL_Sprite(art.bolt, boltX, boltY, unit, false);
        }
        if (phase == GL_Phase_Waves) {
            for (int i = 0; i < GL_Knight_Most; i++) {
                Texture skeleton = GL_Knight_Skeleton_Frame(i);
                if (skeleton != null) {
                    int x = center + Math.round(side[i] * distance[i] * unit) - GL_Knight_Width / 2 * unit;
                    GL_Intro_Draw.GL_Sprite(skeleton, x, standY, unit, side[i] < 0);
                }
            }
        }
        if (phase == GL_Phase_In) {
            GL_Intro_Draw.GL_Sprite(phaseTime < GL_Knight_In_Dither ? art.knightDither : art.knight, standX, standY, unit, false);
        } else if (phase == GL_Phase_Waves) {
            GL_Intro_Draw.GL_Sprite(art.knight, standX + Math.round(GL_Knight_Lunge_Now() * unit), standY, unit, facingRight);
        } else if (phase == GL_Phase_Strike) {
            GL_Intro_Draw.GL_Sprite(tick % 2 == 0 ? art.swordOneDither : art.swordTwoDither, swordX, swordY, unit, false);
        } else {
            GL_Intro_Draw.GL_Sprite(GL_Knight_Tick(swordTime) / GL_Knight_Sword_Swap % 2 == 0 ? art.swordOne : art.swordTwo, swordX, swordY, unit, false);
        }
    }

    private Texture GL_Knight_Skeleton_Frame(int i) {
        int s = state[i];
        if (s == GL_Spawn) {
            return art.skeletonDither;
        }
        if (s == GL_Walk || s == GL_Ready) {
            return art.skeleton;
        }
        if (s == GL_Die) {
            return GL_Knight_Tick(timer[i]) % 2 == 0 ? art.skeleton : null;
        }
        return null;
    }
}
