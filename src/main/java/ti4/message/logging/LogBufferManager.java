package ti4.message.logging;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.AsyncTI4DiscordBot;
import ti4.message.MessageHelper;

@UtilityClass
public class LogBufferManager {

    private static final int INITIAL_STRING_BUFFER_SIZE = 2000;
    private static final int INITIAL_BUFFER_SIZE = 500;
    private static final Object BUFFER_LOCK = new Object();

    private static Deque<AbstractEventLog> logBuffer = new ArrayDeque<>(INITIAL_BUFFER_SIZE);

    static void addLogMessage(@Nonnull AbstractEventLog logMessage) {
        synchronized (BUFFER_LOCK) {
            logBuffer.add(logMessage);
        }
    }

    public static void sendBufferedLogsToDiscord() {
        Map<LogTarget, StringBuilder> messageBuilders = drainMessageBufferIntoMessageBuilders();

        // For each class of message either send by channel (if exists) or thread
        for (Map.Entry<LogTarget, StringBuilder> entry : messageBuilders.entrySet()) {
            StringBuilder message = entry.getValue();
            if (message.isEmpty()) continue;
            sendByChannelOrThread(entry.getKey(), message);
        }
    }

    private static Map<LogTarget, StringBuilder> drainMessageBufferIntoMessageBuilders() {
        try {
            Deque<AbstractEventLog> toProcess;
            synchronized (BUFFER_LOCK) {
                toProcess = logBuffer;
                logBuffer = new ArrayDeque<>(INITIAL_BUFFER_SIZE);
            }

            Map<LogTarget, StringBuilder> messageBuilders = new HashMap<>();
            while (!toProcess.isEmpty()) {
                AbstractEventLog eventLog = toProcess.pollFirst();
                tryToHandleEventLog(eventLog, messageBuilders);
            }
            return messageBuilders;
        } catch (Exception e) {
            BotLogger.error("Error draining message buffer.", e);
            return Collections.emptyMap();
        }
    }

    private static void tryToHandleEventLog(AbstractEventLog eventLog, Map<LogTarget, StringBuilder> messageBuilders) {
        try {
            LogTarget target = new LogTarget(eventLog.getChannelName(), eventLog.getThreadName());
            messageBuilders
                    .computeIfAbsent(target, k -> new StringBuilder(INITIAL_STRING_BUFFER_SIZE))
                    .append(eventLog.getLogString());
        } catch (Exception e) {
            BotLogger.error("Error draining message buffer for single event log.", e);
        }
    }

    private static void sendByChannelOrThread(LogTarget target, StringBuilder message) {
        List<TextChannel> logCandidates =
                AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName(target.channelName(), false);

        if (logCandidates.isEmpty()) {
            BotLogger.error("Cannot log buffered logs because target channel not found in primary guild: "
                    + target.channelName());
            return;
        }

        TextChannel channel = logCandidates.getFirst();
        String threadName = target.threadName();
        try {
            if (isNotBlank(threadName)) {
                MessageHelper.sendMessageToThread(channel, target.threadName(), message.toString());
            } else {
                MessageHelper.sendMessageToChannel(channel, message.toString());
            }
        } catch (Exception e) {
            BotLogger.error("Failed to send LogBufferManager message", e);
        }
    }

    private record LogTarget(String channelName, String threadName) {}
}
