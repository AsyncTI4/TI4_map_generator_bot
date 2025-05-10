package ti4.message;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.apache.commons.lang3.exception.ExceptionUtils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.cron.CronManager;
import ti4.cron.InteractionLogCron;
import ti4.helpers.*;
import ti4.listeners.ModalListener;
import ti4.map.Game;
import ti4.map.Player;
import ti4.selections.SelectionMenuProcessor;
import ti4.settings.GlobalSettings;

public class BotLogger {

	private static volatile long lastScheduledWebhook = 0;
	private static final Object lastScheduledWebhookLock = new Object();
	public static final long DISCORD_RATE_LIMIT = 50; // Min time in millis between discord webhook messages

	/**
	 * Sends a message to #bot-log-info in the offending server, else resorting to #bot-log and finally webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin  - The discord-based source of this log entry
	 * @param message - The message associated with this log entry
	 * @param err     - The error associated with this log entry. A full stack trace will be sent if this is not null
	 */
	public static void info(@Nullable LogMessageOrigin origin, @Nonnull String message, @Nullable Throwable err) {
		logToChannel(origin, message, err, LogSeverity.Info);
	}

	/**
	 * Sends a message to the primary server's webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param message - The message associated with this log entry
	 */
	public static void info(@Nonnull String message) {
		logToChannel(null, message, null, LogSeverity.Info);
	}

	/**
	 * Sends a message to the primary server's webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param message - The message associated with this log entry
	 * @param err     - The error associated with this log entry. A full stack trace will be sent if this is not null
	 */
	public static void info(@Nonnull String message, @Nullable Throwable err) {
		logToChannel(null, message, err, LogSeverity.Info);
	}

	/**
	 * Sends a message to #bot-log-info in offending server, else resorting to #bot-log and finally webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin  - The discord-based source of this log entry
	 * @param message - The message associated with this log entry
	 */
	public static void info(@Nullable LogMessageOrigin origin, @Nonnull String message) {
		logToChannel(origin, message, null, LogSeverity.Info);
	}

	/**
	 * Sends a message to #bot-log-warning in offending server, else resorting to #bot-log and finally webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin  - The discord-based source of this log entry
	 * @param message - The message associated with this log entry
	 * @param err     - The error associated with this log entry. A full stack trace will be sent if this is not null
	 */
	public static void warning(@Nullable LogMessageOrigin origin, @Nonnull String message, @Nullable Throwable err) {
		logToChannel(origin, message, err, LogSeverity.Warning);
	}

	/**
	 * Sends a message to the primary server's webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param message - The message associated with this log entry
	 */
	public static void warning(@Nonnull String message) {
		logToChannel(null, message, null, LogSeverity.Warning);
	}

	/**
	 * Sends a message to the primary server's webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param message - The message associated with this log entry
	 * @param err     - The error associated with this log entry. A full stack trace will be sent if this is not null
	 */
	public static void warning(@Nonnull String message, @Nullable Throwable err) {
		logToChannel(null, message, err, LogSeverity.Warning);
	}

	/**
	 * Sends a message to #bot-log-warning in offending server, else resorting to #bot-log and finally webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin  - The discord-based source of this log entry
	 * @param message - The message associated with this log entry
	 */
	public static void warning(@Nullable LogMessageOrigin origin, @Nonnull String message) {
		logToChannel(origin, message, null, LogSeverity.Warning);
	}

	/**
	 * Sends a message to #bot-log-error in offending server, else resorting to #bot-log and finally webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin  - The discord-based source of this log entry
	 * @param message - The message associated with this log entry
	 * @param err     - The error associated with this log entry. A full stack trace will be sent if this is not null
	 */
	public static void error(@Nullable LogMessageOrigin origin, @Nonnull String message, @Nullable Throwable err) {
		logToChannel(origin, message, err, LogSeverity.Error);
	}

	/**
	 * Sends a message to the primary server's webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param message - The message associated with this log entry
	 */
	public static void error(@Nonnull String message) {
		logToChannel(null, message, null, LogSeverity.Error);
	}

	/**
	 * Sends a message to the primary server's webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param message - The message associated with this log entry
	 * @param err     - The error associated with this log entry. A full stack trace will be sent if this is not null
	 */
	public static void error(@Nonnull String message, @Nullable Throwable err) {
		logToChannel(null, message, err, LogSeverity.Error);
	}

