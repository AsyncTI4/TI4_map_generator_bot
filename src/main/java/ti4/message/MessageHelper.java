package ti4.message;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cards.CardsInfo;
import ti4.map.Map;
import ti4.map.MapManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MessageHelper {

    public static void sendMessageToChannel(SlashCommandInteractionEvent event, String messageText, String... reaction) {
        splitAndSent(messageText, event.getChannel(), event, reaction);
    }

    public static void sendMessageToChannelWithButtons(SlashCommandInteractionEvent event, String messageText, Button... buttons) {
        splitAndSent(messageText, event.getChannel(), event, null, buttons);
    }

    public static void sendMessageToChannelWithButtons(MessageChannel channel, String messageText, Guild guild, Button... buttons) {
        splitAndSent(messageText, channel, null, guild, buttons);
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
        replyToMessage(event, file, false);
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, File file, boolean forceShowMap) {

        try {
            String gameName = event.getChannel().getName();
            gameName = gameName.replace(CardsInfo.CARDS_INFO, "");
            gameName = gameName.substring(0, gameName.indexOf("-"));
            Map activeMap = MapManager.getInstance().getMap(gameName);
            if (!activeMap.isFoWMode() || forceShowMap) {
                sendFileToChannel(event.getChannel(), file);
                replyToMessageTI4Logo(event);
            } else {
                replyToMessage(event, "Map updated successfully. Use /special map_info to check the systems.");
            }
        }
        catch (Exception e){
            replyToMessage(event, "Could not send response, use /show_game or contact Admins or Bothelper");
        }
    }

    public static void sentToMessageToUser(GenericInteractionCreateEvent event, String messageText) {
        event.getUser().openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }

    private static void splitAndSent(String messageText, MessageChannel channel) {
        splitAndSent(messageText, channel, null, null, "");
    }

    private static void splitAndSent(String messageText, MessageChannel channel, SlashCommandInteractionEvent event, String... reaction) {
        Integer messageLength = messageText.length();
        if (messageLength > 1500) {
            List<String> texts = new ArrayList<>();
            int index = 0;
            while (index < messageLength) {
                String next1500Chars = messageText.substring(index, Math.min(index + 1500, messageLength));
                Integer lastNewLineIndex = next1500Chars.lastIndexOf("\n") + 1; // number of chars until right after the last \n
                String textToAdd = "";
                if (lastNewLineIndex > 0) {
                    textToAdd = next1500Chars.substring(0, lastNewLineIndex);
                    index += lastNewLineIndex;
                } else {
                    textToAdd = next1500Chars;
                    index += next1500Chars.length();
                }
                texts.add(textToAdd);
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

    private static void splitAndSent(String messageText, MessageChannel channel, SlashCommandInteractionEvent event, Guild guild, Button... buttons) {
        Integer messageLength = messageText.length();
        if (messageLength > 1500) {
            List<String> texts = new ArrayList<>();
            int index = 0;
            while (index < messageLength) {
                String next1500Chars = messageText.substring(index, Math.min(index + 1500, messageLength));
                Integer lastNewLineIndex = next1500Chars.lastIndexOf("\n") + 1; // number of chars until right after the last \n
                String textToAdd = next1500Chars.substring(0, lastNewLineIndex);
                texts.add(textToAdd);
                index += lastNewLineIndex;
            }
            for (String text : texts) {
                channel.sendMessage(text).queue();
            }
        } else {
            if (event == null && guild == null || buttons == null || buttons.length == 0) {
                channel.sendMessage(messageText).queue();
            } else {
                Guild guild_ = guild == null ? event.getGuild() : guild;
                if (guild_ != null) {
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

    public static void sentToMessageToUser(GenericCommandInteractionEvent event, String messageText, User user) {
        user.openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }
}
