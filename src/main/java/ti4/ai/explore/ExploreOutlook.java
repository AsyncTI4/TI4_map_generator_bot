package ti4.ai.explore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.Threats;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

public final class ExploreOutlook {

    private static final int ONE_MORE_RESOURCE = 1;

    private final Game game;
    private final Player seat;
    private final Map<String, Boolean> enemyReach = new HashMap<>();
    private List<Planet> claimablePlanets;
    private Boolean spendsMore;
    private Boolean fundsFreelancers;

    public ExploreOutlook(Game game, Player seat) {
        this.game = game;
        this.seat = seat;
    }

    public Game game() {
        return game;
    }

    public Player seat() {
        return seat;
    }

    public boolean canFundFreelancers() {
        if (fundsFreelancers == null) {
            fundsFreelancers = FreelancersPayment.cheapest(this, ExploreValues.FREELANCERS_TYPICAL_COST)
                    .isPresent();
        }
        return fundsFreelancers;
    }

    public double expansionNeed(Planet holding) {
        List<Planet> claimable = claimablePlanets();
        if (claimable.size() <= groundForcesElsewhere(holding)) return 0;
        double best =
                claimable.stream().mapToDouble(BoardView::planetValue).max().orElse(0);
        return ExploreValues.EXPANSION_SHARE * best;
    }

    private List<Planet> claimablePlanets() {
        if (claimablePlanets == null) claimablePlanets = computeClaimablePlanets();
        return claimablePlanets;
    }

    private int groundForcesElsewhere(Planet holding) {
        int forces = 0;
        for (Tile tile : game.getTileMap().values()) {
            forces += BoardView.groundForces(BoardView.space(tile), seat);
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!planet.getName().equals(holding.getName())) forces += BoardView.groundForces(planet, seat);
            }
        }
        return forces;
    }

    public boolean enemyCanReach(Tile tile) {
        return enemyReach.computeIfAbsent(tile.getPosition(), position -> Threats.anyEnemyCanReach(game, seat, tile));
    }

    public boolean willSpendMore() {
        if (spendsMore == null) spendsMore = computeSpendsMore();
        return spendsMore;
    }

    public double planetStake(Planet planet) {
        return PlanetStake.of(game, seat, planet);
    }

    private List<Planet> computeClaimablePlanets() {
        Set<String> reachable = new HashSet<>();
        for (Tile origin : game.getTileMap().values()) {
            int move = fastestShipMove(origin);
            if (move > 0)
                reachable.addAll(MovementGraph.reach(game, seat, origin.getPosition(), move)
                        .keySet());
        }
        return game.getTileMap().values().stream()
                .filter(tile -> reachable.contains(tile.getPosition()))
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(this::isClaimable)
                .toList();
    }

    private int fastestShipMove(Tile origin) {
        return BoardView.ships(BoardView.space(origin), seat).keySet().stream()
                .filter(type -> type != UnitType.Fighter)
                .mapToInt(type -> BoardView.moveValueWithGravityDrive(seat, type))
                .max()
                .orElse(0);
    }

    private boolean isClaimable(Planet planet) {
        boolean worthHaving = !planet.getPlanetTypes().isEmpty() || planet.getResources() > 0;
        return worthHaving
                && BoardView.controller(game, planet.getName()) == null
                && !BoardView.hasCustodians(planet)
                && !BoardView.enemyGroundForcesOn(game, seat, planet);
    }

    private boolean computeSpendsMore() {
        if (seat.isPassed()) return false;
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                .distinct()
                .anyMatch(dock -> ProductionPlanner.plan(game, seat, dock, ONE_MORE_RESOURCE)
                                .totalCost()
                        > ProductionPlanner.plan(game, seat, dock).totalCost());
    }
}
