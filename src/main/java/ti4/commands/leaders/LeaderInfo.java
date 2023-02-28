package ti4.commands.leaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class LeaderInfo extends LeaderSubcommandData {
    public static final String CARDS_INFO = Constants.CARDS_INFO_THREAD_PREFIX;
    private static HashMap<Map, TextChannel> threadTextChannels = new HashMap<>();
    
    public LeaderInfo() {
        super(Constants.INFO, "Send Leader info to your Cards-Info thread");
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false));
    }

    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply();
        Map activeMap = getActiveMap();
        User user = getUser();
        Player player = activeMap.getPlayer(user.getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
           editReplyMessage(event, "Player could not be found");
            return;
        }
        editReplyMessage(event, Helper.getPlayerRepresentation(event, player) + " Leader Info:");

        String leaderInfo = getLeaderInfo(activeMap, player);

        if (event != null) {
            OptionMapping option = event.getOption(Constants.DM_CARD_INFO);
            if (option != null && option.getAsBoolean()) { 
                MessageHelper.sendMessageToUser(leaderInfo, user);
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), leaderInfo);
    }

    public static String getLeaderInfo(Map activeMap, Player player) {
        // LEADERS
        StringBuilder leaderSB = new StringBuilder();
        leaderSB.append("_ _\n");
        leaderSB.append("**Leaders:**").append("\n");
        for (Leader leader : player.getLeaders()) {
            if (leader.isLocked()) {
                leaderSB.append("LOCKED: ").append(Helper.getLeaderLockedRepresentation(player, leader)).append("\n");
            } else if (leader.isExhausted()) {
                leaderSB.append("EXHAUSTED: ").append("~~").append(Helper.getLeaderFullRepresentation(player, leader)).append("~~\n");
            } else if (leader.isActive()) {
                leaderSB.append("ACTIVE: ").append(Helper.getLeaderFullRepresentation(player, leader)).append("\nActive Hero will be purged during `/status cleanup`\n");
            } else {
                leaderSB.append(Helper.getLeaderFullRepresentation(player, leader)).append("\n");
            }
        }
        
        //PROMISSORY NOTES
        LinkedHashMap<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            //PLAY AREA PROMISSORY NOTES
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    String pnData = Mapper.getPromissoryNote(pn.getKey(), false);
                    if (pnData.contains("Alliance")) {
                        String[] split = pnData.split(";");
                        if (split.length < 2) continue;
                        String colour = split[1];
                        for (Player player_ : activeMap.getPlayers().values()) {
                            if (player_.getColor().equalsIgnoreCase(colour)) {
                                Leader playerLeader = player_.getLeader(Constants.COMMANDER);
                                leaderSB.append("ALLIANCE: ");
                                if (playerLeader.isLocked()) {
                                    leaderSB.append("(LOCKED) ").append(Helper.getLeaderLockedRepresentation(player_, playerLeader)).append("\n");
                                } else {
                                    leaderSB.append(Helper.getLeaderFullRepresentation(player_, playerLeader)).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        //ADD YSSARIL AGENT REFERENCE
        if (player.getFaction().equals("yssaril")) {
            leaderSB.append("_ _\n");
            leaderSB.append("**Other Faction's Agents:**").append("\n");
            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_ != player) {
                    if (player.getLeader(Constants.AGENT).isExhausted()) {
                        leaderSB.append("EXHAUSTED: ").append(Helper.getLeaderFullRepresentation(player_, player_.getLeader(Constants.AGENT))).append("\n");
                    } else {
                        leaderSB.append(Helper.getLeaderFullRepresentation(player_, player_.getLeader(Constants.AGENT))).append("\n");
                    }
                }
            }
        }

        //ADD MAHACT IMPERIA REFERENCE
        if (player.getFaction().equals("mahact")) {
            leaderSB.append("_ _\n");
            leaderSB.append("**Imperia Commanders:**").append("\n");
            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_ != player) {
                    if (player.getMahactCC().contains(player_.getColor())) {
                        leaderSB.append(Helper.getLeaderFullRepresentation(player_, player_.getLeader(Constants.COMMANDER))).append("\n");
                    }
                }
            }
        }
    
        return leaderSB.toString();
    }



    // private static MessageChannel getCardsInfoChannel(@Nullable SlashCommandInteractionEvent event, @Nullable ButtonInteractionEvent buttonEvent, Map activeMap, Player player) {
    //     User userById = event != null ? event.getJDA().getUserById(player.getUserID()) : (buttonEvent != null ? buttonEvent.getJDA().getUserById(player.getUserID()) : null);
    //     if (userById != null) {

    //         if (activeMap.isCommunityMode() && player.getPrivateChannel() != null) {
    //             //TODO: switch to this line if we want to show the buttons on cards in community mode
    //             //     sendCardInfoToChannel(player.getPrivateChannel(), null, soText, soButtons, acText, acButtons, pnText);
    //             return player.getPrivateChannel();
    //         } else {
    //             try {
    //                 MessageChannel channel = event != null ? event.getChannel() : buttonEvent.getChannel();
    //                 MessageChannelUnion channelUnion = event != null ? event.getChannel() : buttonEvent.getChannel();
    //                 if (activeMap.isFoWMode()) {
    //                     if (player.getPrivateChannel() == null) {
    //                         MessageHelper.sendMessageToChannel(channel, "Private channels are not set up for this game. Messages will be suppressed.");
    //                     } else {
    //                         channel = player.getPrivateChannel();
    //                     }
    //                 }

    //                 if (channel == null) {
    //                     BotLogger.log("Could not find channel");
    //                     return null;
    //                 }

    //                 ChannelType type = channel.getType();

    //                 TextChannel textChannel = threadTextChannels.get(activeMap);
    //                 if (activeMap.isFoWMode()) {
    //                     textChannel = (TextChannel) channel;
    //                 }
    //                 if (textChannel == null) {
    //                     String mainChannelName = activeMap.getName() + Constants.ACTIONS_CHANNEL_SUFFIX;
    //                     for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {
    //                         if (textChannel_.getName().equals(mainChannelName)) {
    //                             textChannel = textChannel_;
    //                             threadTextChannels.put(activeMap, textChannel);
    //                             break;
    //                         }
    //                     }

    //                     if (textChannel == null) {
    //                         if (ChannelType.GUILD_PUBLIC_THREAD.equals(type) || ChannelType.GUILD_PRIVATE_THREAD.equals(type)) {
    //                             IThreadContainer parentChannel = channelUnion.asThreadChannel().getParentChannel();
    //                             if (parentChannel instanceof TextChannel) {
    //                                 textChannel = (TextChannel) parentChannel;
    //                             } else {
    //                                 MessageHelper.sendMessageToUser(cardInfo, userById);
    //                                 MessageHelper.sendMessageToUser("Please Execute info command in non thread channel", userById);
    //                                 return;
    //                             }
    //                         } else {
    //                             textChannel = channelUnion.asTextChannel();
    //                         }
    //                     }
    //                 }
    //                 List<ThreadChannel> threadChannels = textChannel.getThreadChannels();
    //                 boolean threadFound = false;
    //                 String threadName = CARDS_INFO + activeMap.getName() + "-" + player.getUserName().replaceAll("/", "");
    //                 String playerPing = threadName + " " + Helper.getPlayerPing(player);

    //                 for (ThreadChannel threadChannel : threadChannels) {
    //                     if (threadChannel.getName().equals(threadName)) {
    //                         sendCardInfoToChannel(threadChannel, playerPing, soText, soButtons, acText, acButtons, pnText);
    //                         threadFound = true;
    //                         break;
    //                     }
    //                 }
    //                 if (!threadFound) {
    //                     ThreadChannel new_thread = textChannel.createThreadChannel(threadName, true)
    //                         .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
    //                         .setInvitable(false)
    //                         .complete();
    //                     sendCardInfoToChannel(new_thread, playerPing, soText, soButtons, acText, acButtons, pnText);
    //                 }
    //             } catch (Exception e) {
    //                 BotLogger.log("Could not create Private Thread");
    //             }
    //         }
    //     } else {
    //         MessageHelper.sendMessageToUser("Player: " + player.getUserName() + " not found", event != null ? event : buttonEvent);
    //     }
    // }


    // private static void sendCardInfoToChannel(MessageChannel privateChannel, String ping, String leaderText) {
    //     String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
    //     String acPlayMsg = "_ _\nClick a button below to play an Action Card";
    //     String text = ping == null ? null : ping + "\n";
    //     MessageHelper.sendMessageToChannel(privateChannel, text);
    //     MessageHelper.sendMessageToChannel(privateChannel, ac);
    //     messageList = getMessageObject(acPlayMsg, acButtons);
    //     for (MessageCreateData message : messageList) {
    //         privateChannel.sendMessage(message).queue();
    //     }
    //     MessageHelper.sendMessageToChannel(privateChannel, pn);
    // }

    // private static List<MessageCreateData> getMessageObject(String message, List<Button> buttons) {
    //     buttons.removeIf(Objects::isNull);
    //     List<List<Button>> partitions = ListUtils.partition(buttons, 5);
    //     List<ActionRow> actionRows = new ArrayList<>();
    //     for (List<Button> partition : partitions) {
    //         actionRows.add(ActionRow.of(partition));
    //     }
    //     List<List<ActionRow>> partitionActionRows = ListUtils.partition(actionRows, 5);
    //     List<MessageCreateData> buttonMessages = new ArrayList<>();

    //     for (List<ActionRow> partitionActionRow : partitionActionRows) {
    //         buttonMessages.add(new MessageCreateBuilder()
    //                 .addContent(message)
    //                 .addComponents(partitionActionRow).build());
    //     }
    //     return buttonMessages;
    // }
}
