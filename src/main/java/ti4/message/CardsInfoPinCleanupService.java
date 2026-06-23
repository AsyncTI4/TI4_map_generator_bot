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
public class CardsInfoPinCleanupService {

    private static final long PIN_RETRIEVAL_DRAIN_INTERVAL_MILLIS = 1000;
    private static final long UNPIN_DRAIN_INTERVAL_MILLIS = 250;
    private static final Queue<PinnedMessageCleanupRequest> PIN_RETRIEVAL_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Queue<Message> UNPIN_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED_THREAD_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> QUEUED_MESSAGE_IDS = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService PIN_CLEANUP_DRAIN = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("cards-info-pin-cleanup").factory());

    static {
        PIN_CLEANUP_DRAIN.scheduleAtFixedRate(
                CardsInfoPinCleanupService::drainOnePinnedMessageRetrieval,
                PIN_RETRIEVAL_DRAIN_INTERVAL_MILLIS,
                PIN_RETRIEVAL_DRAIN_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
        PIN_CLEANUP_DRAIN.scheduleAtFixedRate(
                CardsInfoPinCleanupService::drainOneUnpin,
                UNPIN_DRAIN_INTERVAL_MILLIS,
                UNPIN_DRAIN_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public static void queueStalePinnedMessageCleanup(ThreadChannel threadChannel, Set<String> protectedMessageIds) {
        if (threadChannel == null) {
            return;
        }

        String threadId = threadChannel.getId();
        if (!QUEUED_THREAD_IDS.add(threadId)) return;
        PIN_RETRIEVAL_QUEUE.add(new PinnedMessageCleanupRequest(
                threadChannel, protectedMessageIds == null ? Set.of() : Set.copyOf(protectedMessageIds)));
    }

    private static void drainOnePinnedMessageRetrieval() {
        PinnedMessageCleanupRequest request = PIN_RETRIEVAL_QUEUE.poll();
        if (request == null) return;

        request.threadChannel()
                .retrievePinnedMessages()
                .queue(
                        pinnedMessages -> {
                            QUEUED_THREAD_IDS.remove(request.threadChannel().getId());
                            for (var pinnedMessage : pinnedMessages) {
                                Message message = pinnedMessage.getMessage();
                                if (message == null || isProtected(message, request.protectedMessageIds())) continue;
                                if (!message.getAuthor().isBot()) continue;
                                queueUnpin(message);
                            }
                        },
                        error -> {
                            QUEUED_THREAD_IDS.remove(request.threadChannel().getId());
                            BotLogger.catchRestError(error);
                        });
    }

    public static void queuePinnedBotMessageCleanup(ThreadChannel threadChannel) {
        queueStalePinnedMessageCleanup(threadChannel, Set.of());
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

    private static void drainOneUnpin() {
        Message message = UNPIN_QUEUE.poll();
        if (message == null) return;
        String messageId = message.getId();
        message.unpin().queue(ignored -> QUEUED_MESSAGE_IDS.remove(messageId), error -> {
            QUEUED_MESSAGE_IDS.remove(messageId);
            BotLogger.catchRestError(error);
        });
    }

    private record PinnedMessageCleanupRequest(ThreadChannel threadChannel, Set<String> protectedMessageIds) {}
}
