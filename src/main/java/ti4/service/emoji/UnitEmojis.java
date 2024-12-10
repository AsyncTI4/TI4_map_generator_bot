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
}
