package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class PrecisionRules {

    public static final String FIGHT_WITH_PRECISION = "fwp";
    static final int FORWARD_SHIPS = 3;

    public static boolean held(Player seat) {
        return seat.getSecretsUnscored().containsKey(FIGHT_WITH_PRECISION);
    }

    public static double chanceToClear(Player seat, Map<UnitType, Integer> ships, int fighters) {
        if (fighters <= 0) return 0;
        return DiceOdds.atLeast(dieChances(seat, ships), fighters);
    }

    public static boolean hasBarrage(Player seat, Map<UnitType, Integer> ships) {
        return !barrageShips(seat, ships).isEmpty();
    }

    static Map<UnitType, Integer> barrageShips(Player seat, Map<UnitType, Integer> ships) {
        Map<UnitType, Integer> barrage = new EnumMap<>(UnitType.class);
        ships.forEach((type, count) -> {
            UnitModel model = seat.getUnitByType(type);
            if (count > 0 && model != null && model.getAfbDieCount(seat) > 0) barrage.put(type, count);
        });
        return barrage;
    }

    static int enemyFighters(Game game, Player seat, Tile tile) {
        return game.getRealPlayersNNeutral().stream()
                .filter(other -> other != seat)
                .mapToInt(other -> BoardView.count(BoardView.space(tile), other, UnitType.Fighter))
                .max()
                .orElse(0);
    }

    private static int moveOf(Player seat, Map<UnitType, Integer> barrage) {
        return barrage.keySet().stream()
                .mapToInt(type -> BoardView.moveValue(seat, type))
                .min()
                .orElse(0);
    }

    static double bestChance(
            Game game,
            Player seat,
            Tile from,
            Map<UnitType, Integer> ships,
            BiFunction<Tile, Integer, Map<String, Integer>> reach) {
        Map<UnitType, Integer> barrage = barrageShips(seat, ships);
        if (barrage.isEmpty()) return 0;
        double best = 0;
        for (String position : reach.apply(from, moveOf(seat, barrage)).keySet()) {
            Tile target = game.getTileByPosition(position);
            if (target == null || target == from) continue;
            best = Math.max(best, chanceToClear(seat, barrage, enemyFighters(game, seat, target)));
        }
        return best;
    }

    static boolean facesFighters(Game game, Player seat, Tile dock) {
        int move = BoardView.moveValue(seat, UnitType.Destroyer);
        return MovementGraph.reach(game, seat, dock.getPosition(), move).keySet().stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && tile != dock && enemyFighters(game, seat, tile) > 0);
    }

    private static List<Double> dieChances(Player seat, Map<UnitType, Integer> ships) {
        List<Double> chances = new ArrayList<>();
        ships.forEach((type, count) -> {
            UnitModel model = seat.getUnitByType(type);
            if (model == null || count <= 0) return;
            double chance = BattleMath.hitChance(model.getAfbHitsOn(seat));
            for (int die = 0; die < model.getAfbDieCount(seat) * count; die++) chances.add(chance);
        });
        return chances;
    }
}
