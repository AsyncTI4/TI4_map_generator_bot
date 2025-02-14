package ti4.service.emoji;

import org.jetbrains.annotations.NotNull;

public enum TechEmojis implements TI4Emoji {
    PropulsionTech,
    PropulsionDisabled,
    Propulsion2,
    Propulsion3,

    BioticTech,
    BioticDisabled,
    Biotic2,
    Biotic3,

    CyberneticTech,
    CyberneticDisabled,
    Cybernetic2,
    Cybernetic3,

    WarfareTech,
    WarfareDisabled,
    Warfare2,
    Warfare3,

    UnitUpgradeTech,
    UnitTechSkip,
    NonUnitTechSkip;

    @NotNull
    public static TI4Emoji getBasicTechEmoji(String type) {
        type = type.toLowerCase();
        if (type.endsWith("tech")) type.replace("tech", "");
        return switch (type) {
            case "propulsion" -> PropulsionTech;
            case "biotic" -> BioticTech;
            case "cybernetic" -> CyberneticTech;
            case "warfare" -> WarfareTech;
            case "unitupgrade" -> UnitUpgradeTech;
            default -> MiscEmojis.Scout;
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
