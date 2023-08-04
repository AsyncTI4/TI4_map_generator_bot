package ti4.model;

import lombok.Getter;
import ti4.ResourceHelper;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class BorderAnomalyModel {
    public enum BorderAnomalyType {
        ASTEROID("Asteroid Field", "asteroid_border.png"),
        GRAVITY_WAVE("Gravity Wave", "gravity_wave_border.png"),
        ION_STORM("Ion Storm", "ion_storm_border.png"),
        MINEFIELD("Minefield", "minefield_border.png"),
        ARROW("Arrow", "adjacency_arrow.png"),
        NO_SPACE("No-space", "no-space_border.png");

        @Getter
        private final String name;

        @Getter
        private final File imageFile;

        BorderAnomalyType(String name, String fileName) {
            this.name = name;
            String filePath = ResourceHelper.getInstance().getResourceFromFolder("borders/", fileName, "Could not find file");
            this.imageFile = new File(filePath);
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String toSearchString() {
            return this.toString().toLowerCase().replace("_","");
        }
    }

    public BorderAnomalyType getBorderAnomalyTypeFromString(String type) {
        if (type == null) {
            return null;
        }
        BorderAnomalyType.values();
        Map<String, BorderAnomalyType> allTypes = Arrays.stream(BorderAnomalyType.values())
                .collect(
                        Collectors.toMap(
                                BorderAnomalyType::toSearchString,
                                (t -> t)
                        )
                );
        if (allTypes.containsKey(type.toLowerCase()))
            return allTypes.get(type.toLowerCase());
        return null;
    }
}
