package GL_Java.GM_Particles;

import zombie.iso.weather.ClimateManager;

final class GM_Particles_Wind {
    private static float GM_Wind_X;
    private static float GM_Wind_Y;

    private GM_Particles_Wind() {
    }

    static void GM_Update(float time) {
        ClimateManager climate = ClimateManager.getInstance();
        float gust = 0.55f * (float) Math.sin(time * 1.2f)
            + 0.30f * (float) Math.sin(time * 3.0f + 1.7f)
            + 0.15f * (float) Math.sin(time * 6.0f + 0.5f);
        float speed = climate.getWindIntensity() * Math.max(0.0f, 2.0f + gust);
        float angle = climate.getWindAngleRadians();
        GM_Wind_X = (float) Math.cos(angle) * speed;
        GM_Wind_Y = (float) Math.sin(angle) * speed;
    }

    static float GM_X() {
        return GM_Wind_X;
    }

    static float GM_Y() {
        return GM_Wind_Y;
    }
}