	/**
	 * Sends a message to #bot-log-error in offending server, else resorting to #bot-log and finally webhook.
	 * <p>
	 * If err is not null, a full stack trace will be sent in a thread.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin  - The discord-based source of this log entry
	 * @param message - The message associated with this log entry
	 */
	public static void error(@Nullable LogMessageOrigin origin, @Nonnull String message) {
		logToChannel(origin, message, null, LogSeverity.Error);
	}

	/**
	 * Sends a log message via the specified bot log channel. Should be used through info(), warning(), or error() methods.
	 * <p>
	 * Assumes that the event specified in data has a guild if it exists.
	 *
	 * @param origin - The origin of the discord event that created this log entry
	 * @param message - The message associated with this log entry
	 * @param err - The error associated with this log entry. A full stack trace will be sent if this is not null and the log is not sent via websocket
	 * @param severity - The severity of the log message
	 */
	private static void logToChannel(@Nullable LogMessageOrigin origin, @Nonnull String message, @Nullable Throwable err, @Nonnull LogSeverity severity) {
		TextChannel channel = null;
		StringBuilder msg = new StringBuilder().append(severity.headerText);

		if (origin != null) {
			msg.append(origin.getOriginTime())
				.append("\n");
			origin.appendSourceString(msg);
			origin.appendEventString(msg);
			channel = origin.getLogChannel(severity);
		} else {
			msg.append(DateTimeHelper.getCurrentTimestamp())
				.append("\n")
				.append("Source: Not provided\n");
		}

		msg.append("Message: ")
			.append(message);

		// Send off message
		String compiledMessage = msg.toString();
		int msgLength = compiledMessage.length();

		if (channel == null && AsyncTI4DiscordBot.guildPrimary != null) {
			List<TextChannel> logCandidates = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("bot-log", false);
			if (logCandidates.isEmpty()) {
				BotLogger.error("Primary log channel could not be found in main server, defaulting to webhook");
			} else {
				channel = logCandidates.getFirst();
			}
		}

		// Handle message length overflow. Overflow length is derived from previous implementation
		for (int i = 0; i <= msgLength; i += 2000) {
			String msgChunk = compiledMessage.substring(i, Math.min(i + 2000, msgLength));

			if (err == null || i + 2000 < msgLength) { // If length could overflow or there is no error to trace
				if (channel == null)
					scheduleWebhookMessage(msgChunk); // Send message on webhook
				else
					channel.sendMessage(msgChunk).queue(); // Send message on channel

			} else { // Handle error on last send
				if (origin != null && origin.getGuild() != null) // Second check may not be necessary but this is a hotfix.
					ThreadArchiveHelper.checkThreadLimitAndArchive(origin.getGuild());
				else
					ThreadArchiveHelper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);

				if (channel == null)
					scheduleWebhookMessage(msgChunk); // Send message on webhook
				else
					channel.sendMessage(msgChunk)
						.queue(m -> m.createThreadChannel("Stack Trace")
							.setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
							.queue(t -> {
								MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(err));
								t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
							}));
			}
		}
	}

	/**
	 * Sends a message to the bot-log webhook.
	 * <p>
	 * Has a rudimentary fix for discord rate limiting on webhook messages.
	 * @param message - The message to send to the webhook
	 */
	private static void scheduleWebhookMessage(@Nonnull String message) {
		long timeToNextMessage;
		synchronized (lastScheduledWebhookLock) {
			timeToNextMessage = lastScheduledWebhook + DISCORD_RATE_LIMIT - System.currentTimeMillis();
			lastScheduledWebhook = System.currentTimeMillis() + Math.max(timeToNextMessage, 0);
		}

		if (timeToNextMessage <= 0) {
			sendMessageToBotLogWebhook(message);
		} else {
            CronManager.scheduleOnce(BotLogger.class, () -> sendMessageToBotLogWebhook(message), timeToNextMessage, TimeUnit.MILLISECONDS);
		}
	}

	private static void sendMessageToBotLogWebhook(String message) {
		String botLogWebhookURL = switch (AsyncTI4DiscordBot.guildPrimaryID) {
			case Constants.ASYNCTI4_HUB_SERVER_ID -> // AsyncTI4 Primary HUB Production Server
				"https://discord.com/api/webhooks/1106562763708432444/AK5E_Nx3Jg_JaTvy7ZSY7MRAJBoIyJG8UKZ5SpQKizYsXr57h_VIF3YJlmeNAtuKFe5v";
			case "1059645656295292968" -> // PrisonerOne's Test Server
				"https://discord.com/api/webhooks/1159478386998116412/NiyxcE-6TVkSH0ACNpEhwbbEdIBrvTWboZBTwuooVfz5n4KccGa_HRWTbCcOy7ivZuEp";
			case null, default ->
				null;
		};

		if (botLogWebhookURL == null) {
			System.out.println("\"ERROR: Unable to get url for bot-log webhook\n " + message);
			return;
		}
		DiscordWebhook webhook = new DiscordWebhook(botLogWebhookURL);
		webhook.setContent(message);
		try {
			webhook.execute();
		} catch (Exception exception) {
			System.out.println("[BOT-LOG-WEBHOOK] " + message + "\n" + exception.getMessage());
		}
	}

	/**
	 * Sends a message to the Primary Async Server's #bot-log channel
	 *
	 * @param msg - message to send to the #bot-log channel
	 */
	@Deprecated
	public static void log(String msg) {
		info(msg);
	}

	@Deprecated
	public static void logWithTimestamp(String msg) {
		info(msg);
	}

	/**
	 * Sends a message to the Primary Async Server's #bot-log channel, including stack trace.
	 * <p>
	 * Will create a thread and post the full Stack Trace when supplied with an Exception
	 *
	 * @param msg - message to send to the #bot-log channel
	 * @e - Exception
	 */
	@Deprecated
	public static void log(String msg, Throwable e) {
		error(msg, e);
	}

	/**
	 * Sends a message to the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
	 *
	 * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
	 */
	@Deprecated
	public static void log(GenericInteractionCreateEvent event, String msg) {
		info(new LogMessageOrigin(event), msg);
	}

	/**
	 * Sends just the stack trace the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
	 * <p>
	 * Will create a thread and post the full Stack Trace
	 *
	 * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
	 */
	@Deprecated
	public static void log(GenericInteractionCreateEvent event, Exception e) {
		error(new LogMessageOrigin(event), "", e);
	}

	/**
	 * Sends a message to the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
	 * <p>
	 * Will create a thread and post the full Stack Trace when supplied with an Exception
	 *
	 * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
	 * @param e Exception
	 */
	@Deprecated
	public static void log(GenericInteractionCreateEvent event, String msg, Throwable e) {
		if (ignoredError(e)) return;

		TextChannel botLogChannel = getBotLogChannel(event);
		if (msg == null) msg = "";

		String sysOut = "[BOT-LOG] " + msg;
		if (e != null) {
			sysOut += "\n> - " + e.getMessage();
		}
		System.out.println(sysOut);
		if (e != null) e.printStackTrace();

		//Adding so we don't cause an exception by attempting to log
		if (msg.length() > 2000) {
			String ellipses = "...\n### Error message was too long and was truncated here\n"; //TODO: handle this better, don't truncate
			msg = msg.substring(0, 2000 - ellipses.length() - 1) + ellipses;
		}

		if (botLogChannel == null) {
			String name;
			if (event == null) {
				return;
			} else {
				name = event.getGuild().getName();
			}
			MessageHelper.sendMessageToBotLogWebhook("Failed to find bot-log channel for server: " + name + "\nSending via webhook to main server.\n>" + msg);
			return;
		}

		switch (event) {
			case null -> {
				if (e == null) {
					botLogChannel.sendMessage(msg).queue();
				} else {
					botLogChannel.sendMessage(msg).queue(m -> m.createThreadChannel("Stack Trace").queue(t -> {
						MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
						t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
					}));
				} //NON-EVENT LOGS
			}
			case SlashCommandInteractionEvent slashCommandInteractionEvent -> { //SLASH COMMAND EVENT LOGS
				String channelName = event.getChannel().getName();
				String channelMention = event.getChannel().getAsMention();
				String commandString = slashCommandInteractionEvent.getCommandString();
				String message = "[" + channelName + "](" + channelMention + ") " + event.getUser().getEffectiveName() + " used: `" + commandString + "`\n> Error: " + msg;
				if (e == null) {
					botLogChannel.sendMessage(message).queue();
				} else {
					ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
					botLogChannel.sendMessage(message).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
						MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
						t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
					}));
				}
			}
			case ButtonInteractionEvent buttonInteractionEvent -> { //BUTTON EVENT LOGS
				String channelName = event.getChannel().getName();
				Button button = buttonInteractionEvent.getButton();
				String message = "[" + channelName + "](" + buttonInteractionEvent.getMessage().getJumpUrl() + ") " + event.getUser().getEffectiveName() + " pressed button: " + ButtonHelper.getButtonRepresentation(button) +
					"\n> Error: " + msg;
				if (e == null) {
					botLogChannel.sendMessage(message).queue();
				} else {
					ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
					botLogChannel.sendMessage(message).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
						MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
						t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
					}));
				}
			}
			case StringSelectInteractionEvent sEvent -> { //SELECTION EVENT LOGS
				String channelName = event.getChannel().getName();
				String channelMention = event.getChannel().getAsMention();

				String menuInfo = SelectionMenuProcessor.getSelectionMenuDebugText(sEvent);
				String logMsg = channelMention + "\n" + channelName + ". " + menuInfo + "\n" + msg;
				if (e == null) {
					botLogChannel.sendMessage(logMsg).queue();
				} else {
					ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
					botLogChannel.sendMessage(logMsg).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
						MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
						t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
					}));
				}
			}
			case ModalInteractionEvent mEvent -> { // MODAL EVENT LOGS
				String channelName = event.getChannel().getName();
				String channelMention = event.getChannel().getAsMention();

				String menuInfo = ModalListener.getModalDebugText(mEvent);
				String logMsg = channelMention + "\n" + channelName + ". " + menuInfo + "\n" + msg;
				if (e == null) {
					botLogChannel.sendMessage(logMsg).queue();
				} else {
					ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
					botLogChannel.sendMessage(logMsg).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
						MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
						t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
					}));
				}
			}
			default -> {
				if (e == null) {
					botLogChannel.sendMessage("[unknown event]\n" + msg).queue();
				} else {
					ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
					botLogChannel.sendMessage("[unknown event]\n" + msg).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
						MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
						t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
					}));
				}
			}
		}
	}

	/**
	 * This class represents an event to be logged via InteractionLogCron.
	 * It contains all the necessary data to find the correct logging channel or thread in the primary server and write a log message.
	 */
	public static void logButton(ButtonInteractionEvent event) {
		InteractionLogCron.addLogMessage(new AbstractEventLog.ButtonInteraction(new LogMessageOrigin(event)));
	}

	public static void logSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
		InteractionLogCron.addLogMessage(new AbstractEventLog.SlashCommand(new LogMessageOrigin(event), commandResponseMessage));
	}

	/**
	 * Retreives either the event's guild's #bot-log channel, or, if that is null, the Primary server's #bot-log channel.
	 */
	@Deprecated
	public static TextChannel getBotLogChannel(GenericInteractionCreateEvent event) {
		TextChannel botLogChannel = null;
		if (event != null) {
			for (TextChannel textChannel : event.getGuild().getTextChannels()) {
				if ("bot-log".equals(textChannel.getName())) {
					botLogChannel = textChannel;
				}
			}
		}
		if (botLogChannel == null && AsyncTI4DiscordBot.guildPrimary != null) { //USE PRIMARY SERVER'S BOTLOG CHANNEL
			botLogChannel = getPrimaryBotLogChannel();
		}
		return botLogChannel;
	}

	@Deprecated
	public static TextChannel getPrimaryBotLogChannel() {
		for (TextChannel textChannel : AsyncTI4DiscordBot.guildPrimary.getTextChannels()) {
			if ("bot-log".equals(textChannel.getName())) {
				return textChannel;
			}
		}
		return null;
	}

	public static void catchRestError(Throwable e) {
		// if it's ignored, it's not actionable. Simple
		// if (ignoredError(e)) return;

		// Otherwise... maybe actionable!

		// If it gets too annoying, we can limit to testing mode/debug mode
		boolean debugMode = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);
		if (System.getenv("TESTING") != null || debugMode) {
			BotLogger.error("Encountered REST error", e);
		}
	}

	private static boolean ignoredError(Throwable error) {
		if (error instanceof ErrorResponseException restError) {
			// This is an "unknown message" error. Typically caused by the bot trying to delete or edit
			// a message that has already been deleted. We don't generally care about these
			return restError.getErrorCode() == 10008;
		}
		return false;
	}

	/**
	 * Enum for data associated with log severity
	 */
	private enum LogSeverity {
		Info("bot-log-info", "### INFO\n"), Warning("bot-log-warning", "## WARNING\n"), Error("bot-log-error", "## ERROR\n");

		public final String channelName;
		public final String headerText;

		LogSeverity(String channelName, String headerText) {
			this.channelName = channelName;
			this.headerText = headerText;
		}
	}

	/**
	 * Describes the discord-based origin of a log message and handles fetching data for log messages from these sources.
	 */
	public static class LogMessageOrigin {
		@Getter
		@Nullable
		private Guild guild;

		@Getter
		@Nullable
		private GuildChannel channel;

		@Getter
		@Nullable
		private GenericInteractionCreateEvent event;

		@Getter
		@Nullable
		private Game game;

		@Getter
		@Nullable
		private Player player;

		@Getter
		private String originTime;

		public LogMessageOrigin(@Nonnull Guild guild) {
			this.guild = guild;
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		public LogMessageOrigin(@Nonnull GuildChannel channel) {
			this.channel = channel;
			this.guild = channel.getGuild();
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		public LogMessageOrigin(@Nonnull GenericInteractionCreateEvent event) {
			this.event = event;
			if (event.isFromGuild()) {
				this.channel = event.getGuildChannel();
				this.guild = event.getGuild();
			} else {
				BotLogger.warning("LocationSource created from non-guild event. This will not attribute messages.");
			}
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		public LogMessageOrigin(@Nonnull Game game) {
			this.game = game;
			this.guild = game.getGuild();
			this.channel = game.getMainGameChannel();
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		public LogMessageOrigin(@Nonnull Player player) {
			this.player = player;
			this.game = player.getGame();
			if (game != null) {
				this.guild = game.getGuild();
				this.channel = game.getMainGameChannel();
			} else {
				BotLogger.warning("LocationSource created from player with null game. This will not attribute messages.");
			}
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		public LogMessageOrigin(@Nonnull GenericInteractionCreateEvent event, @Nonnull Game game) {
			this.game = game;
			this.guild = game.getGuild();
			this.event = event;
			if (event.isFromGuild())
				this.channel = event.getGuildChannel();
			else
				this.channel = game.getMainGameChannel();
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		public LogMessageOrigin(@Nonnull GenericInteractionCreateEvent event, @Nonnull Player player) {
			this.player = player;
			this.game = player.getGame();
			if (game != null) this.guild = game.getGuild();
			this.event = event;
			if (event.isFromGuild()) {
				this.channel = event.getGuildChannel();
				this.guild = event.getGuild();
			} else {
				this.channel = game.getMainGameChannel();
			}
			this.originTime = DateTimeHelper.getCurrentTimestamp();
		}

		/**
		 * Get mention for the most granular source.
		 * @return The most granular mention as a string
		 */
		@Nonnull
		public String getStrictestMention() {
			switch (event) {
				case ButtonInteractionEvent bEvent -> {
					return bEvent.getMessage().getJumpUrl();
				}
				case StringSelectInteractionEvent sEvent -> {
					return sEvent.getMessage().getJumpUrl();
				}
				case ModalInteractionEvent mEvent -> {
					if (mEvent.getMessage() != null) return mEvent.getMessage().getJumpUrl();
				}
				case null -> {}
				default -> {} // This will default to the GuildChannel in which the event was sent.
			}

			if (channel != null) return channel.getAsMention();

			if (guild != null) return "<Location source is a guild>";

			warning("A LocationSource was created with no location");
			return "No mention available";
		}

		/**
		 * Get name of the most granular source.
		 * @return The most granular name as a string
		 */
		@Nonnull
		public String getStrictestName() {
			if (channel != null) return "| Channel \"" + channel.getName() + "\"";

			if (guild != null) return "| Guild \"" + guild.getName() + "\"";

			warning("A LocationSource was created with no location");
			return "No name available";
		}

		/**
		 * Append the "Source:" portion of a log message to a StringBuilder.
		 * @param builder - The StringBuilder to which the source string is appended
		 * @return The StringBuilder passed into builder
		 */
		@Nonnull
		public StringBuilder appendSourceString(@Nonnull StringBuilder builder) {
			builder.append("Source: ");
			if (player != null) builder.append("| Player \"")
					.append(player.getDisplayName())
					.append("\" ");
			if (game != null) builder.append("| Game \"")
					.append(game.getName())
					.append("\" ");
			builder.append(getStrictestName())
					.append(" (")
					.append(getStrictestMention())
					.append(")\n");

			return builder;
		}

		/**
		 * Append the "Event:" portion of a log message to a StringBuilder.
		 * @param builder - The StringBuilder to which the event string is appended
		 * @return The StringBuilder passed into builder
		 */
		@Nonnull
		public StringBuilder appendEventString(@Nonnull StringBuilder builder) {
			if (event == null) return builder;

			builder.append(event.getUser().getEffectiveName())
					.append(" ");

			switch (event) {
				case SlashCommandInteractionEvent sEvent -> builder.append("used command `")
						.append(sEvent.getCommandString())
						.append("`\n");
				case ButtonInteractionEvent bEvent -> builder.append("pressed button ")
						.append(ButtonHelper.getButtonRepresentation(bEvent.getButton()))
						.append("\n");
				case StringSelectInteractionEvent sEvent -> builder.append("selected ")
						.append(SelectionMenuProcessor.getSelectionMenuDebugText(sEvent))
						.append("\n");
				case ModalInteractionEvent mEvent -> builder.append("used modal ")
						.append(ModalListener.getModalDebugText(mEvent))
						.append("\n");
				default -> builder.append("initiated an unexpected event\n");
			}

			return builder;
		}

		/**
		 * Get the most relevant log channel for this source. Priority is to severity.channelName, then "#bot-log", then returns null.
		 *
		 * @param severity - The severity of the log message, used to find the appropriate channel based on LogSeverity.channelName
		 * @return The most relevant logging TextChannel
		 */
		@Nullable
		public TextChannel getLogChannel(@Nonnull LogSeverity severity) {
			if (guild == null) return null;

			return guild.getTextChannelsByName(severity.channelName, false)
					.stream()
					.findAny()
					.orElse(guild.getTextChannelsByName("bot-log", false)
							.stream()
							.findFirst()
							.orElse(null));
		}
	}

	/**
	 * Describes event logs to be sent to InteractionLogCron
	 */
	// Implementor's note: all subclasses of AbstractEventLog are automatically accounted for in InteractionLogCron as long as channelName and threadName are defined.
	public sealed abstract static class AbstractEventLog { // Yes, this is basically trying to recreate a rust enum. No, I'm not sorry
		protected LogMessageOrigin source;

		// Implementor's note: These fields must have getters, as this is how the subclasses override the statics without changing them for all subclasses
		@Getter
		private static String channelName = "";

		@Getter
		private static String threadName = "";

		@Getter
		private static String messagePrefix = "";

		protected String message = "";

		public String getLogString() {
			StringBuilder message = new StringBuilder();

			source.appendEventString(
					source.appendSourceString(
							message.append(source.getOriginTime())
									.append("\n")));

			if (!this.message.isEmpty())
				message.append(getMessagePrefix())
						.append(this.message)
						.append("\n");

			message.append("\n");
			return message.toString();
		}

		AbstractEventLog(LogMessageOrigin source) {
			this.source = source;
		}

		public static final class ButtonInteraction extends AbstractEventLog {
			@Getter
			static String channelName = "bot-button-log";

			@Getter
			static String threadName = "button-log";

			ButtonInteraction(LogMessageOrigin source) {
				super(source);
			}
		}

		public static final class SlashCommand extends AbstractEventLog {
			@Getter
			static String channelName = "bot-slash-command-log";

			@Getter
			static String threadName = "slash-command-log";

			@Getter
			static String messagePrefix = "Response: ";

			SlashCommand(LogMessageOrigin source, Message commandResponse) {
				super(source);
				super.message = commandResponse.getContentDisplay();
			}
		}
	}
}
