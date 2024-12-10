package ti4.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import ti4.service.emoji.TechEmojis;

public class TechSpecialtyModel {
    public enum TechSpecialty {
        BIOTIC, CYBERNETIC, PROPULSION, WARFARE, UNITSKIP, NONUNITSKIP;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String getEmoji() {
            return (switch (this) {
                case BIOTIC -> TechEmojis.BioticTech;
                case CYBERNETIC -> TechEmojis.CyberneticTech;
                case PROPULSION -> TechEmojis.PropulsionTech;
                case WARFARE -> TechEmojis.WarfareTech;
                case UNITSKIP -> TechEmojis.UnitTechSkip;
                case NONUNITSKIP -> TechEmojis.NonUnitTechSkip;
            }).toString();
        }
    }

    public TechSpecialty getTechSpecialtyFromString(String specialty) {
        Map<String, TechSpecialty> allTypes = Arrays.stream(TechSpecialty.values())
            .collect(Collectors.toMap(TechSpecialty::toString, (techSpecialty -> techSpecialty)));
        if (allTypes.containsKey(specialty.toLowerCase()))
            return allTypes.get(specialty.toLowerCase());
        return null;
    }
}
