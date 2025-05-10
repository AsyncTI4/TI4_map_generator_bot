package ti4.service.emoji;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.helpers.Constants;
import ti4.message.BotLogger;
import ti4.service.emoji.ApplicationEmojiCacheService.CachedEmoji;

public interface TI4Emoji {

    default Emoji asEmoji() {
        String mention = this.toString();
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
            UnitEmojis.warsun, UnitEmojis.spacedock, UnitEmojis.pds, UnitEmojis.mech, UnitEmojis.infantry, UnitEmojis.flagship,
            UnitEmojis.fighter, UnitEmojis.dreadnought, UnitEmojis.destroyer, UnitEmojis.carrier, UnitEmojis.cruiser,
            // Explores
            ExploreEmojis.HFrag, ExploreEmojis.CFrag, ExploreEmojis.IFrag, ExploreEmojis.UFrag, ExploreEmojis.Relic,
            ExploreEmojis.Cultural, ExploreEmojis.Industrial, ExploreEmojis.Hazardous, ExploreEmojis.Frontier,
            // Planet
            PlanetEmojis.SemLord,
            // Cards
            CardEmojis.ActionCard, CardEmojis.Agenda, CardEmojis.PN,
            CardEmojis.SecretObjective, CardEmojis.Public1, CardEmojis.Public2,
            // Tech
            TechEmojis.CyberneticTech, TechEmojis.PropulsionTech, TechEmojis.BioticTech, TechEmojis.WarfareTech,
            // Other
            MiscEmojis.tg, MiscEmojis.comm, MiscEmojis.Sleeper,
            MiscEmojis.influence, MiscEmojis.resources,
            MiscEmojis.WHalpha, MiscEmojis.WHbeta, MiscEmojis.WHgamma, MiscEmojis.LegendaryPlanet,
            MiscEmojis.SpeakerToken, MiscEmojis.BortWindow));
    }

}
