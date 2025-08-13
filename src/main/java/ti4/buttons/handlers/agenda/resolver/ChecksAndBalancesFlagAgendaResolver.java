package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;

public class ChecksAndBalancesFlagAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "checks";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        game.setStoredValue("agendaChecksNBalancesAgainst", "true");
    }
}
