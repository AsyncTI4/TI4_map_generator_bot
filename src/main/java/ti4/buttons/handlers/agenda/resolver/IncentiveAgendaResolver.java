package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.service.objectives.RevealPublicObjectiveService;

public class IncentiveAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "incentive";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        var actionsChannel = game.getMainGameChannel();
        RevealPublicObjectiveService.revealS1(game, event, actionsChannel);
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        var actionsChannel = game.getMainGameChannel();
        RevealPublicObjectiveService.revealS2(game, event, actionsChannel);
    }
}
