package ti4.service.combat.sustain;

import java.util.Locale;

public enum HitSource {
    SPACE_COMBAT,
    GROUND_COMBAT,
    AFB,
    SPACE_CANNON,
    BOMBARDMENT,
    ABILITY;

    public static HitSource fromAssignHitsType(String assignHitsType) {
        if (assignHitsType == null) {
            return ABILITY;
        }
        return switch (assignHitsType.toLowerCase(Locale.ROOT)) {
            case "spacecombat", "courageouscombat", "assaultcannoncombat" -> SPACE_COMBAT;
            case "groundcombat" -> GROUND_COMBAT;
            case "afb" -> AFB;
            case "pds" -> SPACE_CANNON;
            case "bombardment" -> BOMBARDMENT;
            default -> ABILITY;
        };
    }

    public boolean isSpaceCombatRound() {
        return this == SPACE_COMBAT || this == AFB;
    }
}
