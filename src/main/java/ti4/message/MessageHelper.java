package ti4.message;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.ButtonListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessageHelper {

    public static void sendMessageToChannel(SlashCommandInteractionEvent event, String messageText, String... reaction) {
        splitAndSent(messageText, event.getChannel(), event, reaction);
    }

    public static void sendMessageToChannelWithButtons(SlashCommandInteractionEvent event, String messageText, Button... buttons) {
        splitAndSent(messageText, event.getChannel(), event, buttons);
    }

    public static void sendMessageToChannel(MessageChannel channel, String messageText) {
        splitAndSent(messageText, channel);
    }

    public static void sendFileToChannel(MessageChannel channel, File file) {
        channel.sendFile(file).queue();
    }


    public static void replyToMessageTI4Logo(SlashCommandInteractionEvent event) {
        replyToMessage(event, "");
    }
    public static void replyToMessage(SlashCommandInteractionEvent event, String messageText) {
        if (messageText.length() > 1500) {
            splitAndSent(messageText, event.getChannel());
            event.getHook().sendMessage("Message to long for replay, sent all information in base messages").queue();
        } else {
            if (!messageText.isEmpty()) {
                splitAndSent(messageText, event.getChannel());
            }
            event.getHook().sendMessage("-").queue();
            //Deletes slash command
//            event.getHook().sendMessage("-").queue(msg -> {
//                msg.delete().queueAfter(1, TimeUnit.SECONDS);
//            });
        }
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, File file) {
        sendFileToChannel(event.getChannel(), file);
        replyToMessageTI4Logo(event);
    }

    public static void sentToMessageToUser(SlashCommandInteractionEvent event, String messageText) {
        event.getUser().openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }

    private static void splitAndSent(String messageText, MessageChannel channel) {
        splitAndSent(messageText, channel, null, "");
    }
    private static void splitAndSent(String messageText, MessageChannel channel, SlashCommandInteractionEvent event, String... reaction) {
        if (messageText.length() > 1500) {
            List<String> texts = new ArrayList<>();
            int index = 0;
            while (index < messageText.length()) {
                texts.add(messageText.substring(index, Math.min(index + 1500, messageText.length())));
                index += 1500;
            }
            for (String text : texts) {
                channel.sendMessage(text).queue();
            }
        } else {
            if (event == null || reaction == null || reaction.length == 0) {
                channel.sendMessage(messageText).queue();
            } else {
                Guild guild = event.getGuild();
                if (guild != null) {
                    Message complete = channel.sendMessage(messageText).complete();
                    for (String reactionID : reaction) {
                        Emote emoteById = guild.getEmoteById(reactionID);
                        if (emoteById == null) {
                            continue;
                        }
                        complete.addReaction(emoteById).queue();
                    }
                    return;
                }
                channel.sendMessage(messageText).queue();
            }
        }
    }

    private static void splitAndSent(String messageText, MessageChannel channel, SlashCommandInteractionEvent event, Button... buttons) {
        if (messageText.length() > 1500) {
            List<String> texts = new ArrayList<>();
            int index = 0;
            while (index < messageText.length()) {
                texts.add(messageText.substring(index, Math.min(index + 1500, messageText.length())));
                index += 1500;
            }
            for (String text : texts) {
                channel.sendMessage(text).queue();
            }
        } else {
            if (event == null || buttons == null || buttons.length == 0) {
                channel.sendMessage(messageText).queue();
            } else {
                Guild guild = event.getGuild();
                if (guild != null) {
                    Message message = new MessageBuilder()
                            .append(messageText)
                            .setActionRows(ActionRow.of(buttons)).build();
                    channel.sendMessage(message).queue();
                    return;
                }
                channel.sendMessage(messageText).queue();
            }
        }
    }

    public static void sentToMessageToUser(SlashCommandInteractionEvent event, String messageText, User user) {
        user.openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }
}
