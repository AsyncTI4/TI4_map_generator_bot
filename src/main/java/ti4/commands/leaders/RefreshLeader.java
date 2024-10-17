package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RefreshLeader extends LeaderAction {
    public RefreshLeader() {
        super(Constants.REFRESH_LEADER, "Ready leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game game, Player player) {
        Leader playerLeader = player.getLeader(leaderID).orElse(null);
        if (playerLeader != null) {
            if (playerLeader.isLocked()) {
                MessageHelper.sendMessageToEventChannel(event, "Leader is locked");
                return;
            }
            int tgCount = playerLeader.getTgCount();
            refreshLeader(player, playerLeader, game);
            StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" readied ")
                .append(Helper.getLeaderShortRepresentation(playerLeader));
            if (tgCount > 0) {
                message.append(" - ").append(tgCount).append(Emojis.getTGorNomadCoinEmoji(game)).append(" transferred from leader to player");

            }
            String msg = message.toString();
            MessageHelper.sendMessageToEventChannel(event, msg);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
        }
    }

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
