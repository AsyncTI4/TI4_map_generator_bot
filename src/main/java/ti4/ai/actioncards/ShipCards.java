package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class ShipCards {

    static final String SCUTTLE = "scuttle";
    private static final String TILE_PREFIX = "scuttleIn_";
    private static final String UNIT_PREFIX = "scuttleOn_";
    private static final String FINISH_PREFIX = "endScuttle_";
    private static final String SEPARATOR = "_";
    private static final String DAMAGED = "damaged";
    private static final String NONE_YET = "0";
    private static final double SHIP_KEPT_SHARE = 0.8;
    private static final Set<UnitType> SCUTTLED = Set.of(
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    static AiCard scuttle() {
        return AiActionCards.component(
                SCUTTLE,
                ShipCards::scuttlePlan,
                "startToScuttleAUnit_0",
                List.of(TILE_PREFIX, UNIT_PREFIX, FINISH_PREFIX),
                CardChains.chain(
                        CardChains.next(
                                TILE_PREFIX,
                                Source.OWNED,
                                (context, suffix) -> tileValue(context.game(), context.seat(), first(suffix)),
                                "scuttle a ship in"),
                        CardChains.next(
                                UNIT_PREFIX,
                                Source.OWNED,
                                (context, suffix) -> unitValue(context.game(), context.seat(), suffix),
                                "scuttle"),
                        CardChains.last(FINISH_PREFIX, Source.MENTIONED, (context, suffix) -> 0, "finish scuttling:")));
    }

    static Optional<Plan> scuttlePlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return game.getTileMap().values().stream()
                .map(tile -> best(game, seat, tile))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(Scuttle::value).thenComparing(Scuttle::position))
                .map(scuttle -> Plan.aimedAt(
                        scuttle.value(),
                        CardChains.target(
                                scuttle.position() + SEPARATOR + NONE_YET,
                                scuttle.position() + SEPARATOR + scuttle.unit() + SEPARATOR + NONE_YET),
                        "on a ship in " + scuttle.position() + "."));
    }

    private record Scuttle(String position, String unit, double value) {}

    private static Optional<Scuttle> best(Game game, Player seat, Tile tile) {
        if (!mayScuttleIn(game, seat, tile)) return Optional.empty();
        return SCUTTLED.stream()
                .filter(type -> BoardView.count(BoardView.space(tile), seat, type) > 0)
                .map(type -> new Scuttle(tile.getPosition(), unitName(seat, type), valueOf(game, seat, tile, type)))
                .max(Comparator.comparingDouble(Scuttle::value).thenComparing(Scuttle::unit));
    }

    private static boolean mayScuttleIn(Game game, Player seat, Tile tile) {
        if (!BoardView.hasOwnShips(seat, tile) || BoardView.hasEnemyUnits(game, seat, tile)) return false;
        if (tile.getPosition().equals(game.getActiveSystem())) return false;
        boolean defendsAPlanet = BoardView.planets(tile).stream()
                .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
        if (defendsAPlanet) return false;
        if (BoardView.nonFighterShips(BoardView.space(tile), seat) > 1) return true;
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        return objectives.gain(objectives.before().withoutShipsIn(tile.getPosition())) >= 0;
    }

    private static double valueOf(Game game, Player seat, Tile tile, UnitType type) {
        double cost = cost(seat, type);
        double unlock = ScoringStakes.scoreValue(game, seat, SpendUnlock.pointDelta(game, seat, (int) cost));
        return cost * (1 - SHIP_KEPT_SHARE) + unlock;
    }

    private static double cost(Player seat, UnitType type) {
        UnitModel model = BoardView.model(seat, type).orElse(null);
        return model == null ? 0 : model.getCost();
    }

    private static String unitName(Player seat, UnitType type) {
        return new UnitKey(type, seat.getColor()).unitName();
    }

    private static String first(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(0, suffix.indexOf(SEPARATOR)) : suffix;
    }

    private static double tileValue(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !mayScuttleIn(game, seat, tile)) return Double.NEGATIVE_INFINITY;
        return best(game, seat, tile).map(Scuttle::value).orElse(Double.NEGATIVE_INFINITY);
    }

    private static double unitValue(Game game, Player seat, String suffix) {
        String[] fields = suffix.split(SEPARATOR);
        if (fields.length < 3) return Double.NEGATIVE_INFINITY;
        Tile tile = game.getTileByPosition(fields[0]);
        String name = fields[1].replace(DAMAGED, "");
        if (tile == null || !mayScuttleIn(game, seat, tile)) return Double.NEGATIVE_INFINITY;
        return SCUTTLED.stream()
                .filter(type -> unitName(seat, type).equals(name))
                .mapToDouble(type -> valueOf(game, seat, tile, type))
                .findFirst()
                .orElse(Double.NEGATIVE_INFINITY);
    }
}
