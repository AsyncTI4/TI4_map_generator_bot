package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;

public interface AgendaResolver {
    String getAgendaId();

    void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner);
}
