package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;

public class ChecksAndBalancesFlagAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "checks";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) return;
        game.setStoredValue("agendaChecksNBalancesAgainst", "true");
    }
}
