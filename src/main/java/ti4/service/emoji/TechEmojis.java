package ti4.service.emoji;

import org.jetbrains.annotations.NotNull;

public enum TechEmojis implements TI4Emoji {
    PropulsionPropulsion,
    PropulsionTech,
    PropulsionDisabled,
    Propulsion2,
    Propulsion3,

    BioticBiotic,
    BioticPropulsion,
    BioticWarfare,
    BioticCybernetic,
    BioticTech,
    BioticDisabled,
    Biotic2,
    Biotic3,

    CyberneticWarfare,
    CyberneticPropulsion,
    CyberneticCybernetic,
    CyberneticTech,
    CyberneticDisabled,
    Cybernetic2,
    Cybernetic3,

    WarfarePropulsion,
    WarfareWarfare,
    WarfareTech,
    WarfareDisabled,
    Warfare2,
    Warfare3,

    UnitUpgradeTech,
    UnitTechSkip,
    NonUnitTechSkip,
    SynergyPropulsionLeft,
    SynergyPropulsionRight, //
    SynergyBioticLeft,
    SynergyBioticRight, //
    SynergyCyberneticLeft,
    SynergyCyberneticRight, //
    SynergyWarfareLeft,
    SynergyWarfareRight, //
    SynergyAll,
    SynergyNone;

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
            default -> NonUnitTechSkip;
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
