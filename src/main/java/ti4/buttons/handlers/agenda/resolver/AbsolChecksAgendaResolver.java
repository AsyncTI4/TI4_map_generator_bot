package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;

public class AbsolChecksAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "absol_checks";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            AgendaHelper.resolveAbsolAgainstChecksNBalances(game);
        }
    }
}
