package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;

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
