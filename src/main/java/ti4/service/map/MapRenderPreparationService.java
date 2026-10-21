package ti4.service.map;

import java.util.Collection;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.service.fow.FogMemoryService;
import ti4.service.fow.FogViewer;
import ti4.service.relic.TriadService;
import ti4.service.transaction.DebtPoolIconService;

public final class MapRenderPreparationService {

    private MapRenderPreparationService() {}

    public static void prepare(Game game, FogViewer viewer) {
        FogMemoryService.refresh(game, viewer);
        TriadService.refreshDerivedPlanetStats(game);
        DebtPoolIconService.assignMissingIcons(game);
    }

    public static void prepareBoard(Game game, Collection<Tile> drawnTiles) {
        if (drawnTiles.stream().anyMatch(MapRenderPreparationService::hasUnits)) {
            game.getNeutral();
        }
    }

    private static boolean hasUnits(Tile tile) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> !holder.getUnits().isEmpty());
    }
}
