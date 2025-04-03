package ti4.message;

import java.util.concurrent.TimeUnit;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.ThreadArchiveHelper;
import ti4.helpers.ThreadGetter;
import ti4.listeners.ModalListener;
import ti4.selections.SelectionMenuProcessor;
import ti4.settings.GlobalSettings;

import javax.annotation.Nonnull;

public class BotLogger {
    /**
     * Sends a message to the Primary Async Server's #bot-log channel
     * 
     * @param msg - message to send to the #bot-log channel
     */
    public static void log(String msg) {
        log(null, msg, null);
    }


    /**
     * Stores the data necessary to send a log message. All elements may be null.
     */
    public static class LogData {
        GenericInteractionCreateEvent event;
        String msg;
        Throwable err;
        boolean includeTimestamp;
    }


    /**
     * Sends a message to #bot-log-info in offending server, else resorting to #bot-log and finally webhook.
     * <p>
     * If err is not null, a full stack trace will be sent in a thread.
     * <p>
     * Assumes that the event specified in data has a guild if it exists.
     *
     * @param data - The log data to show in the log message. If null, the log message will indicate as such.
     */
    public static void info(@Nonnull LogData data) {
        logToChannel(data, "bot-log-info");
    }

    /**
     * Sends a message to #bot-log-warning in offending server, eelse resorting to #bot-log and finally webhook.
     * <p>
     * If err is not null, a full stack trace will be sent in a thread.
     * <p>
     * Assumes that the event specified in data has a guild if it exists.
     *
     * @param data - The log data to show in the log message. If null, the log message will indicate as such.
     */
    public static void warning(@Nonnull LogData data) {
        logToChannel(data, "bot-log-warning");
    }

    /**
     * Sends a message to #bot-log-error in offending server, else resorting to #bot-log and finally webhook.
     * <p>
     * If err is not null, a full stack trace will be sent in a thread.
     * <p>
     * Assumes that the event specified in data has a guild if it exists.
     *
     * @param data - The log data to show in the log message. If null, the log message will indicate as such.
     */
    public static void error(@Nonnull LogData data) {
        logToChannel(data, "bot-log-error");
    }


