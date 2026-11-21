package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.MovementGraph;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.RoundGoals;
import ti4.ai.tactical.RoundGoals.Goal;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.trade.RaisableTradeGoods;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
class OpeningOutlook {

    enum Boost {
        NONE,
        EXTRA_ACTION,
        GRAVITY_DRIVE,
        HOME_PRODUCTION
    }

    enum Order {
        PRODUCE_FIRST,
        MOVE_FIRST
    }

    record Production(Order order, boolean overSupply, double value) {}

    private static final int LAST_OPENING_ROUND = 1;
    private static final int INFANTRY_PER_RESOURCE = 2;
    private static final String GRAVITY_DRIVE = "gd";
    private static final int TRADE_PRIMARY_GOODS = 3;
    private static final double NEXT_ROUND_SHARE = 0.4;
    private static final double LOST_SHIP_PER_RESOURCE = 1.0;
    private static final double HOLD_MARGIN = 0.5;
    private static final double TIE = 1e-9;
    private static final int OVER_SUPPLY_CARRIERS = 1;
    private static final int BEFORE_ANY_ACTION = -1;
    private static final int NEVER = Integer.MAX_VALUE;
    private static final int AFTER_ONE_ACTION = 1;
    private static final int FROM_THE_NEXT_ACTION = 0;

    private record Transport(UnitType type, int capacity, int move) {}

    private record Scenario(Boost boost, Order order, boolean overSupply, int actions, int driveFrom) {

        Scenario(Boost boost, Order order, boolean overSupply, int actions) {
            this(boost, order, overSupply, actions, AFTER_ONE_ACTION);
        }
    }

    private static final class Carrier {
        private final Transport transport;
        private final int bornAt;
        private String position;
        private int cargo;
        private boolean moved;

        Carrier(Transport transport, String position, int bornAt) {
            this.transport = transport;
            this.position = position;
            this.bornAt = bornAt;
        }
    }

    private record Hop(Carrier carrier, Goal goal, int load, double gain) {}

    private record HomeHop(Carrier carrier, String goal, int action, int load) {}

    private record Forward(String position, int move, int cargo) {}

    static double gain(Game game, Player seat, Boost boost) {
        if (game.getRound() > LAST_OPENING_ROUND || boost == Boost.NONE) return 0;
        if (boost != Boost.GRAVITY_DRIVE) return planetGain(game, seat, boost);
        if (holdsTechnology(game, seat)) return gravityDriveGain(game, seat, true, AFTER_ONE_ACTION);
        int driveFrom = holdsStallCard(game, seat) ? FROM_THE_NEXT_ACTION : AFTER_ONE_ACTION;
        return gravityDriveGain(game, seat, canAffordToFollow(game, seat, expectedTradeGoods(game, seat)), driveFrom);
    }

    static double gravityDriveGainAsTechnologyHolder(Game game, Player seat) {
        return gravityDriveGain(game, seat, true, AFTER_ONE_ACTION);
    }

    static double gravityDriveGainFromTrade(Game game, Player seat, boolean picking) {
        if (!(picking || holdsUnplayedTrade(game, seat))
                || holdsTechnology(game, seat)
                || OpeningNeeds.technologyHolder(game, seat).isEmpty()) {
            return 0;
        }
        int washed = RaisableTradeGoods.byWashing(game, seat);
        if (canAffordToFollow(game, seat, washed)) return 0;
        return gravityDriveGain(
                game, seat, canAffordToFollow(game, seat, washed + TRADE_PRIMARY_GOODS), AFTER_ONE_ACTION);
    }

    static int expectedTradeGoods(Game game, Player seat) {
        return raisableTradeGoods(game, seat) + RaisableTradeGoods.fromRivalTrade(game, seat);
    }

    static int raisableTradeGoods(Game game, Player seat) {
        return RaisableTradeGoods.byWashing(game, seat) + (holdsUnplayedTrade(game, seat) ? TRADE_PRIMARY_GOODS : 0);
    }

    private static double gravityDriveGain(Game game, Player seat, boolean funded, int driveFrom) {
        if (game.getRound() > LAST_OPENING_ROUND || !funded || !canResearchGravityDrive(seat)) return 0;
        Optional<Opening> opening = Opening.of(game, seat);
        if (opening.isEmpty()) return 0;
        int actions = seat.getTacticalCC();
        double base = opening.get().value(new Scenario(Boost.NONE, Order.PRODUCE_FIRST, false, actions));
        double driven =
                opening.get().value(new Scenario(Boost.GRAVITY_DRIVE, Order.PRODUCE_FIRST, false, actions, driveFrom));
        return Math.max(0, driven - base);
    }

