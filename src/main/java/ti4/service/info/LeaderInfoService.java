package ti4.service.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;

@UtilityClass
public class LeaderInfoService {

    public static void sendLeadersInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + " Somebody" + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendLeadersInfo(game, player);
    }

    public static void sendLeadersInfo(Game game, Player player) {
        // LEADERS
        List<MessageEmbed> leaderEmbeds = getPlayersLeaderEmbeds(player);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), "**Leaders Information:**", leaderEmbeds, getLeaderButtons());

        //PROMISSORY NOTES
        List<MessageEmbed> allianceEmbeds = new ArrayList<>();
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            //PLAY AREA PROMISSORY NOTES
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn.getKey());
                    if (pnModel.getName().equals("Alliance")) {
                        String color = pnModel.getColor().orElse(null);
                        for (Player otherPlayer : game.getPlayers().values()) {
                            if (otherPlayer.getColor().equalsIgnoreCase(color)) {
                                Leader playerLeader = otherPlayer.unsafeGetLeader(Constants.COMMANDER);
                                if (playerLeader == null) {
                                    continue;
                                }
                                allianceEmbeds.add(playerLeader.getLeaderEmbed());
                            }
                        }
                    }
                }
            }
        }
        if (!allianceEmbeds.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), "**Alliance Commanders:**", allianceEmbeds);
        }

        //ADD YSSARIL AGENT REFERENCE
        List<MessageEmbed> yssarilEmbeds = new ArrayList<>();
        if (player.hasLeader("yssarilagent")) {
            for (Player otherPlayer : game.getPlayers().values()) {
                if (otherPlayer != player) {
                    Leader otherPlayerAgent = otherPlayer.unsafeGetLeader(Constants.AGENT);
                    if (otherPlayerAgent == null) {
                        continue;
                    }
                    yssarilEmbeds.add(otherPlayerAgent.getLeaderEmbed());
                }
            }
        }
        if (!yssarilEmbeds.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), LeaderEmojis.YssarilAgent + "**Clever, Clever Agents:**", yssarilEmbeds);
        }

        //ADD MAHACT IMPERIA REFERENCE
        List<MessageEmbed> imperiaEmbeds = new ArrayList<>();
        if (player.hasAbility("imperia")) {
            for (Player otherPlayer : game.getPlayers().values()) {
                if (otherPlayer != player) {
                    if (player.getMahactCC().contains(otherPlayer.getColor())) {
                        Leader leader = otherPlayer.unsafeGetLeader(Constants.COMMANDER);
                        if (leader == null) {
                            continue;
                        }
                        imperiaEmbeds.add(leader.getLeaderEmbed());
                    }
                }
            }
        }
        if (!imperiaEmbeds.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), "**Commanders from " + FactionEmojis.Mahact + " Imperia:**", imperiaEmbeds);
        }
    }

    private static List<Button> getLeaderButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_LEADER_INFO);
        return buttons;
    }

    private static List<MessageEmbed> getPlayersLeaderEmbeds(Player player) {
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Leader leader : player.getLeaders()) {
            embeds.add(leader.getLeaderEmbed());
        }
        return embeds;
    }
}
