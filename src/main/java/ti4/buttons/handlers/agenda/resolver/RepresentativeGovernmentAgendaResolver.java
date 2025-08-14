package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RepresentativeGovernmentAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "rep_govt";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            List<Player> winOrLose = AgendaHelper.getWinningVoters(winner, game);
            for (Player playerWL : winOrLose) {
                game.setStoredValue("agendaRepGov", game.getStoredValue("agendaRepGov") + playerWL.getFaction());
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "Will exhaust cultural planets of all players who voted \"Against\" at start of next Strategy Phase.");
        }
    }
}