    private static boolean holdsStallCard(Game game, Player seat) {
        return seat.getSCs().stream()
                .anyMatch(initiative -> !game.getPlayedSCs().contains(initiative)
                        && StrategyCard.of(game, initiative) != StrategyCard.TECHNOLOGY);
    }

    static double planetValue(Game game, Player seat, Boost boost) {
        return Opening.of(game, seat)
                .map(opening -> opening.value(new Scenario(boost, Order.PRODUCE_FIRST, false, seat.getTacticalCC())))
                .orElse(0.0);
    }

    private static double planetGain(Game game, Player seat, Boost boost) {
        Optional<Opening> opening = Opening.of(game, seat);
        if (opening.isEmpty()) return 0;
        int actions = seat.getTacticalCC();
        double base = opening.get().value(new Scenario(Boost.NONE, Order.PRODUCE_FIRST, false, actions));
        double boosted = boost == Boost.HOME_PRODUCTION
                ? opening.get().bestProduction(actions).value()
                : opening.get().value(new Scenario(boost, Order.PRODUCE_FIRST, false, actions));
        return Math.max(0, boosted - base);
    }

    static Optional<Production> bestProduction(Game game, Player seat) {
        if (game.getRound() > LAST_OPENING_ROUND) return Optional.empty();
        return Opening.of(game, seat).map(opening -> opening.bestProduction(seat.getTacticalCC()));
    }

    static boolean producesBeforeMoving(Game game, Player seat) {
        return bestProduction(game, seat)
                .map(production -> production.order() == Order.PRODUCE_FIRST)
                .orElse(true);
    }

    static int overSupplyCarriers(Game game, Player seat, Tile tile) {
        if (tile == null || tile != seat.getHomeSystemTile()) return 0;
        return bestProduction(game, seat).filter(Production::overSupply).isPresent() ? OVER_SUPPLY_CARRIERS : 0;
    }

    static boolean holdsCarriersForHomeBuild(Game game, Player seat) {
        if (game.getRound() > LAST_OPENING_ROUND) return false;
        int actions = seat.getTacticalCC();
        if (actions < 2) return false;
        return Opening.of(game, seat)
                .filter(opening ->
                        opening.value(new Scenario(Boost.HOME_PRODUCTION, Order.PRODUCE_FIRST, false, actions - 1))
                                > opening.value(new Scenario(Boost.HOME_PRODUCTION, Order.MOVE_FIRST, false, actions))
                                        + HOLD_MARGIN)
                .isPresent();
    }

    private static final class Opening {
        private final Game game;
        private final Player seat;
        private final Tile home;
        private final List<Goal> goals;
        private final int garrison;
        private final Map<String, Map<String, Integer>> reach = new HashMap<>();

        private Opening(Game game, Player seat, Tile home, List<Goal> goals) {
            this.game = game;
            this.seat = seat;
            this.home = home;
            this.goals = goals;
            this.garrison = TacticalPlanner.groundKeptHomeWhileTransportsLeave(game, seat);
        }

        static Optional<Opening> of(Game game, Player seat) {
            Tile home = seat.getHomeSystemTile();
            if (home == null) return Optional.empty();
            List<Goal> goals = RoundGoals.of(game, seat).goals().stream()
                    .filter(Goal::needsTransport)
                    .filter(goal -> stillToActivate(game, seat, goal))
                    .toList();
            if (goals.isEmpty()) return Optional.empty();
            return Optional.of(new Opening(game, seat, home, goals));
        }

        private static boolean stillToActivate(Game game, Player seat, Goal goal) {
            Tile tile = game.getTileByPosition(goal.position());
            return tile != null && !tile.hasPlayerCC(seat);
        }

        Production bestProduction(int actions) {
            Production best = null;
            for (Order order : Order.values()) {
                for (boolean overSupply : List.of(false, true)) {
                    double value = value(new Scenario(Boost.HOME_PRODUCTION, order, overSupply, actions));
                    if (best == null || value > best.value() + TIE) best = new Production(order, overSupply, value);
                }
            }
            return best;
        }

        double value(Scenario scenario) {
            return new Round(this, scenario).play();
        }

        Map<String, Integer> reach(String position, int move) {
            return reach.computeIfAbsent(position + "#" + move, key -> MovementGraph.reach(game, seat, position, move));
        }
    }

    private static final class Round {
        private final Opening opening;
        private final Scenario scenario;
        private final List<Carrier> carriers = new ArrayList<>();
        private final Set<String> taken = new HashSet<>();
        private final List<HomeHop> homeHops = new ArrayList<>();
        private int infantry;
        private int producedInfantry;
        private int productionAction = NEVER;
        private boolean secondHop;
        private double value;
        private double lostShips;

