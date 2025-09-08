package ti4.buttons.handlers.agenda.resolver;

import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

public class TravelBanAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "travel_ban";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            Set<String> wormholesAndAdj = new HashSet<>();
            for (Tile tile : ButtonHelper.getAllWormholeTiles(game)) {
                wormholesAndAdj.add(tile.getPosition());
                wormholesAndAdj.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), null, false));
            }
            for (String pos : wormholesAndAdj) {
                Tile t = game.getTileByPosition(pos);
                for (Player p : game.getRealPlayersNNeutral()) {
                    UnitKey pds = Units.getUnitKey(UnitType.Pds, p.getColorID());
                    for (UnitHolder uh : t.getUnitHolders().values()) {
                        int num = uh.getUnitCount(pds);
                        if (num > 0) DestroyUnitService.destroyUnit(event, t, game, pds, num, uh, false);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Removed all PDS in or adjacent to wormholes.");
        }
    }
}
