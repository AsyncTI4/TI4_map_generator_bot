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
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you gained " + tgCount + " TG" + (tgCount == 1 ? "" : "s")
                    + " (" + (tg - tgCount) + "->" + tg + ") from " + playerLeader.getId() + " being readied");
            ButtonHelperAbilities.pillageCheck(player, game);
            playerLeader.setTgCount(0);
        }
    }
}