        Round(Opening opening, Scenario scenario) {
            this.opening = opening;
            this.scenario = scenario;
        }

        double play() {
            Player seat = opening.seat;
            Tile home = opening.home;
            for (Transport transport : transportsAt(seat, home)) {
                carriers.add(new Carrier(transport, home.getPosition(), BEFORE_ANY_ACTION));
            }
            infantry = Math.max(0, groundForcesAt(seat, home) - opening.garrison);
            secondHop = scenario.boost() == Boost.EXTRA_ACTION;
            boolean producing = scenario.boost() == Boost.HOME_PRODUCTION;
            int productionDue = scenario.order() == Order.PRODUCE_FIRST ? 0 : 1;
            int actions = scenario.actions() + (scenario.boost() == Boost.EXTRA_ACTION ? 1 : 0);
            int action = 0;
            while (action < actions) {
                if (producing && action >= productionDue) {
                    produce(action);
                    producing = false;
                }
                Optional<Hop> hop = bestHop(action);
                if (hop.isPresent()) {
                    apply(hop.get(), action);
                    action++;
                } else if (producing) {
                    produce(action);
                    producing = false;
                } else if (awaitsTheDrive(action)) {
                    action++;
                } else {
                    break;
                }
            }
            if (producing) produce(action);
            return value + nextRound() - lostShips;
        }

        private boolean awaitsTheDrive(int action) {
            return scenario.boost() == Boost.GRAVITY_DRIVE && action < scenario.driveFrom();
        }

        private Optional<Hop> bestHop(int action) {
            int bonusMove = scenario.boost() == Boost.GRAVITY_DRIVE && action >= scenario.driveFrom() ? 1 : 0;
            Hop best = null;
            for (Carrier carrier : carriers) {
                boolean firstHop = !carrier.moved;
                if (!firstHop && !(secondHop && carrier.cargo > 0)) continue;
                int carried = firstHop ? Math.min(carrier.transport.capacity(), infantry) : carrier.cargo;
                if (carried <= 0) continue;
                Map<String, Integer> distances = opening.reach(carrier.position, carrier.transport.move() + bonusMove);
                for (Goal goal : opening.goals) {
                    if (taken.contains(goal.position()) || !distances.containsKey(goal.position())) continue;
                    int load = firstHop && !secondHop ? Math.min(carried, goal.planets()) : carried;
                    double gain = goal.value() * Math.min(load, goal.planets()) / goal.planets();
                    if (best == null || gain > best.gain()) best = new Hop(carrier, goal, load, gain);
                }
            }
            return Optional.ofNullable(best);
        }

        private void apply(Hop hop, int action) {
            Carrier carrier = hop.carrier();
            boolean fromHome = !carrier.moved;
            if (carrier.moved) secondHop = false;
            else infantry -= hop.load();
            carrier.cargo = hop.load() - Math.min(hop.load(), hop.goal().planets());
            carrier.position = hop.goal().position();
            carrier.moved = true;
            taken.add(hop.goal().position());
            value += hop.gain();
            if (fromHome) homeHops.add(new HomeHop(carrier, hop.goal().position(), action, hop.load()));
        }

        private void produce(int action) {
            Game game = opening.game;
            Player seat = opening.seat;
            Tile home = opening.home;
            int budget = homeResources(seat, home) + seat.getTg();
            int units = Helper.getProductionValue(seat, game, home, false);
            long movedOut = carriers.stream()
                    .filter(carrier -> carrier.moved && carrier.bornAt == BEFORE_ANY_ACTION)
                    .count();
            int fleetRoom =
                    (int) (FleetLimit.ships(seat) - BoardView.nonFighterShips(BoardView.space(home), seat) + movedOut);
            OptionalDouble scrap = ProductionPlanner.cheapestShipToScrap(seat, home);
            int overSupply = scenario.overSupply() && scrap.isPresent() ? OVER_SUPPLY_CARRIERS : 0;
            Transport carrier = transport(seat, UnitType.Carrier);
            int carrierCost = (int) Math.ceil(BoardView.model(seat, UnitType.Carrier)
                    .map(model -> model.getCost())
                    .orElse(Float.MAX_VALUE));
            while (carrier != null
                    && carriersOfType() < opening.goals.size()
                    && (fleetRoom > 0 || overSupply > 0)
                    && budget >= carrierCost + 1
                    && units >= 1 + INFANTRY_PER_RESOURCE) {
                if (fleetRoom > 0) {
                    fleetRoom--;
                } else {
                    overSupply--;
                    lostShips += LOST_SHIP_PER_RESOURCE * scrap.getAsDouble();
                }
                carriers.add(new Carrier(carrier, home.getPosition(), action));
                budget -= carrierCost;
                units--;
            }
            producedInfantry = Math.min(units, budget * INFANTRY_PER_RESOURCE);
            infantry += producedInfantry;
            productionAction = action;
        }

