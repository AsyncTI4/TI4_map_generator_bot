package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;

public class PlowsharesAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "plowshares";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player playerB : Helper.getSpeakerOrFullPriorityOrder(game)) {
            AgendaHelper.doSwords(playerB, event, game);
        }
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player playerB : game.getRealPlayers()) {
            ActionCardHelper.doRise(playerB, event, game);
        }
    }
}
