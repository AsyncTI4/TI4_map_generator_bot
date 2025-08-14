package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;

public interface AgendaResolver {
    String getAgendaId();

    void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner);
}