        private long carriersOfType() {
            return carriers.stream()
                    .filter(carrier -> carrier.transport.type() == UnitType.Carrier)
                    .count();
        }

        private double nextRound() {
            return Math.max(nextRoundValue(false), nextRoundValue(true));
        }

        private double nextRoundValue(boolean joinLastHomeHop) {
            int pool = infantry;
            List<Forward> forwards = new ArrayList<>();
            Set<Carrier> placed = new HashSet<>();
            Optional<HomeHop> last = joinLastHomeHop && !homeHops.isEmpty()
                    ? Optional.of(homeHops.getLast()).filter(hop -> hop.goal().equals(hop.carrier().position))
                    : Optional.empty();
            if (last.isPresent()) {
                HomeHop hop = last.get();
                int available = hop.action() >= productionAction ? pool : Math.max(0, pool - producedInfantry);
                for (Carrier carrier : carriers) {
                    boolean leader = carrier == hop.carrier();
                    if (!leader && (carrier.moved || carrier.bornAt > hop.action())) continue;
                    int room = carrier.transport.capacity() - (leader ? hop.load() : 0);
                    int fill = Math.max(0, Math.min(available, room));
                    available -= fill;
                    pool -= fill;
                    int cargo = (leader ? carrier.cargo : 0) + fill;
                    forwards.add(new Forward(hop.goal(), nextMove(carrier), cargo));
                    placed.add(carrier);
                }
            }
            for (Carrier carrier : carriers) {
                if (placed.contains(carrier)) continue;
                if (carrier.moved) {
                    forwards.add(new Forward(carrier.position, nextMove(carrier), carrier.cargo));
                } else {
                    int cargo = Math.min(carrier.transport.capacity(), pool);
                    pool -= cargo;
                    forwards.add(new Forward(carrier.position, nextMove(carrier), cargo));
                }
            }
            return NEXT_ROUND_SHARE * claimNextRound(forwards);
        }

        private int nextMove(Carrier carrier) {
            return carrier.transport.move() + (scenario.boost() == Boost.GRAVITY_DRIVE ? 1 : 0);
        }

        private double claimNextRound(List<Forward> forwards) {
            Set<String> claimed = new HashSet<>(taken);
            List<Forward> left = new ArrayList<>(
                    forwards.stream().filter(forward -> forward.cargo() > 0).toList());
            double total = 0;
            while (!left.isEmpty()) {
                Forward bestForward = null;
                Goal bestGoal = null;
                double bestGain = 0;
                for (Forward forward : left) {
                    Map<String, Integer> distances = opening.reach(forward.position(), forward.move());
                    for (Goal goal : opening.goals) {
                        if (claimed.contains(goal.position()) || !distances.containsKey(goal.position())) continue;
                        double gain = goal.value() * Math.min(forward.cargo(), goal.planets()) / goal.planets();
                        if (gain > bestGain) {
                            bestForward = forward;
                            bestGoal = goal;
                            bestGain = gain;
                        }
                    }
                }
                if (bestForward == null) break;
                total += bestGain;
                claimed.add(bestGoal.position());
                left.remove(bestForward);
            }
            return total;
        }
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

    private static boolean canResearchGravityDrive(Player seat) {
        if (seat.hasTech(GRAVITY_DRIVE)) return false;
        TechnologyModel drive = Mapper.getTech(GRAVITY_DRIVE);
        return drive != null && ResearchPolicy.missingPrerequisites(seat, drive, null) == 0;
    }

    private static boolean holdsTechnology(Game game, Player seat) {
        return seat.getSCs().stream()
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == StrategyCard.TECHNOLOGY);
    }

    private static boolean holdsUnplayedTrade(Game game, Player seat) {
        return seat.getSCs().stream()
                .anyMatch(initiative -> !game.getPlayedSCs().contains(initiative)
                        && StrategyCard.of(game, initiative) == StrategyCard.TRADE);
    }

    private static boolean canAffordToFollow(Game game, Player seat, int raisedTradeGoods) {
        return StrategyCardRules.hasSpareStrategyToken(game, seat, StrategyCard.TECHNOLOGY)
                && OpeningNeeds.canFollowTechnology(game, seat, 0, raisedTradeGoods);
    }
}
