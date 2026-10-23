package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

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
        List<String> secrets = seat.getSecretsUnscored().keySet().stream()
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
        for (Tile origin : game.getTileMap().values()) {
            if (origin == tile || origin.hasPlayerCC(seat)) continue;
            List<UnitMove> departing = shipsReaching(game, seat, origin, tile);
            if (departing.isEmpty()) continue;
            origins.add(origin);
            moves.addAll(departing);
        }
        Strike strike = Strike.of(seat, tile, game.getNeutral(), origins, moves, RULES);
        return strike.withExtraDefenderShips(new EnumMap<>(pirates.units));
    }

    private static List<UnitMove> shipsReaching(Game game, Player seat, Tile origin, Tile target) {
        List<UnitMove> departing = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int count = BoardView.count(BoardView.space(origin), seat, type);
            int move = BoardView.moveValue(seat, type);
            if (count == 0 || move <= 0) continue;
            if (MovementGraph.reach(game, seat, origin.getPosition(), move).containsKey(target.getPosition())) {
                departing.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, count));
            }
        }
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
