package ti4.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class TechSpecialtyModel {
    public enum TechSpecialty {
        BIOTIC,
        CYBERNETIC,
        PROPULSION,
        WARFARE,
        UNITSKIP,
        NONUNITSKIP;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public TechSpecialty getTechSpecialtyFromString(String specialty) {
        Map<String, TechSpecialty> allTypes = Arrays.stream(TechSpecialty.values())
                .collect(
                        Collectors.toMap(
                                TechSpecialty::toString,
                                (techSpecialty -> techSpecialty)
                        )
                );
        if (allTypes.containsKey(specialty.toLowerCase()))
            return allTypes.get(specialty.toLowerCase());
        return null;
    }
}
