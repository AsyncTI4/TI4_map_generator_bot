package ti4.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class PlanetTypeModel {
    public enum PlanetType {
        CULTURAL, HAZARDOUS, INDUSTRIAL, FAKE, FACTION, NONE, MR;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public PlanetType getPlanetTypeFromString(String type) {
        if (type == null) {
            return PlanetType.NONE;
        }
        Map<String, PlanetType> allTypes = Arrays.stream(PlanetType.values())
            .collect(Collectors.toMap(PlanetType::toString, (t -> t)));
        if (allTypes.containsKey(type.toLowerCase())) {
            return allTypes.get(type.toLowerCase());
        }
        return PlanetType.NONE;
    }
}
