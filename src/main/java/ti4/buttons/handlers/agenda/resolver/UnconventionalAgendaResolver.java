package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class UnconventionalAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "unconventional";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        List<Player> winOrLose;
        if (!"for".equalsIgnoreCase(winner)) {
            winOrLose = AgendaHelper.getLosingVoters(winner, game);
            for (Player playerWL : winOrLose) {
                ActionCardHelper.discardRandomAC(event, game, playerWL, playerWL.getAcCount());
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Discarded the action cards of those who voted \"For\".");
        } else {
            winOrLose = AgendaHelper.getWinningVoters(winner, game);
            for (Player playerWL : winOrLose) {
                ActionCardHelper.drawActionCards(playerWL, 2);
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Drew 2 action cards for each of the players who voted \"For\".");
        }
    }
}
