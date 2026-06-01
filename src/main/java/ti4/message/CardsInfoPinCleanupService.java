package ti4.message;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.logging.BotLogger;

@UtilityClass
class CardsInfoPinCleanupService {

    private static final long DRAIN_INTERVAL_MILLIS = 500;
    private static final Queue<Message> UNPIN_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED_MESSAGE_IDS = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService UNPIN_DRAIN = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("cards-info-pin-cleanup").factory());

    static {
        UNPIN_DRAIN.scheduleAtFixedRate(
                CardsInfoPinCleanupService::drainOne,
                DRAIN_INTERVAL_MILLIS,
                DRAIN_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public static void queueStalePinnedMessageCleanup(ThreadChannel threadChannel, Set<String> protectedMessageIds) {
        if (threadChannel == null) {
            return;
        }

        threadChannel
                .retrievePinnedMessages()
                .queue(
                        pinnedMessages -> {
                            for (var pinnedMessage : pinnedMessages) {
                                Message message = pinnedMessage.getMessage();
                                if (isProtected(message, protectedMessageIds)) continue;
                                if (!message.getAuthor().isBot()) continue;
                                queueUnpin(message);
                            }
                        },
                        BotLogger::catchRestError);
    }

    private static boolean isProtected(Message message, Set<String> protectedMessageIds) {
        return message != null
                && protectedMessageIds != null
                && StringUtils.isNotBlank(message.getId())
                && protectedMessageIds.contains(message.getId());
    }

    private static void queueUnpin(Message message) {
        if (message == null) return;
        String messageId = message.getId();
        if (!QUEUED_MESSAGE_IDS.add(messageId)) return;
        UNPIN_QUEUE.add(message);
    }

    private static void drainOne() {
        Message message = UNPIN_QUEUE.poll();
        if (message == null) return;
        String messageId = message.getId();
        message.unpin().queue(ignored -> QUEUED_MESSAGE_IDS.remove(messageId), error -> {
            QUEUED_MESSAGE_IDS.remove(messageId);
            BotLogger.catchRestError(error);
        });
    }
}
