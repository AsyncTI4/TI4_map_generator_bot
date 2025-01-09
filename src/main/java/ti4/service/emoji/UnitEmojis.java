package ti4.service.emoji;

public enum UnitEmojis implements TI4Emoji {

    // Structures
    spacedock, pds,

    // Ships
    warsun, flagship, dreadnought, carrier, cruiser, destroyer, fighter,

    // Ground forces
    mech, infantry,

    // Homebrew
    TyrantsLament, PlenaryOrbital, Monument;

    @Override
    public String toString() {
        return emojiString();
    }
    
    public String getUnitEmoji(String unit) {
        switch (unit.toLowerCase()) {
            case "sd", "dock", "space dock", "spacedock":
                return spacedock.toString();
            case "pd", "pds":
                return pds.toString();
            case "ws", "war sun", "warsun":
                return warsun.toString();
            case "fs", "flag", "flagship":
                return flagship.toString();
            case "dn", "dread", "dreadnought", "dreadnaught":
                return dreadnought.toString();
            case "cv", "carrier":
                return carrier.toString();
            case "ca", "cruiser":
                return cruiser.toString();
            case "dd", "destroyer":
                return destroyer.toString();
            case "ff", "fighter":
                return fighter.toString();
            case "mf", "mech":
                return mech.toString();
            case "gf", "infantry":
                return infantry.toString();
            case "tyrantslament", "tyrants lament":
                return TyrantsLament.toString();
            case "plenaryorbital", "plenary orbital":
                return PlenaryOrbital.toString();
            case "monument":
                return Monument.toString();
        }
        return TI4Emoji.getRandomGoodDog().toString() + "`" + unit + "`";
    }
}
