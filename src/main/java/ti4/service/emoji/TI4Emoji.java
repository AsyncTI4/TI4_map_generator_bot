package ti4.service.emoji;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.message.logging.BotLogger;
import ti4.service.emoji.ApplicationEmojiCacheService.CachedEmoji;

public interface TI4Emoji {

    default Emoji asEmoji() {
        String mention = toString();
        if (mention.isBlank()) return null;
        return Emoji.fromFormatted(mention);
    }

    default String emojiString() {
        CachedEmoji emoji = ApplicationEmojiService.getApplicationEmoji(name());
        if (emoji == null) {
            BotLogger.warning(Constants.jazzPing() + " could not find requested emoji: " + name());
            return ApplicationEmojiService.fallbackEmoji;
        }
        return emoji.getFormatted();
    }

    String name();

    // -------------------------------------------------------------------------------------------------------------------------------
    // Static Stuff
    // -------------------------------------------------------------------------------------------------------------------------------

    static Set<TI4Emoji> allEmojiEnums() {
        Set<TI4Emoji> values = new HashSet<>();
        values.addAll(Arrays.asList(CardEmojis.values()));
        values.addAll(Arrays.asList(ColorEmojis.values()));
        values.addAll(Arrays.asList(DiceEmojis.values()));
        values.addAll(Arrays.asList(ExploreEmojis.values()));
        values.addAll(Arrays.asList(FactionEmojis.values()));
        values.addAll(Arrays.asList(LeaderEmojis.values()));
        values.addAll(Arrays.asList(MiltyDraftEmojis.values()));
        values.addAll(Arrays.asList(PlanetEmojis.values()));
        values.addAll(Arrays.asList(SourceEmojis.values()));
        values.addAll(Arrays.asList(TechEmojis.values()));
        values.addAll(Arrays.asList(UnitEmojis.values()));
        values.addAll(Arrays.asList(TileEmojis.values()));
        // And then everything else
        values.addAll(Arrays.asList(MiscEmojis.values()));
        return values;
    }

    static <T extends TI4Emoji> T getEmojiFromName(Class<T> clazz, String name) {
        if (!clazz.isEnum()) return null;
        List<T> consts = Arrays.asList(clazz.getEnumConstants());
        return consts.stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    static TI4Emoji findEmoji(String category, String name) {
        return switch (category.toLowerCase()) {
            case "card", "cards", "cardemojis" -> getEmojiFromName(CardEmojis.class, name);
            case "color", "colors", "coloremojis" -> getEmojiFromName(ColorEmojis.class, name);
            case "dice", "diceemojis" -> getEmojiFromName(DiceEmojis.class, name);
            case "explore", "explores", "exploreemojis" -> getEmojiFromName(ExploreEmojis.class, name);
            case "faction", "factions", "factionemojis" -> getEmojiFromName(FactionEmojis.class, name);
            case "leader", "leaders", "leaderemojis" -> getEmojiFromName(LeaderEmojis.class, name);
            case "milty", "miltydraft", "draft", "miltyemojis" -> getEmojiFromName(MiltyDraftEmojis.class, name);
            case "planet", "planets", "planetemojis" -> getEmojiFromName(PlanetEmojis.class, name);
            case "source", "sources", "sourceemojis" -> getEmojiFromName(SourceEmojis.class, name);
            case "tech", "techs", "techemojis" -> getEmojiFromName(TechEmojis.class, name);
            case "unit", "units", "unitemojis" -> getEmojiFromName(UnitEmojis.class, name);
            case "tile", "tiles", "tileemojis" -> getEmojiFromName(TileEmojis.class, name);
            case "misc", "miscemojis" -> getEmojiFromName(MiscEmojis.class, name);
            default -> null;
        };
    }

    static TI4Emoji getRandomGoodDog() {
        List<TI4Emoji> goodDogs = new ArrayList<>(MiscEmojis.goodDogs());
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(goodDogs, seed);
        return goodDogs.getFirst();
    }

    static TI4Emoji getRandomGoodDog(String randomSeed) {
        List<TI4Emoji> goodDogs = new ArrayList<>(MiscEmojis.goodDogs());
        Random seed = new Random(randomSeed.hashCode());
        Collections.shuffle(goodDogs, seed);
        return goodDogs.getFirst();
    }

    @NotNull
    static TI4Emoji getRandomizedEmoji(int value, String messageID) {
        List<TI4Emoji> symbols = new ArrayList<>(symbols());
        Random seed = messageID == null ? ThreadLocalRandom.current() : new Random(messageID.hashCode());
        Collections.shuffle(symbols, seed);
        value %= symbols.size();
        return symbols.get(value);
    }

    // LIST OF SYMBOLS FOR FOG STUFF
    static List<TI4Emoji> symbols() {
        return new ArrayList<>(Arrays.asList(
                // Unit emojis
                UnitEmojis.warsun,
                UnitEmojis.spacedock,
                UnitEmojis.pds,
                UnitEmojis.mech,
                UnitEmojis.infantry,
                UnitEmojis.flagship,
                UnitEmojis.fighter,
                UnitEmojis.dreadnought,
                UnitEmojis.destroyer,
                UnitEmojis.carrier,
                UnitEmojis.cruiser,
                // Explores
                ExploreEmojis.HFrag,
                ExploreEmojis.CFrag,
                ExploreEmojis.IFrag,
                ExploreEmojis.UFrag,
                ExploreEmojis.Relic,
                ExploreEmojis.Cultural,
                ExploreEmojis.Industrial,
                ExploreEmojis.Hazardous,
                ExploreEmojis.Frontier,
                // Planet
                PlanetEmojis.SemLord,
                // Cards
                CardEmojis.ActionCard,
                CardEmojis.Agenda,
                CardEmojis.PN,
                CardEmojis.SecretObjective,
                CardEmojis.Public1,
                CardEmojis.Public2,
                // Tech
                TechEmojis.CyberneticTech,
                TechEmojis.PropulsionTech,
                TechEmojis.BioticTech,
                TechEmojis.WarfareTech,
                // Other
                MiscEmojis.tg,
                MiscEmojis.comm,
                MiscEmojis.Sleeper,
                MiscEmojis.influence,
                MiscEmojis.resources,
                MiscEmojis.WHalpha,
                MiscEmojis.WHbeta,
                MiscEmojis.WHgamma,
                MiscEmojis.LegendaryPlanet,
                MiscEmojis.SpeakerToken,
                MiscEmojis.BortWindow));
    }
}
