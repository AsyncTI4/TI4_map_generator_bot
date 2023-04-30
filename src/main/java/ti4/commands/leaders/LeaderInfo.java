package ti4.commands.leaders;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LeaderInfo extends LeaderSubcommandData {
    public static final String CARDS_INFO = Constants.CARDS_INFO_THREAD_PREFIX;
    private static HashMap<Map, TextChannel> threadTextChannels = new HashMap<>();
    
    public LeaderInfo() {
        super(Constants.INFO, "Send Leader info to your Cards-Info thread");
    }

    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply();
        Map activeMap = getActiveMap();
        User user = getUser();
        Player player = activeMap.getPlayer(user.getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
           sendMessage("Player could not be found");
            return;
        }
        String leaderInfo = getLeaderInfo(activeMap, player);

        if (event != null) {
            OptionMapping option = event.getOption(Constants.DM_CARD_INFO);
            if (option != null && option.getAsBoolean()) { 
                MessageHelper.sendMessageToUser(leaderInfo, user);
            }
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, Helper.getPlayerRepresentation(event, player));
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, leaderInfo);
    }

    public static void sendLeadersInfo(Map activeMap, Player player) {
        //LEADERS INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getLeaderInfo(activeMap, player));

        //BUTTONS
        String leaderPlayMsg = "_ _\nClick a button below to exhaust or purge a Leader";
        List<Button> leaderButtons = getLeaderButtons(activeMap, player);
        if (leaderButtons != null && !leaderButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(leaderPlayMsg, leaderButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeMap);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    } 

    private static List<Button> getLeaderButtons(Map activeMap, Player player) {
        return null;
    }

    public static String getLeaderInfo(Map activeMap, Player player) {
        // LEADERS
        StringBuilder leaderSB = new StringBuilder();
        leaderSB.append("_ _\n");
        leaderSB.append("**Leaders Information:**").append("\n");
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
}
