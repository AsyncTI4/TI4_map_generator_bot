package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SanctionsAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "sanctions";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            for (Player playerWL : game.getRealPlayers()) {
                ActionCardHelper.discardRandomAC(event, game, playerWL, 1);
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Discarded 1 random action card from each player's hand.");
        } else {
            for (Player playerWL : game.getRealPlayers()) {
                ButtonHelper.checkACLimit(game, playerWL);
            }
        }
    }
}
