package ti4.ai.explore;

import java.util.HashMap;
import java.util.Map;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.Threats;
import ti4.ai.scoring.ObjectiveValue;
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
    private ExpansionOutlook expansion;
    private Boolean spendsMore;
    private Boolean fundsFreelancers;
    private ObjectiveValue objectives;

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
        if (expansion == null) expansion = new ExpansionOutlook(game, seat);
        return expansion.need(holding);
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

    public double attachmentObjectiveGain(Planet planet) {
        return objectives().attachmentGain(planet.getName());
    }

    public double specialtyObjectiveGain(Planet planet) {
        return objectives().specialtyGain(planet.getName());
    }

    private ObjectiveValue objectives() {
        if (objectives == null) objectives = new ObjectiveValue(game, seat);
        return objectives;
    }

    private boolean computeSpendsMore() {
        if (seat.isPassed()) return false;
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                .distinct()
                .filter(this::willProduceAt)
                .anyMatch(dock -> ProductionPlanner.plan(game, seat, dock, ONE_MORE_RESOURCE)
                                .totalCost()
                        > ProductionPlanner.plan(game, seat, dock).totalCost());
    }

    private boolean willProduceAt(Tile dock) {
        if (dock.getPosition().equals(game.getActiveSystem()) && seat == game.getActivePlayer()) return true;
        return seat.getTacticalCC() > 0 && !dock.hasPlayerCC(seat);
    }
}
