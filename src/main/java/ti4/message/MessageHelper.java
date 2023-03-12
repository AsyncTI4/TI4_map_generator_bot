package ti4.message;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.MapGenerator;
import ti4.commands.cards.CardsInfo;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MessageHelper {

    public static void sendMessageToChannel(SlashCommandInteractionEvent event, String messageText, String... reaction) {
        splitAndSent(messageText, event.getChannel(), event, false, reaction);
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

    public static void sendMessageToChannelAndPin(MessageChannel channel, String messageText) {
        splitAndSent(messageText, channel, null, true);
    }

    public static void sendFileToChannel(MessageChannel channel, File file) {
        FileUpload fileUpload = FileUpload.fromData(file);
        channel.sendFiles(fileUpload).queue();
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
        replyToMessage(event, file, false, null, false);
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, File file, boolean forceShowMap) {
        replyToMessage(event, file, forceShowMap, null, false);
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, File file, boolean forceShowMap, String messageText, boolean pinMessage) {

        try {
            if (forceShowMap){
                sendMessageWithFile(event.getChannel(), file, messageText, pinMessage);
                replyToMessageTI4Logo(event);
                return;
            }
            String gameName = event.getChannel().getName();
            gameName = gameName.replace(CardsInfo.CARDS_INFO, "");
            gameName = gameName.substring(0, gameName.indexOf("-"));
            Map activeMap = MapManager.getInstance().getMap(gameName);
            if (!activeMap.isFoWMode() || activeMap.isFoWMode() && event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)) {
                sendMessageWithFile(event.getChannel(), file, messageText, pinMessage);
                replyToMessageTI4Logo(event);
            } else {
                replyToMessage(event, "Map updated successfully. Use /special system_info to check the systems.");
            }
        }
        catch (Exception e){
            replyToMessage(event, "Could not send response, use /show_game or contact Admins or Bothelper");
        }
    }

    public static void sendMessageWithFile(MessageChannel channel, File file, String messageText, boolean pinMessage) {
        FileUpload fileUpload = FileUpload.fromData(file);
        MessageCreateBuilder message = new MessageCreateBuilder();
        if (messageText != null) {
            message.addContent(messageText);
        }
        MessageCreateData messageObject = message.addFiles(fileUpload).build();
        channel.sendMessage(messageObject).queue(x -> {
            if (pinMessage) x.pin().queue();
        });
    }

    private static void splitAndSent(String messageText, MessageChannel channel) {
        splitAndSent(messageText, channel, null, false, null, "");
    }

    private static void splitAndSent(String messageText, MessageChannel channel, SlashCommandInteractionEvent event, Boolean pinMessages, String... reaction) {
        if (messageText == null || channel == null || messageText.isEmpty()) {
            // BotLogger.log("`splitAndSent` - `messageText` or `channel` was null");
            return;
        }

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
                channel.sendMessage(text).queue(x -> {
                    if (pinMessages) x.pin().queue();
                });    
            }
        } else {
            if (event == null || reaction == null || reaction.length == 0) {
                channel.sendMessage(messageText).queue(x -> {
                    if (pinMessages) x.pin().queue();
                });
            } else {
                Guild guild = event.getGuild();
                if (guild != null) {
                    channel.sendMessage(messageText).queue(complete -> {
                        if (pinMessages) complete.pin().queue();
                        for (String reactionID : reaction) {
                            Emoji emoteById = guild.getEmojiById(reactionID);
                            if (emoteById == null) {
                                continue;
                            }
                            complete.addReaction(emoteById).queue();
                        }
                    });
                    return;
                }
                channel.sendMessage(messageText).queue(x -> {
                    if (pinMessages) x.pin().queue();
                });
            }
        }
    }

    private static void  splitAndSent(String messageText, MessageChannel channel, SlashCommandInteractionEvent event, Guild guild, Button... buttons) {
        if (messageText == null || channel == null || messageText.isEmpty()) {
            // BotLogger.log("`splitAndSent` - `messageText` or `channel` was null");
            return;
        }
        
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
                    MessageCreateData message = new MessageCreateBuilder()
                            .addContent(messageText)
                            .addComponents(ActionRow.of(buttons)).build();
                    channel.sendMessage(message).queue();
                    return;
                }
                channel.sendMessage(messageText).queue();
            }
        }
    }

    /** Send a private message to the player.
     * 
     * @param player Player to send a message to
     * @param map Active map
     * @param event Event that caused the message
     * @param messageText Message to send
     * @param failText Feedback if the message failed to send
     * @param successText Feedback if the message successfully sent
     * @return True if the message was send successfully, false otherwise
     */
    public static boolean sendPrivateMessageToPlayer(Player player, Map map, SlashCommandInteractionEvent event, String messageText, String failText, String successText) {
        return sendPrivateMessageToPlayer(player, map, event.getChannel(), messageText, failText, successText);
    }

    /** Send a private message to the player. 
     * <p>
     * This implementation does not provide feedback
     *
     * @param player Player to send a message to
     * @param map Active map
     * @param messageText Message to send
     * @return True if the message was send successfully, false otherwise
     */
    public static boolean sendPrivateMessageToPlayer(Player player, Map map, String messageText) {
        return sendPrivateMessageToPlayer(player, map, (MessageChannel) null, messageText, null, null);
    }

    /** Send a private message to the player.
     * 
     * @param player Player to send a message to
     * @param map Active map
     * @param feedbackChannel Channel to send feedback to
     * @param messageText Message to send
     * @param failText Feedback if the message failed to send
     * @param successText Feedback if the message successfully sent
     * @return True if the message was send successfully, false otherwise
     */
    public static boolean sendPrivateMessageToPlayer(Player player, Map map, MessageChannel feedbackChannel, String messageText, String failText, String successText) {
        User user = MapGenerator.jda.getUserById(player.getUserID());
        if (user == null) {
            sendMessageToChannel(feedbackChannel, failText);
            return false;
        } else {
            MessageChannel privateChannel = player.getPrivateChannel();
            if (privateChannel == null) {
                sendMessageToUser(map.getName() + " " + messageText, user);
            } else {
                sendMessageToChannel(privateChannel, messageText);
            }
            sendMessageToChannel(feedbackChannel, successText);
            return true;
        }
    }

    public static void sendMessageToUser(String messageText, GenericInteractionCreateEvent event) {
        sendMessageToUser(messageText, event.getUser());
    }

    public static void sendMessageToUser(String messageText, User user) {
        user.openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }

}
