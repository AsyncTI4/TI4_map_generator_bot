package ti4.model;

import ti4.service.emoji.TechEmojis;

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

        public String getEmoji() {
            return (switch (this) {
                        case BIOTIC -> TechEmojis.BioticTech;
                        case CYBERNETIC -> TechEmojis.CyberneticTech;
                        case PROPULSION -> TechEmojis.PropulsionTech;
                        case WARFARE -> TechEmojis.WarfareTech;
                        case UNITSKIP -> TechEmojis.UnitTechSkip;
                        case NONUNITSKIP -> TechEmojis.NonUnitTechSkip;
                    })
                    .toString();
        }
    }
}
