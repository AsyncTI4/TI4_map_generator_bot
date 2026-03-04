package ti4.helpers;

import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.UnlockLeaderService;

public class BreakthroughHelper {

    public static void resolveYinBreakthroughAbility(Game game, Player player) {
        String leaderID = UnusedCommanderHelper.getUnusedCommander(game);
        if (leaderID == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " cannot gain a new commander, as all commanders are already in play.");
            return;
        }
        player.addLeader(leaderID);
        game.addFakeCommander(leaderID);
        UnlockLeaderService.unlockLeader(
                leaderID,
                game,
                player,
                player.getRepresentation() + " has used _Yin Ascendant_ to acquire a new commander, "
                        + Mapper.getLeader(leaderID).getName() + "!");
    }
}
