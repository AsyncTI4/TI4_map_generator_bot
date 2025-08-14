package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;

public class WormholeReconAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "wormhole_recon";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            for (Tile tile : ButtonHelper.getAllWormholeTiles(game)) {
                for (Player player : game.getRealPlayers()) {
                    if (!FoWHelper.playerHasShipsInSystem(player, tile)) continue;
                    CommandCounterHelper.addCC(event, player, tile);
                }
            }
        }
    }
}
