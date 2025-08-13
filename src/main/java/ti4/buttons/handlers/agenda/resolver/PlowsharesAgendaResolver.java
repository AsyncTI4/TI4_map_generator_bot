package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;
import ti4.map.Player;

public class PlowsharesAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "plowshares";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            for (Player playerB : game.getRealPlayers()) {
                AgendaHelper.doSwords(playerB, event, game);
            }
        } else {
            for (Player playerB : game.getRealPlayers()) {
                ActionCardHelper.doRise(playerB, event, game);
            }
        }
    }
}
