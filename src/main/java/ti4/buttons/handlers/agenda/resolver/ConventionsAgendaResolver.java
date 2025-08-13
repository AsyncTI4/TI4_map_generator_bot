package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ConventionsAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "conventions";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            List<Player> winOrLose = AgendaHelper.getWinningVoters(winner, game);
            for (Player playerWL : winOrLose) {
                ActionCardHelper.discardRandomAC(event, game, playerWL, playerWL.getAc());
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Discarded the action cards of those who voted \"Against\".");
        }
    }
}
