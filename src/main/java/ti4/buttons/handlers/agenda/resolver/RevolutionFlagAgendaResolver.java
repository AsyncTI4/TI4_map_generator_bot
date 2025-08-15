package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;

public class RevolutionFlagAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "revolution";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {}

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        game.setStoredValue("agendaRevolution", "true");
    }
}
