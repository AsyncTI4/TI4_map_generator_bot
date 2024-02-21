package ti4.commands.leaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class LeaderInfo extends LeaderSubcommandData {
    public LeaderInfo() {
        super(Constants.INFO, "Send Leader info to your Cards-Info thread");
    }

    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply();
        Game activeGame = getActiveGame();
        User user = getUser();
        Player player = activeGame.getPlayer(user.getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        String leaderInfo = getLeaderInfo(activeGame, player);

        OptionMapping option = event.getOption(Constants.DM_CARD_INFO);
        if (option != null && option.getAsBoolean()) {
            MessageHelper.sendMessageToUser(leaderInfo, user);
        }
        sendLeadersInfo(activeGame, player, event);
    }

    public static void sendLeadersInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendLeadersInfo(activeGame, player);
    }

    public static void sendLeadersInfo(Game activeGame, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
            player.getCardsInfoThread(),
            getLeaderInfo(activeGame, player),
            getLeaderButtons(player));
    }

    private static List<Button> getLeaderButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_LEADER_INFO);
        return buttons;
    }

    public static String getLeaderInfo(Game activeGame, Player player) {
        // LEADERS
        StringBuilder leaderSB = new StringBuilder();
        leaderSB.append("_ _\n");
        leaderSB.append("**Leaders Information:**").append("\n");
        for (Leader leader : player.getLeaders()) {
            if (leader.isLocked()) {
                leaderSB.append("LOCKED: ").append(Helper.getLeaderLockedRepresentation(leader)).append("\n");
            } else if (leader.isExhausted()) {
                leaderSB.append("EXHAUSTED: ").append("~~").append(Helper.getLeaderFullRepresentation(leader)).append("~~\n");
            } else if (leader.isActive()) {
                leaderSB.append("ACTIVE: ").append(Helper.getLeaderFullRepresentation(leader)).append("\nActive Hero will be purged during `/status cleanup`\n");
            } else {
                leaderSB.append(Helper.getLeaderFullRepresentation(leader)).append("\n");
            }
        }

        //PROMISSORY NOTES
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            //PLAY AREA PROMISSORY NOTES
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    PromissoryNoteModel pnData = Mapper.getPromissoryNote(pn.getKey());
                    if (pnData.getName().equals("Alliance")) {
                        String color = pnData.getColor().orElse(null);
                        for (Player player_ : activeGame.getPlayers().values()) {
                            if (player_.getColor().equalsIgnoreCase(color)) {
                                Leader playerLeader = player_.unsafeGetLeader(Constants.COMMANDER);
                                if (playerLeader == null) continue;
                                leaderSB.append("ALLIANCE: ");
                                if (playerLeader.isLocked()) {
                                    leaderSB.append("(LOCKED) ").append(Helper.getLeaderLockedRepresentation(playerLeader)).append("\n");
                                } else {
                                    leaderSB.append(Helper.getLeaderFullRepresentation(playerLeader)).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        //ADD YSSARIL AGENT REFERENCE
        if (player.hasLeader("yssarilagent")) {
            leaderSB.append("_ _\n");
            leaderSB.append("**Other Faction's Agents:**").append("\n");
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ != player) {
                    Leader playerLeader = player.unsafeGetLeader(Constants.AGENT);
                    Leader otherPlayerAgent = player_.unsafeGetLeader(Constants.AGENT);
                    if (otherPlayerAgent == null) continue;
                    if (playerLeader != null && playerLeader.isExhausted()) {
                        leaderSB.append("EXHAUSTED: ").append(Helper.getLeaderFullRepresentation(otherPlayerAgent)).append("\n");
                    } else {
                        leaderSB.append(Helper.getLeaderFullRepresentation(otherPlayerAgent)).append("\n");
                    }
                }
            }
        }

        //ADD MAHACT IMPERIA REFERENCE
        if (player.hasAbility("imperia")) {
            leaderSB.append("_ _\n");
            leaderSB.append("**Imperia Commanders:**").append("\n");
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ != player) {
                    if (player.getMahactCC().contains(player_.getColor())) {
                        Leader leader = player_.unsafeGetLeader(Constants.COMMANDER);
                        if (leader == null) continue;
                        leaderSB.append(Helper.getLeaderFullRepresentation(leader)).append("\n");
                    }
                }
            }
        }

        return leaderSB.toString();
    }
}
