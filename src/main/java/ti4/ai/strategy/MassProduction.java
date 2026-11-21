package ti4.ai.strategy;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class MassProduction {

    static final int NONE = Integer.MAX_VALUE;
    private static final String PRODUCE_EN_MASSE = "pem";
    private static final int TARGET = 8;
    private static final int DRIVING_DOCKS = 2;
    private static final int DOCK_UPGRADE_PRODUCTION = 2;
    private static final int BASE_DOCK_PRODUCTION = 2;
    private static final int UPGRADED_DOCK_PRODUCTION = 4;

    static boolean pursued(Player seat) {
        return seat.getSecretsUnscored().containsKey(PRODUCE_EN_MASSE);
    }

    static boolean drives(Game game, Player seat) {
        int needed = docksNeeded(game, seat);
        return needed >= 1 && needed <= DRIVING_DOCKS;
    }

    static boolean oneDockFromScoring(Game game, Player seat) {
        return docksNeeded(game, seat) == 1;
    }

    static int docksNeeded(Game game, Player seat) {
        if (!pursued(seat)) return NONE;
        int remaining = remainingDocks(game, seat);
        int needed = NONE;
        for (Tile tile : game.getTileMap().values()) {
            needed = Math.min(needed, docksNeededIn(game, seat, tile, remaining));
        }
        return needed;
    }

    static boolean canHostDock(Game game, Player seat, Planet planet) {
        return seat.getPlanets().contains(planet.getName())
                && !planet.isSpaceStation(game)
                && BoardView.count(planet, seat, UnitType.Spacedock) == 0;
    }

    static int productionWithDockOn(Game game, Player seat, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null || tile.isScar(game)) return 0;
        return Helper.getProductionValue(seat, game, tile, false) + dockProduction(seat, planet);
    }

    static int bestWithDockUpgrade(Game game, Player seat) {
        int best = ButtonHelper.checkHighestProductionSystem(seat, game);
        for (Tile tile : game.getTileMap().values()) {
            int docks = BoardView.countInTile(tile, seat, UnitType.Spacedock);
            if (docks == 0) continue;
            best = Math.max(best, Helper.getProductionValue(seat, game, tile, false) + DOCK_UPGRADE_PRODUCTION * docks);
        }
        return best;
    }

    private static int docksNeededIn(Game game, Player seat, Tile tile, int remaining) {
        if (tile.isScar(game)) return NONE;
        List<Integer> gains = tile.getPlanetUnitHolders().stream()
                .filter(planet -> canHostDock(game, seat, planet))
                .map(planet -> dockProduction(seat, planet))
                .sorted(Comparator.reverseOrder())
                .toList();
        int production = Helper.getProductionValue(seat, game, tile, false);
        if (production >= TARGET) return 0;
        for (int docks = 1; docks <= Math.min(gains.size(), remaining); docks++) {
            production += gains.get(docks - 1);
            if (production >= TARGET) return docks;
        }
        return NONE;
    }

    private static int dockProduction(Player seat, Planet planet) {
        UnitModel dock = seat.getUnitByBaseType("spacedock");
        int base = dock == null ? BASE_DOCK_PRODUCTION : dock.getProductionValue();
        boolean addsResources = base == BASE_DOCK_PRODUCTION || base == UPGRADED_DOCK_PRODUCTION;
        return addsResources ? base + planet.getResources() : base;
    }

    private static int remainingDocks(Game game, Player seat) {
        int cap = seat.getUnitCap("sd");
        return cap <= 0 ? NONE : cap - ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, "sd");
    }
}
