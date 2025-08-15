package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;

public interface ForAgainstAgendaResolver extends AgendaResolver {
    void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId);

    void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId);

    @Override
    default void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            handleFor(game, event, agendaNumericId);
        } else {
            handleAgainst(game, event, agendaNumericId);
        }
    }
}
