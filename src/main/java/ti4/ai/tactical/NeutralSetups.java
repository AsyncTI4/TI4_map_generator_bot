package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.eval.Journey;
import ti4.ai.eval.MoveBudget;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class NeutralSetups {

    public enum Pirates {
        CONTRACT(Map.of(UnitType.Destroyer, 1), Set.of("btv", "uf", "dyp")),
        FLEET(
                Map.of(UnitType.Carrier, 1, UnitType.Cruiser, 1, UnitType.Destroyer, 1, UnitType.Fighter, 2),
                Set.of(PrecisionRules.FIGHT_WITH_PRECISION));

        private final Map<UnitType, Integer> units;
        private final Set<String> secrets;

        Pirates(Map<UnitType, Integer> units, Set<String> secrets) {
            this.units = units;
            this.secrets = secrets;
        }
    }

    private static final String BRAVE_THE_VOID = "btv";
    private static final String UNVEIL_FLAGSHIP = "uf";
    private static final String DEMONSTRATE_YOUR_POWER = "dyp";
    private static final int DEMONSTRATION_SHIPS = 3;
    private static final double SETUP_SHARE = 0.6;
    private static final double ATTACK_TOKEN_COST = 0.75;
    private static final double FWP_MIN_CHANCE = 0.6;
    private static final StrikeRules RULES = new StrikeRules(false, 0.5, true, true);

    public static double value(Game game, Player seat, Tile tile, Pirates pirates) {
        List<String> secrets = ImaginedTurn.unscoredSecrets(seat).keySet().stream()
                .filter(pirates.secrets::contains)
                .toList();
        if (secrets.isEmpty() || !TacticalPlanner.hasTokensForAnAction(game, seat) || !attackable(game, seat, tile)) {
            return 0;
        }
        Strike strike = strikeAt(game, seat, tile, pirates);
        if (strike.moves().isEmpty()) return 0;
        StrikeEstimate estimate = StrikeEvaluator.estimate(game, seat, strike);
        double chance = secrets.stream()
                .mapToDouble(secret -> chanceOf(secret, tile, game, seat, strike, estimate))
                .max()
                .orElse(0);
        if (chance <= 0) return 0;
        double value = ObjectiveValue.VICTORY_POINT_VALUE * SETUP_SHARE * chance
                - ATTACK_TOKEN_COST
                - estimate.ourExpectedLoss();
        return Math.max(0, value);
    }

    private static boolean attackable(Game game, Player seat, Tile tile) {
        if (tile.hasPlayerCC(seat) || tile.isMecatol() || !ButtonHelper.canActivateTile(game, seat, tile)) {
            return false;
        }
        if (!MovementGraph.canEnter(game, seat, tile)) return false;
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .noneMatch(other -> tile.containsPlayersUnits(other) || ownsPlanetIn(other, tile));
    }

    private static boolean ownsPlanetIn(Player player, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .map(Planet::getName)
                .anyMatch(planet -> player.getPlanets().contains(planet));
    }

    private static Strike strikeAt(Game game, Player seat, Tile tile, Pirates pirates) {
        List<Tile> origins = new ArrayList<>();
        List<UnitMove> moves = new ArrayList<>();
        shipsReaching(game, seat, tile).forEach((position, ships) -> {
            List<UnitMove> departing = movingShips(position, ships);
            if (departing.isEmpty()) return;
            origins.add(game.getTileByPosition(position));
            moves.addAll(departing);
        });
        Strike strike = Strike.of(seat, tile, game.getNeutral(), origins, moves, RULES);
        return strike.withExtraDefenderShips(new EnumMap<>(pirates.units));
    }

    private static Map<String, Map<UnitType, Integer>> shipsReaching(Game game, Player seat, Tile target) {
        return MoveBudget.shipsWithin(
                game,
                seat,
                target,
                Journey.thisAction(),
                origin -> !origin.hasPlayerCC(seat),
                type -> shipCost(seat, type),
                Integer.MAX_VALUE);
    }

    private static double shipCost(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
    }

    private static List<UnitMove> movingShips(String origin, Map<UnitType, Integer> ships) {
        List<UnitMove> departing = new ArrayList<>();
        ships.forEach((type, count) -> {
            if (count > 0 && BoardView.MOVING_SHIPS.contains(type)) {
                departing.add(new UnitMove(origin, BoardView.SPACE, type, count));
            }
        });
        return departing;
    }

    private static double chanceOf(
            String secret, Tile tile, Game game, Player seat, Strike strike, StrikeEstimate estimate) {
        return switch (secret) {
            case BRAVE_THE_VOID -> tile.isAnomaly(game, seat) ? estimate.spaceWin() : 0;
            case UNVEIL_FLAGSHIP ->
                strike.ships().getOrDefault(UnitType.Flagship, 0) > 0
                                && estimate.survivors().getOrDefault(UnitType.Flagship, 0) > 0
                        ? estimate.spaceWin()
                        : 0;
            case DEMONSTRATE_YOUR_POWER ->
                nonFighters(estimate.survivors()) >= DEMONSTRATION_SHIPS ? estimate.spaceWin() : 0;
            case PrecisionRules.FIGHT_WITH_PRECISION -> clearsFighters(seat, strike);
            default -> 0;
        };
    }

    private static int nonFighters(Map<UnitType, Integer> ships) {
        return ships.entrySet().stream()
                .filter(ship -> ship.getKey() != UnitType.Fighter)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private static double clearsFighters(Player seat, Strike strike) {
        int fighters = strike.extraDefenderShips().getOrDefault(UnitType.Fighter, 0);
        double chance = PrecisionRules.chanceToClear(seat, strike.ships(), fighters);
        return chance >= FWP_MIN_CHANCE ? chance : 0;
    }
}
