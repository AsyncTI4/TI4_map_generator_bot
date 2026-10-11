package ti4.model;

import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;

public class PlanetTypeModel {
    public enum PlanetType {
        CULTURAL,
        HAZARDOUS,
        INDUSTRIAL,
        FAKE,
        SPACESTATION,
        FACTION,
        NONE,
        MR,
        LIGHTNING;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String getEmoji() {
            return (switch (this) {
                        case CULTURAL -> ExploreEmojis.Cultural;
                        case HAZARDOUS -> ExploreEmojis.Hazardous;
                        case INDUSTRIAL -> ExploreEmojis.Industrial;
                        case MR -> PlanetEmojis.Mecatol;
                        case FACTION, NONE, FAKE -> TI4Emoji.getRandomGoodDog(toString());
                        case SPACESTATION -> PlanetEmojis.SpaceStation;
                        case LIGHTNING -> PlanetEmojis.Lightning;
                    })
                    .toString();
        }
    }
}
