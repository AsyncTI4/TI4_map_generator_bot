package ti4.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.helpers.ResourceHelper;

public class BorderAnomalyModel {

    public enum BorderAnomalyType {
        // homebrew
        ASTEROID("Asteroid Field", "asteroid_border.png"), //
        GRAVITY_WAVE("Gravity Wave", "gravity_wave_border.png"), //
        NEBULA("Nebula", "nebula_border.png"), //
        MINEFIELD("Minefield", "minefield_border.png"), //
        ARROW("Arrow", "adjacency_arrow.png"), //
        SPATIAL_TEAR("Spatial Tear", "spatial_tear_border.png"), //

        // PBD 100 two
        CORE_BORDER("Core border", "core_border.png"), //
        RIM_BORDER("Rim border", "rim_border.png"), //
        YELLOW("Yellow", "yellow.png"), //
        REDORANGE("RedOrange", "redorange.png");

        @Getter
        private final String name;

        @Getter
        private final String imageFilePath;

        BorderAnomalyType(String name, String fileName) {
            this.name = name;
            imageFilePath = ResourceHelper.getResourceFromFolder("borders/", fileName);
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String toSearchString() {
            return toString().toLowerCase().replace("_", "");
        }
    }

    public BorderAnomalyType getBorderAnomalyTypeFromString(String type) {
        if (type == null) {
            return null;
        }
        Map<String, BorderAnomalyType> allTypes = Arrays.stream(BorderAnomalyType.values())
                .collect(Collectors.toMap(BorderAnomalyType::toSearchString, (t -> t)));
        if (allTypes.containsKey(type.toLowerCase())) {
            return allTypes.get(type.toLowerCase());
        }
        return null;
    }
}
