package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RegulationsAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "regulations";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            for (Player playerB : game.getRealPlayers()) {
                if (playerB.getFleetCC() > 4) {
                    playerB.setFleetCC(4);
                    ButtonHelper.checkFleetInEveryTile(playerB, game);
                }
                if (playerB.hasAbility("imperia")) {
                    if (playerB.getFleetCC() + playerB.getMahactCC().size() > 4) {
                        int min = Math.max(0, 4 - playerB.getMahactCC().size());
                        playerB.setFleetCC(min);
                        ButtonHelper.checkFleetInEveryTile(playerB, game);
                    }
                }
            }

            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    game.getPing()
                            + ", all player that had more than 4 command tokens in their fleet pools have had the excess removed.");
        } else {
            for (Player playerB : game.getRealPlayers()) {
                playerB.setFleetCC(playerB.getFleetCC() + 1);
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    game.getPing() + ", all players have had 1 command token added to their respective fleet pools.");
        }
    }
}