    /**
     * Sends a log message via the specified bot log channel. Should be used through info(), warning(), or error() methods.
     * <p>
     * Assumes that the event specified in data has a guild if it exists.
     *
     * @param data - The log data to show in the log message. If null, the log message will indicate as such.
     * @param primaryChannelName - The channel to send the log message in. If null, it will default to the websocket.
     */
    private static void logToChannel(@Nonnull LogData data, @Nonnull String primaryChannelName) {
        TextChannel channel = null;
        StringBuilder msg = new StringBuilder();

        // Get text channel and construct log message
        if (data.event != null) {
            channel = data.event.getGuild().getTextChannelsByName(primaryChannelName, false).getFirst();
            if (channel == null) channel = data.event.getGuild().getTextChannelsByName("bot-log", false).getFirst();

            if (channel == null) msg.append("Failed to find logging channel for \"")
                    .append(data.event.getGuild().getName())
                    .append("\". Log sent via webhook to main server.\n");

            // Appending the channel must be done in the switch statement to ensure that it exists
            switch (data.event) {
                case SlashCommandInteractionEvent event -> msg.append("[")
                        .append(data.event.getChannel().getName())
                        .append("](")
                        .append(event.getChannel().getAsMention())
                        .append(") ")
                        .append(event.getUser().getEffectiveName())
                        .append(" used `")
                        .append(event.getCommandString())
                        .append("`\n");
                case ButtonInteractionEvent event -> msg.append("[")
                        .append(data.event.getChannel().getName())
                        .append("](")
                        .append(event.getMessage().getJumpUrl())
                        .append(") ")
                        .append(event.getUser().getEffectiveName())
                        .append(" pressed button ")
                        .append(ButtonHelper.getButtonRepresentation(event.getButton()))
                        .append("\n");
                case StringSelectInteractionEvent event -> msg.append("[")
                        .append(data.event.getChannel().getName())
                        .append("](")
                        .append(event.getMessage().getJumpUrl())
                        .append(") ")
                        .append(event.getUser().getEffectiveName())
                        .append(" selected ")
                        .append(SelectionMenuProcessor.getSelectionMenuDebugText(event))
                        .append("\n");
                case ModalInteractionEvent event -> msg.append("[")
                        .append(data.event.getChannel().getName())
                        .append("](")
                        .append(event.getChannel().getAsMention())
                        .append(") ")
                        .append(event.getUser().getEffectiveName())
                        .append(" used modal ")
                        .append(ModalListener.getModalDebugText(event))
                        .append("\n");
                default -> msg.append("[Unknown event]\n");
            }
        } else msg.append("Failed to find logging channel without event. Log sent via webhook to main server.\n");

        if (data.includeTimestamp) msg.append(DateTimeHelper.getCurrentTimestamp());

        msg.append("> Message: ").append(data.msg == null ? "" : data.msg);
        String compiledMessage = msg.toString();
        int msgLength = compiledMessage.length();

        // Handle message length overflow. Overflow length is derived from previous implementation
        for (int i = 0; i <= msgLength; i += 2000) {
            String msgChunk = compiledMessage.substring(i, Math.min(i + 2000, msgLength));

            if (data.err == null || i + 2000 >= msgLength) { // If length could overflow or there is no error to trace
                if (channel == null) MessageHelper.sendMessageToBotLogWebhook(msgChunk); // Send message on websocket
                else channel.sendMessage(msgChunk).queue(); // Send message on channel

            } else { // Handle errored thread on last send
                ThreadArchiveHelper.checkThreadLimitAndArchive(data.event.getGuild());

                if (channel == null) MessageHelper.sendMessageToBotLogWebhook(compiledMessage.substring(i, msgLength));
                else channel.sendMessage(compiledMessage.substring(i))
                        .queue(m -> m.createThreadChannel("Stack Trace")
                                .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
                                .queue(t -> {
                                    MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(data.err));
                                    t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
                                })
                        );
            }

        }
    }


    public static void logWithTimestamp(String msg) {
        String timeStampedMessage = DateTimeHelper.getCurrentTimestamp() + "  " + msg;
        log(null, timeStampedMessage, null);
    }

    /**
     * Sends a message to the Primary Async Server's #bot-log channel, including stack trace.
     * <p>
     * Will create a thread and post the full Stack Trace when supplied with an Exception
     * 
     * @param msg - message to send to the #bot-log channel
     * @e - Exception
     */
    public static void log(String msg, Throwable e) {
        log(null, msg, e);
    }

    /**
     * Sends a message to the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
     * 
     * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
     */
    public static void log(GenericInteractionCreateEvent event, String msg) {
        log(event, msg, null);
    }

    /**
     * Sends just the stack trace the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
     * <p>
     * Will create a thread and post the full Stack Trace
     * 
     * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
     */
    public static void log(GenericInteractionCreateEvent event, Exception e) {
        log(event, null, e);
    }

    /**
     * Sends a message to the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
     * <p>
     * Will create a thread and post the full Stack Trace when supplied with an Exception
     * 
     * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
     * @param e Exception
     */
    public static void log(GenericInteractionCreateEvent event, String msg, Throwable e) {
        if (ignoredError(e)) return;

        TextChannel botLogChannel = getBotLogChannel(event);
        if (msg == null) msg = "";

        System.out.println("[BOT-LOG] " + msg);

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

    public static void logButton(ButtonInteractionEvent event) {
        TextChannel primaryBotLogChannel = getPrimaryBotLogChannel();
        if (primaryBotLogChannel == null) return;
        try {
            String threadName = "button-log";
            ThreadGetter.getThreadInChannel(primaryBotLogChannel, threadName,
                threadChannel -> {
                    String sb = event.getUser().getEffectiveName() + " " + ButtonHelper.getButtonRepresentation(event.getButton()) + event.getMessage().getJumpUrl();
                    MessageHelper.sendMessageToChannel(threadChannel, sb);
                });
        } catch (Exception e) {
            // Do nothing
        }
    }

    public static void logSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        TextChannel primaryBotLogChannel = getPrimaryBotLogChannel();
        if (primaryBotLogChannel == null) return;
        try {
            String threadName = "slash-command-log";
            ThreadGetter.getThreadInChannel(primaryBotLogChannel, threadName,
                threadChannel -> {
                    String sb = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + commandResponseMessage.getJumpUrl();
                    MessageHelper.sendMessageToChannel(threadChannel, sb);
                });
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * Retreives either the event's guild's #bot-log channel, or, if that is null, the Primary server's #bot-log channel.
     */
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
            BotLogger.log("Encountered REST error", e);
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
}
