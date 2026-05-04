package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.helpers.AgendaHelper;

public class AbsolChecksAgendaResolver implements AgendaResolver {
    @Override
    public String agendaId() {
        return "absol_checks";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            AgendaHelper.resolveAbsolAgainstChecksNBalances(game);
        }
    }
}
