package ti4.cron;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ThreadGetter;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class InteractionLogCron {

    private static final int INITIAL_BUFFER_SIZE = 500;
    private static final Object BUFFER_LOCK = new Object();

    private static Deque<BotLogger.AbstractEventLog> messageBuffer = new ArrayDeque<>(INITIAL_BUFFER_SIZE);
    private static TextChannel primaryBotLogChannel;
    private static boolean isRegistered;

    public static void addLogMessage(@Nonnull BotLogger.AbstractEventLog logMessage) {
        synchronized (BUFFER_LOCK) {
            messageBuffer.add(logMessage);
        }
    }

    // This is rather ugly, but it keeps the implementation of BotLogger.AbstractEventLog extenders simple
    public static void register() {
        if (isRegistered) {
            BotLogger.info("INTERACTION LOG CRON ALREADY REGISTERED");
            return;
        }
        BotLogger.info("Registering bot log cron");
        isRegistered = true;

        List<TextChannel> logCandidates = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("bot-log", false);
        if (logCandidates.isEmpty()) {
            BotLogger.error("Primary log channel could not be found in InteractionLogCron");
            return;
        }
        primaryBotLogChannel = logCandidates.getFirst();

        CronManager.schedulePeriodically(
                InteractionLogCron.class, InteractionLogCron::logInteractions, 2, 2, TimeUnit.MINUTES);
    }

    // The exceptions in this method are a result of getting abstract class methods, which are required to
    // be defined by the nature of an abstract class
    @SneakyThrows
    private static void logInteractions() {
        Map<Class<?>, StringBuilder> messageBuilders = drainMessageBufferIntoMessageBuilders();

        // For each class of message either send by channel (if exists) or thread
        for (Map.Entry<Class<?>, StringBuilder> entry : messageBuilders.entrySet()) {
            StringBuilder message = entry.getValue();
            if (message.isEmpty()) continue;
            sendByChannelOrThread(entry.getKey(), message);
        }
    }

    private static Map<Class<?>, StringBuilder> drainMessageBufferIntoMessageBuilders() {
        Deque<BotLogger.AbstractEventLog> toProcess;
        synchronized (BUFFER_LOCK) {
            toProcess = messageBuffer;
            messageBuffer = new ArrayDeque<>(INITIAL_BUFFER_SIZE);
        }

        Map<Class<?>, StringBuilder> messageBuilders = new HashMap<>();
        while (!toProcess.isEmpty()) {
            BotLogger.AbstractEventLog e = toProcess.pollFirst();
            messageBuilders
                    .computeIfAbsent(e.getClass(), k -> new StringBuilder())
                    .append(e.getLogString());
        }
        return messageBuilders;
    }

    @SneakyThrows
    private static void sendByChannelOrThread(Class<?> clazz, StringBuilder message) {
        List<TextChannel> logCandidates = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName(
                (String) clazz.getMethod("getChannelName").invoke(null), false);

        if (!logCandidates.isEmpty()) {
            logCandidates.getFirst().sendMessage(message.toString()).queue();
        } else {
            try {
                ThreadGetter.getThreadInChannel(
                        primaryBotLogChannel,
                        (String) clazz.getMethod("getThreadName").invoke(null),
                        (threadChannel) -> MessageHelper.sendMessageToChannel(threadChannel, message.toString()));
            } catch (Exception e) {
                BotLogger.error(
                        "Failed to send a message via ThreadGetter in InteractionLogCron (this should not happen)", e);
            }
        }
    }
}
