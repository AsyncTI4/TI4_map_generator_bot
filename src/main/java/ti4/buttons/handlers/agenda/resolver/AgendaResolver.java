package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;

public interface AgendaResolver {
    String getAgID();

    void handle(Game game, ButtonInteractionEvent event, int aID, String winner);
}
