package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.service.objectives.RevealPublicObjectiveService;

public class IncentiveAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "incentive";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        var actionsChannel = game.getMainGameChannel();
        if ("for".equalsIgnoreCase(winner)) {
            RevealPublicObjectiveService.revealS1(game, event, actionsChannel);
        } else {
            RevealPublicObjectiveService.revealS2(game, event, actionsChannel);
        }
    }
}
