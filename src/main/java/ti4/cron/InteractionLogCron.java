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
	@Nonnull
	private static final Map<Class<?>, TextChannel> logChannels = new HashMap<>();
	private static TextChannel primaryBotLogChannel;
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
	@SneakyThrows // The exceptions in this method are a result of getting abstract class methods, which are required to be defined by the nature of an abstract class
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

		for (Class<?> eventLog : BotLogger.AbstractEventLog.class.getDeclaredClasses()) {
			logCandidates = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName((String) eventLog.getMethod("getChannelName").invoke(null), false);
			if (logCandidates.isEmpty())
				BotLogger.info("No log channel found for event log " + eventLog.getName());
			else
				logChannels.put(eventLog, logCandidates.getFirst());
		}

		CronManager.schedulePeriodically(InteractionLogCron.class, InteractionLogCron::logInteractions, 2, 2, TimeUnit.MINUTES);
	}

	public static void logInteractions() {
		HashMap<Class<?>, StringBuilder> messageBuilders = new HashMap<>();
		for (Class<?> logType : BotLogger.AbstractEventLog.class.getDeclaredClasses()) {
			messageBuilders.put(logType, new StringBuilder());
		}

		synchronized (messageBuffer) {
			for (BotLogger.AbstractEventLog eventLog : messageBuffer) {
				messageBuilders.get(eventLog.getClass()).append(eventLog.getLogString());
			}
			messageBuffer.clear();
		}

		messageBuilders.forEach((variant, message) -> {
			if (message.isEmpty()) return;

			if (logChannels.containsKey(variant))
				logChannels.get(variant).sendMessage(message.toString()).queue();
			else {
				try {
					ThreadGetter.getThreadInChannel(primaryBotLogChannel, (String) variant.getMethod("getThreadName").invoke(null), (threadChannel) -> {
						MessageHelper.sendMessageToChannel(threadChannel, message.toString());
					});
				} catch (Exception e) {
					BotLogger.error("Failed to send a message via ThreadGetter in InteractionLogCron (this should not happen)", e);
				}
			}
		});
	}
}
