package ti4.listeners;

import java.time.Duration;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
@Service
public class BotMessageCacheService {

    private static final long RETENTION_MILLIS = Duration.ofHours(12).toMillis();

    private final BotMessageCacheRepository botMessageCacheRepository;

    public void cache(Message message) {
        if (message == null || !message.getAuthor().equals(message.getJDA().getSelfUser())) {
            return;
        }

        long createdAtEpochMillis = message.getTimeCreated().toInstant().toEpochMilli();
        BotMessageRecord record =
                new BotMessageRecord(message.getIdLong(), message.getContentRaw(), createdAtEpochMillis);
        botMessageCacheRepository.save(record);
    }

    @Nullable
    public String getContent(long messageId) {
        return botMessageCacheRepository.findById(messageId).map(BotMessageRecord::getContent).orElse(null);
    }

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void removeExpiredMessages() {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        botMessageCacheRepository.deleteByCreatedAtEpochMillisLessThan(cutoff);
    }
}
