package ti4.service.emoji;

public enum SourceEmojis implements TI4Emoji {
    // Boxes
    TI4BaseGame,
    TI4PoK,

    // Source Icons
    PoK,
    Codex,
    ThundersEdgeIcon,

    // Homebrew
    Absol,
    DiscordantStars,
    UnchartedSpace,
    Monuments,
    ActionDeck2,
    KeleresPlus,
    ProjectPi,
    Flagshipping,
    PromisesPromises,
    TwilightKart,
    Eronous,
    IgnisAurora,
    MiltyMod,
    StrategicAlliance;

    @Override
    public String toString() {
        return emojiString();
    }
}
