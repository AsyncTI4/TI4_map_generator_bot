package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class CannonExposure {

    static final double MIN_DELIVERY = 0.5;
    private static final double DAMAGE_SHARE = 0.25;
    private static final String ANTIMASS_DEFLECTORS = "amd";
    private static final String ABSOL_ANTIMASS_DEFLECTORS = "absol_amd";

    record Outcome(double expectedLoss, double delivery) {
        static final Outcome UNTOUCHED = new Outcome(0, 1);

        boolean viable() {
            return delivery >= MIN_DELIVERY;
        }
    }

    private record Ship(double cost, int capacity, boolean sustains) {}

    private record State(int capacity, int ships, double lost) {}

    static List<Double> dieChances(Game game, Player seat, Tile tile) {
        List<Double> chances = new ArrayList<>();
        if (tile.isScar(game)) return chances;
        int penalty = seat.hasTech(ANTIMASS_DEFLECTORS) || seat.hasTech(ABSOL_ANTIMASS_DEFLECTORS) ? 1 : 0;
        for (Player shooter : game.getRealPlayers()) {
            if (shooter == seat) continue;
            for (BattleMath.CannonDice stack : BattleMath.cannonDice(game, tile, shooter, List.of())) {
                double chance = BattleMath.hitChance(stack.hitsOn() + penalty);
                for (int die = 0; die < stack.dice(); die++) chances.add(chance);
            }
        }
        return chances;
    }

    static double expectedHits(Game game, Player seat, Tile tile) {
        return dieChances(game, seat, tile).stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    static Outcome of(Player seat, Tile tile, List<UnitMove> arriving, int cargo, List<Double> chances) {
        if (chances.isEmpty()) return Outcome.UNTOUCHED;
        List<State> states = states(shipsAt(seat, tile, arriving));
        int killAll = states.size() - 1;
        if (killAll <= 0) return Outcome.UNTOUCHED;
        double expectedLoss = 0;
        double delivery = 0;
        double atLeast = 1;
        for (int hits = 0; hits <= killAll; hits++) {
            double next = hits == killAll ? 0 : DiceOdds.atLeast(chances, hits + 1);
            delivery += (atLeast - next) * delivered(states.get(hits), cargo);
            expectedLoss += atLeast * (hits == 0 ? 0 : states.get(hits).lost());
            atLeast = next;
        }
        return new Outcome(expectedLoss, delivery);
    }

    private static double delivered(State state, int cargo) {
        if (cargo <= 0) return state.ships() > 0 ? 1 : 0;
        return Math.min(cargo, state.capacity()) / (double) cargo;
    }

    private static List<State> states(List<Ship> ships) {
        List<Ship> alive = new ArrayList<>(ships);
        alive.sort(Comparator.comparingDouble(Ship::cost));
        List<State> states = new ArrayList<>();
        int capacity = alive.stream().mapToInt(Ship::capacity).sum();
        states.add(new State(capacity, alive.size(), 0));
        while (!alive.isEmpty()) {
            int sustaining = indexOfSustaining(alive);
            double lost = 0;
            if (sustaining >= 0) {
                Ship ship = alive.get(sustaining);
                lost = DAMAGE_SHARE * ship.cost();
                alive.set(sustaining, new Ship(ship.cost(), ship.capacity(), false));
            } else {
                Ship ship = alive.removeFirst();
                capacity -= ship.capacity();
                lost = ship.cost();
            }
            states.add(new State(capacity, alive.size(), lost));
        }
        return states;
    }

    private static int indexOfSustaining(List<Ship> ships) {
        for (int index = 0; index < ships.size(); index++) {
            if (ships.get(index).sustains()) return index;
        }
        return -1;
    }

    private static List<Ship> shipsAt(Player seat, Tile tile, List<UnitMove> arriving) {
        List<Ship> ships = new ArrayList<>();
        UnitHolder space = BoardView.space(tile);
        for (UnitType type : BoardView.ships(space, seat).keySet()) {
            addShips(ships, seat, type, BoardView.count(space, seat, type), BoardView.damaged(space, seat, type));
        }
        for (UnitMove move : arriving) {
            boolean ship = BoardView.MOVING_SHIPS.contains(move.type()) || move.type() == UnitType.Fighter;
            if (ship && BoardView.SPACE.equals(move.holder())) addShips(ships, seat, move.type(), move.count(), 0);
        }
        return ships;
    }

    private static void addShips(List<Ship> ships, Player seat, UnitType type, int count, int damaged) {
        UnitModel model = BoardView.model(seat, type).orElse(null);
        if (model == null) return;
        for (int ship = 0; ship < count; ship++) {
            boolean freshSustain = model.getSustainDamage() && ship >= damaged;
            ships.add(new Ship(model.getCost(), BoardView.capacity(seat, type), freshSustain));
        }
    }
}
