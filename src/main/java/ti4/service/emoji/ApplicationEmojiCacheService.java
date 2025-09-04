package ti4.service.emoji;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import org.jetbrains.annotations.NotNull;
import ti4.json.PersistenceManager;
import ti4.message.logging.BotLogger;

@UtilityClass
public class ApplicationEmojiCacheService {

    public static List<CachedEmoji> readCachedEmojis() {
        List<CachedEmoji> cache = null;
        try {
            cache = PersistenceManager.readObjectFromJsonFile("emojis.json", new TypeReference<>() {
            });
        } catch (Exception e) {
            BotLogger.error("Failed to read json data for EmojiCache.", e);
        }
        if (cache == null) return Collections.emptyList();
        return cache;
    }

    public static void saveCachedEmojis(List<CachedEmoji> cachedEmojis) {
        try {
            PersistenceManager.writeObjectToJsonFile("emojis.json", cachedEmojis);
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for EmojiCache.", e);
        }
    }

    public static void saveCachedEmojis(Map<String, ApplicationEmoji> appEmojis) {
        saveCachedEmojis(appEmojis.values().stream().map(CachedEmoji::new).toList());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CachedEmoji {
        private String name;
        private String id;
        private String formatted;
        private long timeCreated;

        public CachedEmoji(@NotNull ApplicationEmoji appEmoji) {
            name = appEmoji.getName();
            id = appEmoji.getId();
            formatted = appEmoji.getFormatted();

            long seconds = appEmoji.getTimeCreated().getLong(ChronoField.INSTANT_SECONDS);
            long millis = appEmoji.getTimeCreated().getLong(ChronoField.NANO_OF_SECOND) / 1000000L;
            timeCreated = seconds * 1000L + millis;
        }

        public String toString() {
            return name + " " + id + " " + formatted + " " + timeCreated;
        }
    }
}
