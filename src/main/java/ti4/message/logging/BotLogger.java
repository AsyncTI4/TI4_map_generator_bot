package ti4.message.logging;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
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
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.cron.CronManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiscordWebhook;
import ti4.helpers.ThreadArchiveHelper;
import ti4.listeners.ModalListener;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.selections.SelectionMenuProcessor;
import ti4.settings.GlobalSettings;

@UtilityClass
public class BotLogger {

    private static final int DISCORD_UNKNOWN_ERROR_STATUS_CODE = 10008;
    private static final Object LAST_SCHEDULED_WEBHOOK_LOCK = new Object();
    private static final long DISCORD_RATE_LIMIT = 50; // Min time in millis between discord webhook messages

    private static volatile long lastScheduledWebhook;

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

    public static void error(
            @Nonnull String message, @Nullable Game game, @Nullable GenericInteractionCreateEvent event) {
        if (event != null && game != null) error(new LogMessageOrigin(event, game), message);
        else if (event != null) error(new LogMessageOrigin(event), message);
        else if (game != null) error(new LogMessageOrigin(game), message);
        else error(message);
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
    private static void logToChannel(
            @Nullable LogMessageOrigin origin,
            @Nonnull String message,
            @Nullable Throwable err,
            @Nonnull LogSeverity severity) {
        TextChannel channel;
        StringBuilder msg = new StringBuilder();

        // **__`2025-01-01 xx:xx:xx.xxx`__** user pressed button blahblahblah
        // Game Info: [Tabletalk] [Actions]
        // Message: jajfdkljakdsjflkajsdlkfjaksdjfklajskdflasd
        boolean multiline = origin != null && (origin.getEventString() != null || origin.getGameInfo() != null);

        if (origin != null) {
            // Add header text iff the error spans multiple lines
            if (multiline || err != null) msg.append(severity.headerText);
            msg.append(origin.getOriginTimeFormatted());
            if (origin.getEventString() != null) msg.append(origin.getEventString());
            if (origin.getGameInfo() != null) msg.append(origin.getGameInfo());
        } else {
            origin = new LogMessageOrigin(AsyncTI4DiscordBot.guildPrimary);
            msg.append(origin.getOriginTimeFormatted());
        }
        channel = origin.getLogChannel(severity);

        if (multiline) {
            msg.append("Message: ");
        }
        msg.append(message);
        if (multiline && err != null)
            msg.append("\n_ _"); // Append a blank line iff the error spans multiple lines and there's no stack trace

        // Send off message
        String compiledMessage = msg.toString();
        int msgLength = compiledMessage.length();

        // Handle message length overflow. Overflow length is derived from previous implementation
        for (int i = 0; i <= msgLength; i += 2000) {
            String msgChunk = compiledMessage.substring(i, Math.min(i + 2000, msgLength));

            if (err == null || i + 2000 < msgLength) { // If length could overflow or there is no error to trace
                if (channel == null) scheduleWebhookMessage(msgChunk); // Send message on webhook
                else channel.sendMessage(msgChunk).queue(); // Send message on channel

            } else { // Handle error on last send
                if (origin.getGuild() != null) { // Second check may not be necessary but this is a hotfix.
                    ThreadArchiveHelper.checkThreadLimitAndArchive(origin.getGuild());
                } else {
                    ThreadArchiveHelper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);
                }

                if (channel == null) {
                    scheduleWebhookMessage(msgChunk); // Send message on webhook
                } else {
                    channel.sendMessage(msgChunk).queue(m -> m.createThreadChannel("Stack Trace")
                            .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
                            .queue(t -> {
                                MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(err));
                                t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
                            }));
                }
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
        synchronized (LAST_SCHEDULED_WEBHOOK_LOCK) {
            timeToNextMessage = lastScheduledWebhook + DISCORD_RATE_LIMIT - System.currentTimeMillis();
            lastScheduledWebhook = System.currentTimeMillis() + Math.max(timeToNextMessage, 0);
        }

        if (timeToNextMessage <= 0) {
            sendMessageToBotLogWebhook(message);
        } else {
            CronManager.scheduleOnce(
                    BotLogger.class,
                    () -> sendMessageToBotLogWebhook(message),
                    timeToNextMessage,
                    TimeUnit.MILLISECONDS);
        }
    }

    private static void sendMessageToBotLogWebhook(String message) {
        String botLogWebhookURL =
                switch (AsyncTI4DiscordBot.guildPrimaryID) {
                    case Constants.ASYNCTI4_HUB_SERVER_ID -> // AsyncTI4 Primary HUB Production Server
                        "https://discord.com/api/webhooks/1106562763708432444/AK5E_Nx3Jg_JaTvy7ZSY7MRAJBoIyJG8UKZ5SpQKizYsXr57h_VIF3YJlmeNAtuKFe5v";
                    case "1059645656295292968" -> // PrisonerOne's Test Server
                        "https://discord.com/api/webhooks/1159478386998116412/NiyxcE-6TVkSH0ACNpEhwbbEdIBrvTWboZBTwuooVfz5n4KccGa_HRWTbCcOy7ivZuEp";
                    case null, default -> null;
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

        // Adding so we don't cause an exception by attempting to log
        if (msg.length() > 2000) {
            // TODO: handle this better, don't truncate
            String ellipses = "...\n### Error message was too long and was truncated here\n";
            msg = msg.substring(0, 2000 - ellipses.length() - 1) + ellipses;
        }

        if (botLogChannel == null) {
            String name;
            if (event == null) {
                return;
            } else {
                name = event.getGuild().getName();
            }
            MessageHelper.sendMessageToBotLogWebhook("Failed to find bot-log channel for server: " + name
                    + "\nSending via webhook to main server.\n>" + msg);
            return;
        }

        switch (event) {
            case SlashCommandInteractionEvent slashCommandInteractionEvent -> { // SLASH COMMAND EVENT LOGS
                String channelName = event.getChannel().getName();
                String channelMention = event.getChannel().getAsMention();
                String commandString = slashCommandInteractionEvent.getCommandString();
                String message = "[" + channelName + "](" + channelMention + ") "
                        + event.getUser().getEffectiveName() + " used: `" + commandString + "`\n> Error: " + msg;
                logMessage(event, e, botLogChannel, message);
            }
            case ButtonInteractionEvent buttonInteractionEvent -> { // BUTTON EVENT LOGS
                String channelName = event.getChannel().getName();
                Button button = buttonInteractionEvent.getButton();
                String message = "[" + channelName + "]("
                        + buttonInteractionEvent.getMessage().getJumpUrl() + ") "
                        + event.getUser().getEffectiveName() + " pressed button: "
                        + ButtonHelper.getButtonRepresentation(button) + "\n> Error: " + msg;
                logMessage(event, e, botLogChannel, message);
            }
            case StringSelectInteractionEvent sEvent -> { // SELECTION EVENT LOGS
                String channelName = event.getChannel().getName();
                String channelMention = event.getChannel().getAsMention();

                String menuInfo = SelectionMenuProcessor.getSelectionMenuDebugText(sEvent);
                String logMsg = channelMention + "\n" + channelName + ". " + menuInfo + "\n" + msg;
                logMessage(event, e, botLogChannel, logMsg);
            }
            case ModalInteractionEvent mEvent -> { // MODAL EVENT LOGS
                String channelName = event.getChannel().getName();
                String channelMention = event.getChannel().getAsMention();

                String menuInfo = ModalListener.getModalDebugText(mEvent);
                String logMsg = channelMention + "\n" + channelName + ". " + menuInfo + "\n" + msg;
                logMessage(event, e, botLogChannel, logMsg);
            }
            case null -> logMessage(event, e, botLogChannel, msg); // NON-EVENT LOGS
            default -> logMessage(event, e, botLogChannel, "[unknown event]\n" + msg);
        }
    }

    private static void logMessage(
            GenericInteractionCreateEvent event, Throwable e, TextChannel botLogChannel, String message) {
        if (e == null) {
            botLogChannel.sendMessage(message).queue();
        } else {
            ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
            botLogChannel.sendMessage(message).queue(m -> m.createThreadChannel("Stack Trace")
                    .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
                    .queue(t -> {
                        MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
                        t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
                    }));
        }
    }

    public static void logButton(ButtonInteractionEvent event) {
        LogBufferManager.addLogMessage(new ButtonInteractionEventLog(new LogMessageOrigin(event)));
    }

    public static void logSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        LogBufferManager.addLogMessage(new SlashCommandEventLog(new LogMessageOrigin(event), commandResponseMessage));
    }

    @Deprecated
    public static TextChannel getBotLogChannel(GenericInteractionCreateEvent event) {
        TextChannel botLogChannel = null;
        if (event != null) {
            botLogChannel = getBotLogChannel(event.getGuild().getTextChannels());
        }
        if (botLogChannel == null) {
            botLogChannel = getBotLogChannel(AsyncTI4DiscordBot.guildPrimary.getTextChannels());
        }
        return botLogChannel;
    }

    private static TextChannel getBotLogChannel(List<TextChannel> textChannels) {
        for (TextChannel textChannel : textChannels) {
            if ("bot-log".equals(textChannel.getName())) {
                return textChannel;
            }
        }
        return null;
    }

    public static void catchRestError(Throwable e) {
        // This has become too annoying, so we are limiting to testing mode/debug mode
        boolean debugMode =
                GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);
        if (System.getenv("TESTING") != null || debugMode) {
            // if it's ignored, it's not actionable.
            if (ignoredError(e)) return;
            error("Encountered REST error", e);
        }
    }

    private static boolean ignoredError(Throwable error) {
        if (error instanceof ErrorResponseException restError) {
            // Typically caused by the bot trying to delete or edit a message that has already been deleted.
            return restError.getErrorCode() == DISCORD_UNKNOWN_ERROR_STATUS_CODE;
        }
        return false;
    }
}
