package ti4.model;

import lombok.Getter;
import ti4.ResourceHelper;
import ti4.helpers.Helper;

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
        ARROW("Arrow", null),
        NO_SPACE("No-space", "no-space_border.png");

        @Getter
        private final String name;

        @Getter
        private final File imageFile;

        BorderAnomalyType(String name, String fileName) {
            this.name = name;
            this.imageFile = new File(ResourceHelper.getInstance().getResourceFromFolder("border/", fileName, "Could not find file"));
        }
        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String toNameString() {
            return this.getName().toLowerCase().replace(" ","").replace("-","");
        }
    }

    public static BorderAnomalyType getBorderAnomalyTypeFromString(String type) {
        if (type == null) {
            return null;
        }
        Map<String, BorderAnomalyType> allTypes = Arrays.stream(BorderAnomalyType.values())
                .collect(
                        Collectors.toMap(
                                BorderAnomalyType::toNameString,
                                (t -> t)
                        )
                );
        if (allTypes.containsKey(type.toLowerCase()))
            return allTypes.get(type.toLowerCase());
        return null;
    }
}
