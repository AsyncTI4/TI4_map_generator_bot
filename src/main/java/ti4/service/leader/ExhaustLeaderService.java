package ti4.service.leader;

import lombok.experimental.UtilityClass;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class ExhaustLeaderService {

    public static void exhaustLeader(Game game, Player player, Leader leader) {
        exhaustLeader(game, player, leader, null);
    }

    public static void exhaustLeader(Game game, Player player, Leader leader, Integer tgCount) {
        leader.setExhausted(true);
        LeaderModel leaderModel = leader.getLeaderModel().orElse(null);
        String message = player.getRepresentation() + " exhausted: ";
        if (leaderModel != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(), message, leaderModel.getRepresentationEmbed());
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message + leader.getId());
        }

        if (tgCount != null) {
            StringBuilder sb = new StringBuilder();
            leader.setTgCount(tgCount);
            String leaderName = leaderModel.getName();
            if ("nomadagentartuno".equals(leaderModel.getID())) {
                leaderName = "Artuno the Betrayer, a Nomad agent";
            } else if ("nomadagentartuno".equals(leaderModel.getID())) {
                leaderName = "Clever Clever Artuno the Betrayer, a Nomad/Yssaril agent";
            }
            sb.append(tgCount)
                    .append(" trade good")
                    .append(tgCount == 1 ? "" : "s")
                    .append(" were placed on top of ")
                    .append(leaderName)
                    .append(".");
            if (leader.getTgCount() != tgCount) {
                sb.append(" *(")
                        .append(tgCount)
                        .append(MiscEmojis.getTGorNomadCoinEmoji(game))
                        .append(" total)*\n");
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        }

        TemporaryCombatModifierModel possibleCombatMod =
                CombatTempModHelper.getPossibleTempModifier(Constants.LEADER, leader.getId(), player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Combat modifier will be applied next time you push the combat roll button.");
        }
    }
}
