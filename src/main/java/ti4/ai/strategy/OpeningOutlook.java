package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Threats;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.tactical.RoundGoals;
import ti4.ai.tactical.RoundGoals.Goal;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
class OpeningOutlook {

    enum Boost {
        NONE,
        EXTRA_ACTION,
        GRAVITY_DRIVE,
        HOME_PRODUCTION
    }

    private static final int LAST_OPENING_ROUND = 1;
    private static final int HOME_GARRISON = 1;
    private static final int INFANTRY_PER_RESOURCE = 2;
    private static final String GRAVITY_DRIVE = "gd";

    private record Transport(UnitType type, int capacity, int move) {}

    private static final class Carrier {
        private final Transport transport;
        private String position;
        private int cargo;
        private boolean moved;

        Carrier(Transport transport, String position) {
            this.transport = transport;
            this.position = position;
        }
    }

    private record Hop(Carrier carrier, Goal goal, int load, double gain) {}

    static double gain(Game game, Player seat, Boost boost) {
        if (game.getRound() > LAST_OPENING_ROUND || boost == Boost.NONE) return 0;
        if (boost == Boost.GRAVITY_DRIVE && !canReachGravityDrive(game, seat)) return 0;
        return Math.max(0, planetValue(game, seat, boost) - planetValue(game, seat, Boost.NONE));
    }

    static double planetValue(Game game, Player seat, Boost boost) {
        Tile home = seat.getHomeSystemTile();
        if (home == null) return 0;
        List<Goal> goals = RoundGoals.of(game, seat).goals().stream()
                .filter(Goal::needsTransport)
                .toList();
        if (goals.isEmpty()) return 0;
        List<Carrier> carriers = new ArrayList<>();
        for (Transport transport : transportsAt(seat, home)) carriers.add(new Carrier(transport, home.getPosition()));
        int garrison = Threats.anyEnemyCanReach(game, seat, home) ? HOME_GARRISON : 0;
        int infantry = Math.max(0, groundForcesAt(seat, home) - garrison);
        if (boost == Boost.HOME_PRODUCTION) infantry += produce(game, seat, home, carriers, goals.size());
        int actions = seat.getTacticalCC() + (boost == Boost.EXTRA_ACTION ? 1 : 0);
        boolean secondHop = boost == Boost.EXTRA_ACTION;
        Map<String, Map<String, Integer>> reach = new HashMap<>();
        Set<String> taken = new HashSet<>();
        double value = 0;
        for (int action = 0; action < actions; action++) {
            int bonusMove = boost == Boost.GRAVITY_DRIVE && action > 0 ? 1 : 0;
            Hop best = null;
            for (Carrier carrier : carriers) {
                boolean firstHop = !carrier.moved;
                if (!firstHop && !(secondHop && carrier.cargo > 0)) continue;
                int carried = firstHop ? Math.min(carrier.transport.capacity(), infantry) : carrier.cargo;
                if (carried <= 0) continue;
                int move = carrier.transport.move() + bonusMove;
                Map<String, Integer> distances = reach.computeIfAbsent(
                        carrier.position + "#" + move, key -> MovementGraph.reach(game, seat, carrier.position, move));
                for (Goal goal : goals) {
                    if (taken.contains(goal.position()) || !distances.containsKey(goal.position())) continue;
                    int load = firstHop && !secondHop ? Math.min(carried, goal.planets()) : carried;
                    double gain = goal.value() * Math.min(load, goal.planets()) / goal.planets();
                    if (best == null || gain > best.gain()) best = new Hop(carrier, goal, load, gain);
                }
            }
            if (best == null) break;
            Carrier carrier = best.carrier();
            if (carrier.moved) secondHop = false;
            else infantry -= best.load();
            carrier.cargo = best.load() - Math.min(best.load(), best.goal().planets());
            carrier.position = best.goal().position();
            carrier.moved = true;
            taken.add(best.goal().position());
            value += best.gain();
        }
        return value;
    }

    private static int produce(Game game, Player seat, Tile home, List<Carrier> carriers, int targets) {
        int budget = homeResources(seat, home) + seat.getTg();
        int units = Helper.getProductionValue(seat, game, home, false);
        int fleetRoom = seat.getFleetCC() - BoardView.nonFighterShips(BoardView.space(home), seat);
        Transport carrier = transport(seat, UnitType.Carrier);
        int carrierCost = (int) Math.ceil(BoardView.model(seat, UnitType.Carrier)
                .map(model -> model.getCost())
                .orElse(Float.MAX_VALUE));
        while (carrier != null
                && carriers.size() < targets
                && fleetRoom > 0
                && budget >= carrierCost + 1
                && units >= 1 + INFANTRY_PER_RESOURCE) {
            carriers.add(new Carrier(carrier, home.getPosition()));
            budget -= carrierCost;
            units--;
            fleetRoom--;
        }
        return Math.min(units, budget * INFANTRY_PER_RESOURCE);
    }

    private static int homeResources(Player seat, Tile home) {
        return home.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getReadiedPlanets().contains(planet.getName()))
                .mapToInt(Planet::getResources)
                .sum();
    }

    private static List<Transport> transportsAt(Player seat, Tile home) {
        UnitHolder space = BoardView.space(home);
        List<Transport> transports = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            Transport transport = transport(seat, type);
            if (transport == null) continue;
            for (int i = 0; i < BoardView.count(space, seat, type); i++) transports.add(transport);
        }
        return transports;
    }

    private static Transport transport(Player seat, UnitType type) {
        int capacity = BoardView.capacity(seat, type);
        int move = BoardView.moveValue(seat, type);
        return capacity > 0 && move > 0 ? new Transport(type, capacity, move) : null;
    }

    private static int groundForcesAt(Player seat, Tile home) {
        return home.getUnitHolders().values().stream()
                .mapToInt(holder -> BoardView.groundForces(holder, seat))
                .sum();
    }

    private static boolean canReachGravityDrive(Game game, Player seat) {
        if (seat.hasTech(GRAVITY_DRIVE)) return false;
        TechnologyModel drive = Mapper.getTech(GRAVITY_DRIVE);
        if (drive == null || ResearchPolicy.missingPrerequisites(seat, drive, null) != 0) return false;
        return holdsTechnology(game, seat) || canAffordToFollow(game, seat);
    }

    private static boolean holdsTechnology(Game game, Player seat) {
        return seat.getSCs().stream()
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == StrategyCard.TECHNOLOGY);
    }

    private static boolean canAffordToFollow(Game game, Player seat) {
        return seat.getStrategicCC() > 0
                && ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(StrategyCardRules.FOLLOW_TECH_COST))
                        .isPresent();
    }
}
