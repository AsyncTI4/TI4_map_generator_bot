package ti4.cron;

import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ThreadGetter;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InteractionLogCron {
	@Nonnull
	private static final ArrayDeque<BotLogger.AbstractEventLog> messageBuffer = new ArrayDeque<>(500);
	private static TextChannel primaryBotLogChannel; // It is safe to store this channel as it should always exist
	private static boolean isRegistered;

	/**
	 * Adds an AbstractEventLog to the message cache.
	 * @param logMessage - The message to be added to the cache
	 */
	public static void addLogMessage(@Nonnull BotLogger.AbstractEventLog logMessage) {
		synchronized (messageBuffer) {
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

		CronManager.schedulePeriodically(InteractionLogCron.class, InteractionLogCron::logInteractions, 2, 2, TimeUnit.MINUTES);
	}

	@SneakyThrows // The exceptions in this method are a result of getting abstract class methods, which are required to be defined by the nature of an abstract class
	public static void logInteractions() {
		// Build event -> combined message map
		HashMap<Class<?>, StringBuilder> messageBuilders = new HashMap<>();
		for (Class<?> logType : BotLogger.AbstractEventLog.class.getDeclaredClasses()) {
			messageBuilders.put(logType, new StringBuilder());
		}

		// Get messages from buffer
		synchronized (messageBuffer) {
			for (BotLogger.AbstractEventLog eventLog : messageBuffer) {
				messageBuilders.get(eventLog.getClass()).append(eventLog.getLogString());
			}
			messageBuffer.clear();
		}

		// For each class of message either send by channel (if exists) or thread
		for (Map.Entry<Class<?>, StringBuilder> entry : messageBuilders.entrySet()) {
			if (entry.getValue().isEmpty()) return;

			List<TextChannel> logCandidates = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName((String) entry.getKey().getMethod("getChannelName").invoke(null), false);

			if (!logCandidates.isEmpty()) {
				logCandidates.getFirst().sendMessage(entry.getValue().toString()).queue();
			} else {
				try {
					ThreadGetter.getThreadInChannel(primaryBotLogChannel, (String) entry.getKey().getMethod("getThreadName").invoke(null), (threadChannel) -> {
						MessageHelper.sendMessageToChannel(threadChannel, entry.getValue().toString());
					});
				} catch (Exception e) {
					BotLogger.error("Failed to send a message via ThreadGetter in InteractionLogCron (this should not happen)", e);
				}
			}
		}
	}
}
