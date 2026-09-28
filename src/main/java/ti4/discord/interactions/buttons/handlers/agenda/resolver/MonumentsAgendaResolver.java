package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperStats;
import ti4.service.agenda.MonumentsAgendaService;

public record MonumentsAgendaResolver(String agendaId) implements AgendaResolver {

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!game.isMonumentsMode()) {
            return;
        }
        if ("cathedralofixth".equals(agendaId)) {
            game.addLaw(agendaNumericId, winner);
            MonumentsAgendaService.resolveCathedralOfIxthPlacement(
                    game, game.getPlayerThatControlsPlanet(winner), winner);
            return;
        }

        if ("ministerofculture".equals(agendaId)) {
            Player electedPlayer = game.getPlayerFromColorOrFaction(winner);
            if (electedPlayer != null) {
                ButtonHelperStats.replenishComms(event, game, electedPlayer, false);
            }
        }
    }
}
