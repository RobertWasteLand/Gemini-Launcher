package GL_Java.GM_Environment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class GM_Features {
    static final String GM_File = "GM_Features.txt";

    static final class GM_Feature {
        final String id;
        final int value;
        final int min;
        final int max;

        GM_Feature(String id, int value, int min, int max) {
            this.id = id;
            this.value = value;
            this.min = min;
            this.max = max;
        }
    }

    static final GM_Feature[] GM_List = GM_Load();
    static final int GM_Water = GM_Index("GM_Water");
    static final int GM_Water_Shore = GM_Index("GM_Water_Shore");
    static final int GM_Water_Style = GM_Index("GM_Water_Style");
    static final int GM_Fog = GM_Index("GM_Fog");
    static final int GM_Fog_Wake = GM_Index("GM_Fog_Wake");
    static final int GM_Reflections = GM_Index("GM_Reflections");
    static final int GM_Glints = GM_Index("GM_Glints");
    static final int GM_Shadows = GM_Index("GM_Shadows");
    static final int GM_Shafts = GM_Index("GM_Shafts");
    static final int GM_Halos = GM_Index("GM_Halos");
    static final int GM_Glow = GM_Index("GM_Glow");
    static final int GM_Grade = GM_Index("GM_Grade");
    static final int GM_Vignette = GM_Index("GM_Vignette");

    private GM_Features() {
    }

    static int GM_Index(String id) {
        for (int i = 0; i < GM_List.length; i++) {
            if (GM_List[i].id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static GM_Feature[] GM_Load() {
        List<GM_Feature> list = new ArrayList<>();
        try (InputStream stream = GM_Features.class.getResourceAsStream(GM_File)) {
            if (stream == null) {
                System.out.println("[GL_Java] GM features: " + GM_File + " is missing from the jar, every effect keeps its built-in state");
                return new GM_Feature[0];
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                GM_Feature feature = GM_Parse(line.split("\\|"));
                if (feature == null) {
                    System.out.println("[GL_Java] GM features: skipped \"" + line.trim() + "\"");
                    continue;
                }
                list.add(feature);
            }
        } catch (Exception e) {
            System.out.println("[GL_Java] GM features: " + GM_File + " could not be read (" + e + ")");
        }
        return list.toArray(new GM_Feature[0]);
    }

    private static GM_Feature GM_Parse(String[] columns) {
        if (columns.length != 6) {
            return null;
        }
        try {
            String id = columns[0].trim();
            String[] kind = columns[1].trim().split("\\s+");
            int value = Integer.parseInt(columns[2].trim());
            String scope = columns[3].trim();
            int min;
            int max;
            switch (kind[0]) {
                case "switch":
                    min = 0;
                    max = 1;
                    break;
                case "level":
                    min = 0;
                    max = 2;
                    break;
                case "slider":
                    if (kind.length != 3) {
                        return null;
                    }
                    min = Integer.parseInt(kind[1]);
                    max = Integer.parseInt(kind[2]);
                    break;
                default:
                    return null;
            }
            if (id.isEmpty() || (!scope.equals("live") && !scope.equals("launch"))) {
                return null;
            }
            if (min > max || value < min || value > max) {
                return null;
            }
            return new GM_Feature(id, value, min, max);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
