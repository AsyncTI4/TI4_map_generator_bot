package ti4.spring.service.messagecache;

import java.time.Duration;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;

@AllArgsConstructor
@Service
public class SavedBotMessagesService {

    private static final long RETENTION_MILLIS = Duration.ofHours(12).toMillis();

    private final BotDiscordMessageEntityRepository botDiscordMessageEntityRepository;

    public void cache(Message message) {
        if (DatabasePersistenceGate.isDisabled()) return;
        if (!isImportantMessage(message)) return;

        long createdAtEpochMillis = message.getTimeCreated().toInstant().toEpochMilli();
        botDiscordMessageEntityRepository.upsert(message.getIdLong(), message.getContentRaw(), createdAtEpochMillis);
    }

    public static boolean isImportantMessage(Message message) {
        if (message == null || message.isEphemeral() || !message.getAuthor().isBot()) return false;

        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        if (message.getTimeCreated().toInstant().toEpochMilli() < cutoff) return false;

        String content = message.getContentRaw();
        return content.contains("privately used the command:")
                || content.contains("A command string message was deleted.")
                || content.contains("```sus");
    }

    @Nullable
    public String getContent(long messageId) {
        if (DatabasePersistenceGate.isDisabled()) return null;
        return botDiscordMessageEntityRepository
                .findById(messageId)
                .map(BotDiscordMessageEntity::getContent)
                .orElse(null);
    }

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void removeExpiredMessages() {
        if (DatabasePersistenceGate.isDisabled()) {
            BotLogger.info("Skipping bot message cache cleanup because database maintenance mode is active.");
            return;
        }
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        long deletedRowCount = botDiscordMessageEntityRepository.deleteExpired(cutoff);
        BotLogger.info("Deleted " + deletedRowCount + " rows from the `bot_discord_message` table.");
    }

    public void remove(long messageId) {
        if (DatabasePersistenceGate.isDisabled()) return;
        botDiscordMessageEntityRepository.deleteMessage(messageId);
    }

    public static SavedBotMessagesService getBean() {
        return SpringContext.getBean(SavedBotMessagesService.class);
    }
}
