package ti4.message;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.selections.SelectionMenuProvider;

public class BotLogger {
    /**
     * Sends a message to the Primary Async Server's #bot-log channel
     * 
     * @param msg - message to send to the #bot-log channel
     */
    public static void log(String msg) {
        log(null, msg, null);
    }

    public static void logWithTimestamp(String msg) {
        String timeStampedMessage = "`" + StringUtils.rightPad(new Timestamp(System.currentTimeMillis()).toString(), 23) + "`  " + msg;
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

        // Logger logger = LoggerFactory.getLogger(BotLogger.class);
        // logger.info(msg);
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
                String commandString = ((CommandInteractionPayload) event).getCommandString();
                if (e == null) {
                    botLogChannel.sendMessage(channelMention + "\n" + channelName + " [command: `" + commandString + "`]\n" + msg).queue();
                } else {
                    Helper.checkThreadLimitAndArchive(event.getGuild());
                    botLogChannel.sendMessage(channelMention + "\n" + channelName + " [command: `" + commandString + "`]\n" + msg).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
                        MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
                        t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
                    }));
                }
            }
            case ButtonInteractionEvent buttonInteractionEvent -> { //BUTTON EVENT LOGS
                String channelName = event.getChannel().getName();
                String channelMention = event.getChannel().getAsMention();
                Button button = ((ButtonInteraction) event).getButton();
                if (e == null) {
                    botLogChannel.sendMessage(channelMention + "\n" + channelName + " [button: `" + button.getId() + "` pressed]\n" + msg).queue();
                } else {
                    Helper.checkThreadLimitAndArchive(event.getGuild());
                    botLogChannel.sendMessage(channelMention + "\n" + channelName + " [button: `" + button.getId() + "` pressed]\n" + msg).queue(m -> m.createThreadChannel("Stack Trace").setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue(t -> {
                        MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e));
                        t.getManager().setArchived(true).queueAfter(15, TimeUnit.SECONDS);
                    }));
                }
            }
            case StringSelectInteractionEvent sEvent -> { //SELECTION EVENT LOGS
                String channelName = event.getChannel().getName();
                String channelMention = event.getChannel().getAsMention();

                String menuInfo = SelectionMenuProvider.getSelectionMenuDebugText(sEvent);
                String logMsg = channelMention + "\n" + channelName + ". " + menuInfo + "\n" + msg;
                if (e == null) {
                    botLogChannel.sendMessage(logMsg).queue();
                } else {
                    Helper.checkThreadLimitAndArchive(event.getGuild());
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
                    Helper.checkThreadLimitAndArchive(event.getGuild());
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
            List<ThreadChannel> threadChannels = primaryBotLogChannel.getThreadChannels();
            String threadName = "button-log";
            ThreadChannel buttonLogThread = null;
            // SEARCH FOR EXISTING OPEN THREAD
            for (ThreadChannel threadChannel : threadChannels) {
                if (threadChannel.getName().equals(threadName)) {
                    buttonLogThread = threadChannel;
                }
            }

            // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
            if (buttonLogThread == null) {
                List<ThreadChannel> hiddenThreadChannels = primaryBotLogChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel : hiddenThreadChannels) {
                    if (threadChannel.getName().equals(threadName)) {
                        buttonLogThread = threadChannel;
                    }
                }
            }
            if (buttonLogThread == null) return;

            StringBuilder sb = new StringBuilder();
            sb.append(event.getUser().getEffectiveName()).append(" ");
            sb.append("[");
            if (event.getButton().getEmoji() != null) sb.append(event.getButton().getEmoji().getFormatted());
            sb.append(event.getButton().getLabel()).append("]");
            sb.append(" `").append(event.getButton().getId()).append("` ");
            sb.append(event.getMessage().getJumpUrl());
            MessageHelper.sendMessageToChannel(buttonLogThread, sb.toString());
        } catch (Exception e) {
            // Do nothing
        }
    }

    public static void logSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        TextChannel primaryBotLogChannel = getPrimaryBotLogChannel();
        if (primaryBotLogChannel == null) return;
        try {
            List<ThreadChannel> threadChannels = primaryBotLogChannel.getThreadChannels();
            String threadName = "slash-command-log";
            ThreadChannel slashCommandLogThread = null;
            // SEARCH FOR EXISTING OPEN THREAD
            for (ThreadChannel threadChannel : threadChannels) {
                if (threadChannel.getName().equals(threadName)) {
                    slashCommandLogThread = threadChannel;
                }
            }

            // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
            if (slashCommandLogThread == null) {
                List<ThreadChannel> hiddenThreadChannels = primaryBotLogChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel : hiddenThreadChannels) {
                    if (threadChannel.getName().equals(threadName)) {
                        slashCommandLogThread = threadChannel;
                    }
                }
            }
            if (slashCommandLogThread == null) return;

            String sb = event.getUser().getEffectiveName() + " " +
                    "`" + event.getCommandString() + "` " +
                    commandResponseMessage.getJumpUrl();
            MessageHelper.sendMessageToChannel(slashCommandLogThread, sb);
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
        if (ignoredError(e)) return;

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
