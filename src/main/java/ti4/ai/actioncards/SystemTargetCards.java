package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.explore.ExploreValues;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;

@UtilityClass
class SystemTargetCards {

    static final String GHOST_SHIP = "ghost_ship";
    static final String EXPLORATION_PROBE = "probe";
    private static final String GHOST_SHIP_PREFIX = "ghostShipStep2_";
    private static final String PROBE_PREFIX = "probeStep2_";
    private static final double DESTROYER_VALUE = 1.0;
    private static final double POSITION_VALUE = 1.0;
    private static final double OBJECTIVE_SHARE = 0.5;
    private static final double MALLICE_ACCESS_VALUE = 1.0;
    private static final String NEXUS_PREFIX = "82";

    static AiCard ghostShip() {
        return AiActionCards.component(
                GHOST_SHIP,
                SystemTargetCards::ghostShipPlan,
                "resolveGhostShipStep1",
                GHOST_SHIP_PREFIX,
                SystemTargetCards::ghostShipStep);
    }

    static AiCard explorationProbe() {
        return AiActionCards.component(
                EXPLORATION_PROBE,
                SystemTargetCards::probePlan,
                "resolveProbeStep1",
                PROBE_PREFIX,
                SystemTargetCards::probeStep);
    }

    static Optional<Plan> ghostShipPlan(Game game, Player seat) {
        if (!ExploreValues.hasRoomForUnit(game, seat, UnitType.Destroyer)) return Optional.empty();
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        return game.getTileMap().values().stream()
                .filter(tile -> canHoldGhostShip(game, seat, tile))
                .map(tile -> Plan.aimedAt(
                        ghostShipValue(game, seat, tile, objectives),
                        tile.getPosition(),
                        "in " + tile.getRepresentation() + "."))
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    private static boolean canHoldGhostShip(Game game, Player seat, Tile tile) {
        if (!FoWHelper.doesTileHaveWHs(game, tile.getPosition()) || tile.isHomeSystem(game)) return false;
        boolean otherShips = game.getRealPlayersNNeutral().stream()
                .anyMatch(other -> other != seat && FoWHelper.playerHasActualShipsInSystem(other, tile));
        int fleet = BoardView.nonFighterShips(BoardView.space(tile), seat);
        return !otherShips && fleet + 1 <= FleetLimit.ships(seat);
    }

    private static double ghostShipValue(Game game, Player seat, Tile tile, ObjectiveValue objectives) {
        Footprint after = objectives.before().withBuilt(tile.getPosition(), Map.of(UnitType.Destroyer, 1));
        double fleet = nextToOwnFleet(game, seat, tile) ? POSITION_VALUE : 0;
        double objective = OBJECTIVE_SHARE * Math.max(0, objectives.gain(after));
        return DESTROYER_VALUE + fleet + objective + malliceAccess(game, seat, tile);
    }

    private static double malliceAccess(Game game, Player seat, Tile tile) {
        if (!FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition())) return 0;
        Optional<Tile> nexus = game.getTileMap().values().stream()
                .filter(candidate -> candidate.getTileID().startsWith(NEXUS_PREFIX))
                .findFirst();
        if (nexus.isEmpty() || BoardView.hasOwnShips(seat, nexus.get())) return 0;
        boolean alreadyConnected = game.getTileMap().values().stream()
                .filter(other -> BoardView.hasOwnShips(seat, other))
                .anyMatch(other -> FoWHelper.doesTileHaveAlphaOrBeta(game, other.getPosition()));
        return alreadyConnected ? 0 : MALLICE_ACCESS_VALUE;
    }

    private static boolean nextToOwnFleet(Game game, Player seat, Tile tile) {
        if (BoardView.hasOwnShips(seat, tile)) return true;
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacent -> adjacent != null && BoardView.hasOwnShips(seat, adjacent));
    }

    private static Optional<AiDecision> ghostShipStep(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        List<Offer> offers = CardPrompts.targetOffers(context, play, GHOST_SHIP_PREFIX);
        Optional<Offer> choice = CardPrompts.choose(offers, play.extra(), position -> {
            Tile tile = game.getTileByPosition(position);
            return tile == null ? Double.NEGATIVE_INFINITY : ghostShipValue(game, seat, tile, objectives);
        });
        choice.ifPresent(offer -> PlayMemory.moveTo(context, key, play, Stage.DONE));
        return choice.map(offer -> offer.match().press("place the Ghost Ship in " + offer.suffix()));
    }

    static Optional<Plan> probePlan(Game game, Player seat) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasFrontierToken(tile) && inOrNextToOwnShips(game, seat, tile))
                .map(tile -> Plan.aimedAt(
                        ExploreValues.FRONTIER_CARD,
                        tile.getPosition(),
                        "on the frontier token in " + tile.getRepresentation() + "."))
                .min(Comparator.comparing(Plan::target));
    }

    private static boolean hasFrontierToken(Tile tile) {
        return BoardView.space(tile).getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER));
    }

    private static boolean inOrNextToOwnShips(Game game, Player seat, Tile tile) {
        return nextToOwnFleet(game, seat, tile);
    }

    private static Optional<AiDecision> probeStep(AiTurnContext context, String key, CardPlay play) {
        List<Offer> offers = CardPrompts.targetOffers(context, play, PROBE_PREFIX);
        Optional<Offer> choice = CardPrompts.choose(offers, play.extra(), position -> 0);
        choice.ifPresent(offer -> PlayMemory.moveTo(context, key, play, Stage.DONE));
        return choice.map(offer -> offer.match().press("explore the frontier token in " + offer.suffix()));
    }
}
