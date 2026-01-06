package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RegulationsAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "regulations";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player p : game.getRealPlayers()) {
            if (p.getFleetCC() > 4) {
                p.setFleetCC(4);
                ButtonHelper.checkFleetInEveryTile(p, game);
            }
            if (p.getEffectiveFleetCC() > 4) {
                String msg = p.getRepresentation()
                        + ", please lose command tokens from your fleet pool until you are at 4 total.";
                var buttons = ButtonHelper.getLoseFleetCCButtons(p);
                MessageHelper.sendMessageToChannelWithButtons(p.getCorrectChannel(), msg, buttons);
            }
        }

        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                game.getPing()
                        + ", all player that had more than 4 command tokens in their fleet pools have had the excess removed.");
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player playerB : game.getRealPlayers()) {
            playerB.setFleetCC(playerB.getFleetCC() + 1);
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                game.getPing() + ", all players have had 1 command token added to their respective fleet pools.");
    }
}
