package ti4.ai.trade;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FactionAggression;
import ti4.ai.eval.GameHorizon;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.ThronePoint;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
class ThroneValue {

    record Weights(
            double baseLossPerRound,
            double neighbourLossPerRound,
            double sharedSystemLossPerRound,
            double maxLossPerRound,
            double restrictionNeighbourPerRound,
            double restrictionPerSharedSystemPerRound,
            double restrictionMecatolPerRound,
            double maxRestriction,
            double retainShare,
            int retainRounds,
            double maxRetainShare,
            double peaceNeighbour,
            double peacePerSharedSystem,
            double minStrengthRatio,
            double maxStrengthRatio,
            double maxPeace) {}

    private static final double EARLY_COST = 6.0;
    private static final double FIT_WEIGHT = 1.5;
    private static final double TEMPO_SHARE_PER_POINT = 0.5;

    static final Weights DEFAULT =
            new Weights(0.02, 0.03, 0.02, 0.2, 0.4, 0.3, 0.5, 4.0, 0.5, 4, 0.75, 1.0, 0.3, 0.5, 2.0, 2.5);

    static double received(Game game, Player seat, Player owner) {
        return received(game, seat, owner, DEFAULT);
    }

    static double received(Game game, Player seat, Player owner, Weights weights) {
        if (owner == null) return NotesForTrade.OTHER_NOTE;
        int shared = sharedSystems(game, seat, owner);
        boolean neighbours = TradeLegality.neighbours(game, seat, owner);
        double points = ThronePoint.gained(game, seat, owner) * keepChance(game, neighbours, shared, weights);
        return points
                - restriction(game, seat, owner, neighbours, shared, weights)
                - earlyCost(game, seat, owner)
                + fit(game, seat, owner);
    }

    static double givenCost(Game game, Player seat, Player receiver) {
        return givenCost(game, seat, receiver, DEFAULT);
    }

    static double givenCost(Game game, Player seat, Player receiver, Weights weights) {
        return Math.max(
                        0,
                        retained(game, seat, weights)
                                - peace(game, seat, receiver, weights)
                                - fit(game, seat, receiver))
                + earlyCost(game, seat, receiver)
                + tempoPremium(game, seat, receiver);
    }

    static double tempoPremium(Game game, Player seat, Player receiver) {
        double lead = ScoringStakes.tempoLead(game, seat, receiver);
        if (lead <= 0) return 0;
        return TEMPO_SHARE_PER_POINT * lead * ScoringStakes.vpWorth(game, receiver);
    }

    static double earlyCost(Game game, Player seat, Player partner) {
        return ThroneTiming.open(game, seat, partner) ? 0 : EARLY_COST;
    }

    static double fit(Game game, Player seat, Player partner) {
        if (FactionAggression.of(seat) == FactionAggression.AGGRESSIVE) {
            return FIT_WEIGHT * fleetCost(game, partner) / Math.max(1.0, strongestFleet(game));
        }
        return TradeLegality.neighbours(game, seat, partner) ? FIT_WEIGHT : 0;
    }

    private static double strongestFleet(Game game) {
        return game.getRealPlayers().stream()
                .mapToDouble(player -> fleetCost(game, player))
                .max()
                .orElse(0);
    }

    static double keepChance(Game game, boolean neighbours, int sharedSystems, Weights weights) {
        double perRound = weights.baseLossPerRound()
                + (neighbours ? weights.neighbourLossPerRound() : 0)
                + sharedSystems * weights.sharedSystemLossPerRound();
        return Math.pow(1 - Math.min(weights.maxLossPerRound(), perRound), GameHorizon.roundsLeft(game));
    }

    static double retained(Game game, Player seat, Weights weights) {
        double horizon = (double) GameHorizon.roundsLeft(game) / weights.retainRounds();
        double share = Math.min(weights.maxRetainShare(), weights.retainShare() * horizon);
        return share * Desperation.vpWorth(game, seat);
    }

    static double peace(Game game, Player seat, Player receiver, Weights weights) {
        int shared = sharedSystems(game, receiver, seat);
        boolean neighbours = TradeLegality.neighbours(game, seat, receiver);
        if (!neighbours && shared == 0) return 0;
        double border = (neighbours ? weights.peaceNeighbour() : 0) + shared * weights.peacePerSharedSystem();
        double pressure = FactionAggression.of(receiver).factor() * strengthRatio(game, receiver, seat, weights);
        return Math.min(weights.maxPeace(), border * pressure);
    }

    private static double restriction(
            Game game, Player seat, Player owner, boolean neighbours, int shared, Weights weights) {
        double perRound = (neighbours ? weights.restrictionNeighbourPerRound() : 0)
                + shared * weights.restrictionPerSharedSystemPerRound()
                + (holdsMecatolAgainst(game, owner, seat) ? weights.restrictionMecatolPerRound() : 0);
        return Math.min(weights.maxRestriction(), perRound * GameHorizon.roundsLeft(game));
    }

    private static boolean holdsMecatolAgainst(Game game, Player owner, Player seat) {
        Tile mecatol = game.getMecatolTile();
        return mecatol != null && FoWHelper.playerHasUnitsInSystem(owner, mecatol) && !seat.controlsMecatol(false);
    }

    static int sharedSystems(Game game, Player seat, Player owner) {
        Set<String> planets = Set.copyOf(seat.getPlanets());
        int shared = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasUnitsInSystem(owner, tile)) continue;
            if (FoWHelper.playerHasUnitsInSystem(seat, tile) || hasPlanetOf(tile, planets)) shared++;
        }
        return shared;
    }

    private static boolean hasPlanetOf(Tile tile, Set<String> planets) {
        Set<String> names =
                tile.getPlanetUnitHolders().stream().map(Planet::getName).collect(Collectors.toSet());
        return names.stream().anyMatch(planets::contains);
    }

    private static double strengthRatio(Game game, Player first, Player second, Weights weights) {
        double ratio = fleetCost(game, first) / Math.max(1.0, fleetCost(game, second));
        return Math.clamp(ratio, weights.minStrengthRatio(), weights.maxStrengthRatio());
    }

    static double fleetCost(Game game, Player player) {
        return game.getTileMap().values().stream()
                .mapToDouble(tile -> BoardView.shipCost(BoardView.space(tile), player))
                .sum();
    }
}
