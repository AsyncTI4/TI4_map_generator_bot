package ti4.spring.service.messagecache;

import java.time.Duration;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;

@AllArgsConstructor
@Service
public class SavedBotMessagesService {

    private static final long RETENTION_MILLIS = Duration.ofHours(12).toMillis();

    private final BotDiscordMessageEntityRepository botDiscordMessageEntityRepository;

    public void cache(Message message) {
        if (!isImportantMessage(message)) return;

        long createdAtEpochMillis = message.getTimeCreated().toInstant().toEpochMilli();
        var record = new BotDiscordMessageEntity(message.getIdLong(), message.getContentRaw(), createdAtEpochMillis);
        botDiscordMessageEntityRepository.save(record);
    }

    private boolean isImportantMessage(Message message) {
        if (message == null || message.isEphemeral() || !message.getAuthor().isBot()) return false;
        String content = message.getContentRaw();
        return content.contains("privately used the command:")
                || content.contains("A command string message was deleted.")
                || content.contains("```sus");
    }

    @Nullable
    public String getContent(long messageId) {
        return botDiscordMessageEntityRepository
                .findById(messageId)
                .map(BotDiscordMessageEntity::getContent)
                .orElse(null);
    }

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void removeExpiredMessages() {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        long deletedRowCount = botDiscordMessageEntityRepository.deleteByCreatedAtEpochMillisLessThan(cutoff);
        BotLogger.info("Deleted " + deletedRowCount + " rows from the `bot_discord_message` table.");
    }

    public static SavedBotMessagesService getBean() {
        return SpringContext.getBean(SavedBotMessagesService.class);
    }
}
