package ti4.ai.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class RiftRisk {

    public static final double SURVIVAL = 0.7;

    public static double worth(int rolls) {
        return rolls == 0 ? 1 : 2 * SURVIVAL - 1;
    }

    public static double allArrive(int rollingShips) {
        return Math.pow(SURVIVAL, rollingShips);
    }

    public static double expectedLoss(
            Game game, Player seat, Tile target, List<Departure> moves, ToDoubleFunction<UnitType> cost) {
        return expectedLoss(seat, target, moves, MoveBudget.riftExits(game, seat, target, moves), cost);
    }

    public static double expectedLoss(
            Player seat, Tile target, List<Departure> moves, List<Departure> exits, ToDoubleFunction<UnitType> cost) {
        if (exits.isEmpty()) return 0;
        double lost = 0;
        Map<String, Map<UnitType, Integer>> rolling = new LinkedHashMap<>();
        for (Departure exit : exits) {
            lost += exit.count() * (1 - SURVIVAL) * cost.applyAsDouble(exit.type());
            rolling.computeIfAbsent(exit.origin(), ignored -> new EnumMap<>(UnitType.class))
                    .merge(exit.type(), exit.count(), Integer::sum);
        }
        for (Map.Entry<String, Map<UnitType, Integer>> origin : rolling.entrySet()) {
            lost += (1 - SURVIVAL) * cargoAtRisk(seat, target, origin.getKey(), origin.getValue(), moves, cost);
        }
        return lost;
    }

    public static int rollingShips(Game game, Player seat, Tile target, List<Departure> moves) {
        return count(MoveBudget.riftExits(game, seat, target, moves));
    }

    public static int count(List<Departure> exits) {
        return exits.stream().mapToInt(Departure::count).sum();
    }

    private static double cargoAtRisk(
            Player seat,
            Tile target,
            String origin,
            Map<UnitType, Integer> rolling,
            List<Departure> moves,
            ToDoubleFunction<UnitType> cost) {
        if (origin.equals(target.getPosition())) return 0;
        int calmCapacity = 0;
        List<Double> cargo = new ArrayList<>();
        List<Departure> fromOrigin = moves.stream()
                .filter(move -> move.origin().equals(origin) && move.count() > 0)
                .toList();
        int fighters = 0;
        for (Departure move : fromOrigin) {
            if (BoardView.MOVING_SHIPS.contains(move.type())) {
                int calm = Math.max(0, move.count() - rolling.getOrDefault(move.type(), 0));
                calmCapacity += calm * BoardView.capacity(seat, move.type());
            } else if (move.type() == UnitType.Fighter) {
                fighters += move.count();
            } else if (BoardView.GROUND_FORCES.contains(move.type())) {
                for (int each = 0; each < move.count(); each++) cargo.add(cost.applyAsDouble(move.type()));
            }
        }
        int carriedFighters =
                FighterWings.flies(seat) ? MoveBudget.wing(seat, fromOrigin).carried() : fighters;
        for (int each = 0; each < carriedFighters; each++) cargo.add(cost.applyAsDouble(UnitType.Fighter));
        cargo.sort(Comparator.reverseOrder());
        double atRisk = 0;
        for (int index = calmCapacity; index < cargo.size(); index++) atRisk += cargo.get(index);
        return atRisk;
    }
}
