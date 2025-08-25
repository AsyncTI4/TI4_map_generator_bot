package ti4.message.logging;

import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.cron.CronManager;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DiscordWebhook;
import ti4.helpers.ThreadArchiveHelper;
import ti4.message.MessageHelper;
import ti4.service.statistics.SREStats;
import ti4.settings.GlobalSettings;

@UtilityClass
public class BotLogger {

    private static final int DISCORD_UNKNOWN_ERROR_STATUS_CODE = 10008;
    private static final Object LAST_SCHEDULED_WEBHOOK_LOCK = new Object();
    private static final long DISCORD_RATE_LIMIT = 50; // Min time in millis between discord webhook messages
    private static final int MAX_DISCORD_MESSAGE_SIZE = 2000;
    private static final int SECONDS_TO_WAIT_BEFORE_QUEUEING_STACKTRACE = 15;

    private static volatile long lastScheduledWebhook;

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

    public static void info(@Nonnull LogOrigin logOrigin, @Nonnull String message) {
        logToChannel(logOrigin, message, null, LogSeverity.Info);
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

    public static void warning(@Nonnull LogOrigin logOrigin, @Nonnull String message) {
        logToChannel(logOrigin, message, null, LogSeverity.Warning);
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

    public static void error(
            @Nonnull GenericInteractionCreateEvent event, @Nonnull String message, Throwable throwable) {
        logToChannel(new LogOrigin(event), message, throwable, LogSeverity.Error);
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

    public static void error(@Nonnull LogOrigin logOrigin, @Nonnull String message, @Nullable Throwable err) {
        logToChannel(logOrigin, message, err, LogSeverity.Error);
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
    public static void error(@Nullable LogOrigin origin, @Nonnull String message) {
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
    private static void logToChannel(
            @Nullable LogOrigin origin,
            @Nonnull String message,
            @Nullable Throwable err,
            @Nonnull LogSeverity severity) {
        // Count Error-severity logs once per entry
        if (severity == LogSeverity.Error) {
            SREStats.incrementErrorCount();
        }

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
            msg.append(DateTimeHelper.getCurrentTimestamp());
        }
        channel = getLogChannel(severity);

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
        for (int i = 0; i <= msgLength; i += MAX_DISCORD_MESSAGE_SIZE) {
            String msgChunk = compiledMessage.substring(i, Math.min(i + MAX_DISCORD_MESSAGE_SIZE, msgLength));

            if (err == null
                    || i + MAX_DISCORD_MESSAGE_SIZE
                            < msgLength) { // If length could overflow or there is no error to trace
                if (channel == null) scheduleWebhookMessage(msgChunk); // Send message on webhook
                else channel.sendMessage(msgChunk).queue(); // Send message on channel
            } else { // Handle error on last send
                ThreadArchiveHelper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);

                if (channel == null) {
                    scheduleWebhookMessage(msgChunk); // Send message on webhook
                } else {
                    channel.sendMessage(msgChunk).queue(m -> m.createThreadChannel("Stack Trace")
                            .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
                            .queue(t -> {
                                MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(err));
                                t.getManager()
                                        .setArchived(true)
                                        .queueAfter(SECONDS_TO_WAIT_BEFORE_QUEUEING_STACKTRACE, TimeUnit.SECONDS);
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

    public static void logButton(ButtonInteractionEvent event) {
        LogBufferManager.addLogMessage(new ButtonInteractionEventLog(new LogOrigin(event)));
    }

    public static void logSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        LogBufferManager.addLogMessage(new SlashCommandEventLog(new LogOrigin(event), commandResponseMessage));
    }

    public static void logCron(String message) {
        LogBufferManager.addLogMessage(new CronEventLog(message));
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

    /**
     * Get the most relevant log channel for this source. Priority is to severity.channelName, then "#bot-log", then returns null.
     *
     * @param severity - The severity of the log message, used to find the appropriate channel based on LogSeverity.channelName
     * @return The most relevant logging TextChannel
     */
    @Nullable
    private TextChannel getLogChannel(@Nonnull LogSeverity severity) {
        Guild guild = AsyncTI4DiscordBot.guildPrimary;
        if (guild == null) return null;

        return guild.getTextChannelsByName(severity.channelName, false).stream()
                .findFirst()
                .orElse(guild.getTextChannelsByName("bot-log", false).stream()
                        .findFirst()
                        .orElse(null));
    }
}
