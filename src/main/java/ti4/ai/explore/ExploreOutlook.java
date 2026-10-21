package ti4.ai.explore;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.Threats;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;

public final class ExploreOutlook {

    private static final int ONE_MORE_RESOURCE = 1;
    private static final String SLING_RELAY = "sr";
    private static final Set<StrategyCard> SPENDING_CARDS =
            EnumSet.of(StrategyCard.LEADERSHIP, StrategyCard.WARFARE, StrategyCard.TECHNOLOGY);

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

    public boolean canSpendReadied(String planet) {
        if (helpsScoreASpendObjective(planet)) return true;
        if (seat.isPassed()) return false;
        return willSpendMore() || hasReadySlingRelay() || spendingStrategyCardLeft();
    }

    private boolean helpsScoreASpendObjective(String planet) {
        if (!Helper.canPlayerScorePOs(game, seat)) return false;
        Wallet now = Wallet.of(game, seat);
        Wallet readied = now.withPlanets(game, List.of(planet));
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> !ObjectivePolicy.hasScored(game, seat, id))
                .map(ObjectiveCatalog::spendCost)
                .flatMap(Optional::stream)
                .anyMatch(cost -> !now.canPay(cost) && readied.canPay(cost));
    }

    private boolean hasReadySlingRelay() {
        return seat.hasTech(SLING_RELAY) && !seat.getExhaustedTechs().contains(SLING_RELAY);
    }

    private boolean spendingStrategyCardLeft() {
        for (Player player : game.getRealPlayers()) {
            for (int card : player.getSCs()) {
                if (game.getPlayedSCs().contains(card)) continue;
                if (!SPENDING_CARDS.contains(StrategyCard.of(game, card))) continue;
                if (player == seat || seat.getStrategicCC() > 0) return true;
            }
        }
        return false;
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
