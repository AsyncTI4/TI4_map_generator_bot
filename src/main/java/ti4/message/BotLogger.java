package ti4.message;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.MapGenerator;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class BotLogger {
    /** Sends a message to the Primary Async Server's #bot-log channel
     * @param msg - message to send to the #bot-log channel
     */
    public static void log(String msg) {
        log((GenericInteractionCreateEvent) null, msg, null);
    }

    /** Sends a message to the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
     * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
     * @param msg
     */
    public static void log(GenericInteractionCreateEvent event, String msg) {
        log(event, msg, null);
    }

    /** Sends just the stack trace the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
     * <p> Will create a thread and post the full Stack Trace
     * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
     * @param msg
     */
    public static void log(GenericInteractionCreateEvent event, Exception e) {
        log(event, null, e);
    }

    /** Sends a message to the offending server's #bot-log channel, or if it does not exist, the Primary server's #bot-log channel
     *  <p> Will create a thread and post the full Stack Trace when supplied with an Exception
     * @param event GenericInteractionCreateEvent, handling for null, SlashCommandInteractionEvent, and ButtonInteractionEvent
     * @param msg
     * @param e Exception
     */
    public static void log(GenericInteractionCreateEvent event, String msg, Exception e) {
        TextChannel botLogChannel = getBotLogChannel(event);
        if (msg == null) msg = "";
        if (botLogChannel != null) {
            if (event == null) {
                botLogChannel.sendMessage(msg).queue();
            } else if (event instanceof SlashCommandInteractionEvent) {
                String channelName = event.getChannel().getName();
                String commandString = ((SlashCommandInteractionEvent) event).getCommandString();
                if (e == null) {
                    botLogChannel.sendMessage(channelName + " [command: `" + commandString + "`] " + msg).queue();
                } else {
                    botLogChannel.sendMessage(channelName + " [command: `" + commandString + "`] " + msg).queue(m -> m.createThreadChannel("Stack Trace").queue(t -> MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e))));
                }
            } else if (event instanceof ButtonInteractionEvent) {
                String channelName = event.getChannel().getName();
                Button button = ((ButtonInteractionEvent) event).getButton();
                if (e == null) {
                    botLogChannel.sendMessage(channelName + " [button: `" + button.getId() + "` pressed] " + msg).queue();
                } else {
                    botLogChannel.sendMessage(channelName + " [button: `" + button.getId() + "` pressed] " + msg).queue(m -> m.createThreadChannel("Stack Trace").queue(t -> MessageHelper.sendMessageToChannel(t, ExceptionUtils.getStackTrace(e))));
                }
            }
        }
    }

    /** Retreives either the event's guild's #bot-log channel, or, if that is null, the Primary server's #bot-log channel.
     * @param event
     * @return
     */
    private static TextChannel getBotLogChannel(GenericInteractionCreateEvent event) {
        if (event == null) return null;
        TextChannel botLogChannel = null;
        for (TextChannel textChannel : event.getGuild().getTextChannels()) {
            if ("bot-log".equals(textChannel.getName())) {
                botLogChannel = textChannel;
            }
        }
        if (botLogChannel == null) { //USE PRIMARY SERVER'S BOTLOG CHANNEL
            for (TextChannel textChannel : MapGenerator.guildPrimary.getTextChannels()) {
                if ("bot-log".equals(textChannel.getName())) {
                    botLogChannel = textChannel;
                }
            }
        }
        return botLogChannel;
    }
}
