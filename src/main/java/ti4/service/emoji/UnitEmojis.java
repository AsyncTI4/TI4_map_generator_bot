package ti4.service.emoji;

public enum UnitEmojis implements TI4Emoji {

    // Structures
    spacedock,
    pds,

    // Ships
    warsun,
    flagship,
    dreadnought,
    carrier,
    cruiser,
    destroyer,
    fighter,

    // Ground forces
    mech,
    infantry,

    // Homebrew
    TyrantsLament,
    PlenaryOrbital,
    Monument;

    @Override
    public String toString() {
        return emojiString();
    }

    public static String getUnitEmoji(String unit) {
        return switch (unit.toLowerCase()) {
            case "sd", "dock", "space dock", "spacedock" -> spacedock.toString();
            case "pd", "pds" -> pds.toString();
            case "ws", "war sun", "warsun" -> warsun.toString();
            case "fs", "flag", "flagship" -> flagship.toString();
            case "dn", "dread", "dreadnought", "dreadnaught" -> dreadnought.toString();
            case "cv", "carrier" -> carrier.toString();
            case "ca", "cruiser" -> cruiser.toString();
            case "dd", "destroyer" -> destroyer.toString();
            case "ff", "fighter" -> fighter.toString();
            case "mf", "mech" -> mech.toString();
            case "gf", "infantry" -> infantry.toString();
            case "tyrantslament", "tyrants lament" -> TyrantsLament.toString();
            case "plenaryorbital", "plenary orbital" -> PlenaryOrbital.toString();
            case "monument" -> Monument.toString();
            default -> null;
        };
    }
}
