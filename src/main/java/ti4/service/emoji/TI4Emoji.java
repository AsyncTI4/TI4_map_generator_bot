package ti4.service.emoji;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.helpers.Constants;
import ti4.message.BotLogger;
import ti4.service.emoji.ApplicationEmojiCacheService.CachedEmoji;

public interface TI4Emoji {

    public static Set<TI4Emoji> allEmojiEnums() {
        Set<TI4Emoji> values = new HashSet<>();
        values.addAll(Arrays.asList(ColorEmojis.values()));
        values.addAll(Arrays.asList(DiceEmojis.values()));
        values.addAll(Arrays.asList(FactionEmojis.values()));
        values.addAll(Arrays.asList(LeaderEmojis.values()));
        values.addAll(Arrays.asList(MiltyDraftEmojis.values()));
        values.addAll(Arrays.asList(PlanetEmojis.values()));
        values.addAll(Arrays.asList(SourceEmojis.values()));
        values.addAll(Arrays.asList(StratCardEmojis.values()));
        values.addAll(Arrays.asList(UncategorizedEmojis.values()));
        return values;
    }

    default public Emoji asEmoji() {
        String mention = this.toString();
        if (mention.isBlank()) return null;
        return Emoji.fromFormatted(mention);
    }

    default public String emojiString() {
        CachedEmoji emoji = ApplicationEmojiService.getApplicationEmoji(name());
        if (emoji == null) {
            BotLogger.log(Constants.jazzPing() + " could not find requested emoji: " + name());
            return ApplicationEmojiService.fallbackEmoji;
        }
        return emoji.getFormatted();
    }

    public String name();
}
