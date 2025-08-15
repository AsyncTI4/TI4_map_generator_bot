package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ArticlesOfWarAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "articles_war";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) return;
        List<Player> winOrLose = AgendaHelper.getLosingVoters(winner, game);
        for (Player playerWL : winOrLose) {
            playerWL.setTg(playerWL.getTg() + 3);
            ButtonHelperAbilities.pillageCheck(playerWL, game);
            ButtonHelperAgents.resolveArtunoCheck(playerWL, 3);
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "Each player that voted \"For\" has gained 3 trade goods.");
    }
}
