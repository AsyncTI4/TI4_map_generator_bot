package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.leader.UnlockLeaderService;

public class BreakthroughHelper {

    public static void resolveYinBreakthroughAbility(Game game, Player player) {

        String leaderID = getUnusedCommander(game);
        if (leaderID != null) {
            player.addLeader(leaderID);
            game.addFakeCommander(leaderID);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " utilized the Yin breakthrough to acquire a new commander, "
                            + Mapper.getLeader(leaderID).getName() + "!");
            UnlockLeaderService.unlockLeader(leaderID, game, player);
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " cannot gain a new commander, as all commanders are already in play.");
        }
    }

    public static String getUnusedCommander(Game game) {
        List<String> commanders = new ArrayList<>();
        List<FactionModel> allFactions = Mapper.getFactionsValues().stream()
                .filter(f -> game.isDiscordantStarsMode()
                        ? f.getSource().isDs()
                        : f.getSource().isOfficial())
                .toList();
        for (FactionModel faction : allFactions) {
            String commanderName = faction.getAlias() + "commander";
            if (commanderName.contains("keleres")) {
                commanderName = "kelerescommander";
            }
            if (game.getFactions().contains(faction.getAlias())
                    || (game.isMinorFactionsMode() && game.getTile(faction.getID()) != null)
                    || (Helper.getPlayerFromLeader(game, commanderName) != null)
                    || commanders.contains(commanderName)) {
                continue;
            }
            commanders.add(commanderName);
        }
        if (!commanders.isEmpty()) {
            Collections.shuffle(commanders);
            return commanders.getFirst();
        } else {
            return null;
        }
    }
}
