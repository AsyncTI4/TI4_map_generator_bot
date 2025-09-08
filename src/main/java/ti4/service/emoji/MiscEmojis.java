package ti4.service.emoji;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;

public enum MiscEmojis implements TI4Emoji {
    // Tokens
    Custodians,
    CustodiansVP,
    tg,
    NomadCoin,
    comm,
    Sleeper,
    Wash, //
    WHalpha,
    WHbeta,
    WHgamma,
    CreussAlpha,
    CreussBeta,
    CreussGamma, //
    LegendaryPlanet,
    SpeakerToken,
    DoubleBoom,

    // Res / Inf
    influence,
    resources,
    ResInf, //
    Resources_0,
    Resources_1,
    Resources_2,
    Resources_3,
    Resources_4, //
    Resources_5,
    Resources_6,
    Resources_7,
    Resources_8,
    Resources_9, //
    Influence_0,
    Influence_1,
    Influence_2,
    Influence_3,
    Influence_4, //
    Influence_5,
    Influence_6,
    Influence_7,
    Influence_8,
    Influence_9, //

    // Doggies
    Winnie,
    Ozzie,
    Summer,
    Charlie,
    Scout,
    ScoutSpinner,

    // Tiles
    Supernova,
    Asteroids,
    GravityRift,
    Nebula,
    Anomaly,
    EmptySystem,
    Nexus,

    // Other
    Sabotage,
    NoSabo,
    NoWhens,
    NoAfters, //
    Winemaking,
    BortWindow,
    SpoonAbides,
    AsyncTI4Logo,
    TIGL,
    RollDice, //
    BLT,
    Stroter,
    Wololo,
    TaDont;

    public static TI4Emoji getCreussWormhole(@NotNull String wormhole) {
        return switch (wormhole.toLowerCase()) {
            case "a", "alpha", "creussalpha" -> CreussAlpha;
            case "b", "beta", "creussbeta" -> CreussBeta;
            default -> CreussGamma;
        };
    }

    public static String getInfluenceEmoji(int count) {
        return switch (count) {
            case 0 -> Influence_0.toString();
            case 1 -> Influence_1.toString();
            case 2 -> Influence_2.toString();
            case 3 -> Influence_3.toString();
            case 4 -> Influence_4.toString();
            case 5 -> Influence_5.toString();
            case 6 -> Influence_6.toString();
            case 7 -> Influence_7.toString();
            case 8 -> Influence_8.toString();
            case 9 -> Influence_9.toString();
            default -> influence.toString() + count;
        };
    }

    public static String getResourceEmoji(int count) {
        return switch (count) {
            case 0 -> Resources_0.toString();
            case 1 -> Resources_1.toString();
            case 2 -> Resources_2.toString();
            case 3 -> Resources_3.toString();
            case 4 -> Resources_4.toString();
            case 5 -> Resources_5.toString();
            case 6 -> Resources_6.toString();
            case 7 -> Resources_7.toString();
            case 8 -> Resources_8.toString();
            case 9 -> Resources_9.toString();
            default -> resources.toString() + count;
        };
    }

    public static List<TI4Emoji> goodDogs() {
        return new ArrayList<>(List.of(Winnie, Ozzie, Summer, Charlie, Scout));
    }

    public static String getTGorNomadCoinEmoji(Game game) {
        if (game == null) return tg.toString();
        return game.isNomadCoin() ? NomadCoin.toString() : tg.toString();
    }

    public static String comm(int x) {
        return comm.toString().repeat(x);
    }

    public static String tg(int x) {
        return tg.toString().repeat(x);
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
