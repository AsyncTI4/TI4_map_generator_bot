package ti4.service.leader;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelperAbilities;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class RefreshLeaderService {

    public static void refreshLeader(Player player, Leader playerLeader, Game game) {
        int tgCount = playerLeader.getTgCount();
        playerLeader.setExhausted(false);
        if (tgCount > 0) {
            int tg = player.getTg();
            tg += tgCount;
            player.setTg(tg);
            String leaderName = playerLeader.getId();
            if ("nomadagentartuno".equals(leaderName)) {
                leaderName = "Artuno the Betrayer, a Nomad agent,";
            } else if ("nomadagentartuno".equals(leaderName)) {
                leaderName = "Clever Clever Artuno the Betrayer, a Nomad/Yssaril agent,";
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you gained " + tgCount + " trade good"
                            + (tgCount == 1 ? "" : "s") + " (" + (tg - tgCount) + "->" + tg + ") from " + leaderName
                            + " being readied.");
            ButtonHelperAbilities.pillageCheck(player, game);
            playerLeader.setTgCount(0);
        }
    }
}
