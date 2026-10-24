package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.CombatOdds.Outcome;
import ti4.ai.eval.IconStake;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.PlanetStake;
import ti4.ai.letnev.L4Disruptors;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class StrikeEvaluator {

    private static final Detailed UNDEFENDED = new Detailed(new Outcome(1, 0, 0), 0, 0, 0, 0, 0);
    private static final String FORESIGHT = "foresight";
    private static final double FORESIGHT_ESCAPE_WIN = 0.5;
    private static final double COST_TOLERANCE = 1e-9;

    private record Space(
            double win,
            double ourLoss,
            double ourLossIfWin,
            double theirLoss,
            double killChance,
            double ourLossChance) {}

    private record Ground(
            Map<String, Integer> landings,
            Map<String, Double> wins,
            Map<String, Integer> sent,
            boolean fought,
            double ourLoss,
            double theirLoss,
            double noKill,
            Map<UnitType, Integer> dropped) {}

    // TODO: Magen Defense Grid, Mentak Ambush, L4 Disruptors and Titans' planetary PDS are not in the odds
    static StrikeEstimate estimate(Game game, Player seat, Strike strike) {
        return estimate(game, seat, strike, true);
    }

    static StrikeEstimate laterSpaceCombat(Game game, Player seat, Strike strike) {
        return estimate(game, seat, strike, false);
    }

    private static StrikeEstimate estimate(Game game, Player seat, Strike strike, boolean openingFire) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        UnitHolder space = BoardView.space(target);
        Side attacking = new Side(seat, strike.ships());
        Map<UnitType, Integer> defenderShips = merged(BoardView.ships(space, opponent), strike.extraDefenderShips());
        boolean spaceCombat = !defenderShips.isEmpty();
        int theirCannon = openingFire ? BattleMath.expectedCannonHits(game, seat, target) : 0;
        List<Combatant> arriving =
                CombatForces.combatants(game, target, space, attacking, new Side(opponent, Map.of()), strike.damaged());
        List<Combatant> survivingCannon = BattleMath.afterCannonFire(arriving, theirCannon);
        if (theirCannon > 0 && survivingCannon.isEmpty() && !arriving.isEmpty()) {
            return wipedOut(seat, strike, spaceCombat);
        }
        double cannonLoss = costOf(arriving) - costOf(survivingCannon);
        Space cannonOnly = new Space(1, cannonLoss, cannonLoss, 0, 0, cannonLoss > 0 ? 1 : 0);
        Space fight = spaceCombat
                ? spaceCombat(
                        game, seat, strike, attacking, new Side(opponent, defenderShips), theirCannon, openingFire)
                : cannonOnly;
        boolean escaped = spaceCombat && escapes(game, strike, fight);
        if (escaped) fight = cannonOnly;
        if (fight.win() == 0) return lost(seat, strike, fight);
        Map<UnitType, Integer> survivors = survivors(seat, strike.ships(), fight.ourLossIfWin());
        Ground ground = ground(game, seat, strike, fight, survivors, bombardingShips(seat, space, attacking));
        double cargoLoss =
                (1 - fight.win()) * shipCost(seat, strike.cargo()) + fight.win() * shipCost(seat, ground.dropped());
        return new StrikeEstimate(
                fight.win(),
                escaped,
                spaceCombat && !escaped,
                ground.fought(),
                ground.landings(),
                ground.wins(),
                ground.sent(),
                fight.ourLoss() + ground.ourLoss() + cargoLoss,
                fight.ourLossIfWin(),
                fight.theirLoss() + ground.theirLoss(),
                fight.killChance(),
                1 - ground.noKill(),
                fight.ourLossChance(),
                survivors);
    }

    private static StrikeEstimate wipedOut(Player seat, Strike strike, boolean spaceCombat) {
        double lost = shipCost(seat, strike.ships()) + shipCost(seat, strike.cargo());
        return new StrikeEstimate(
                0, false, spaceCombat, false, Map.of(), Map.of(), Map.of(), lost, lost, 0, 0, 0, 1, Map.of());
    }

    private static StrikeEstimate lost(Player seat, Strike strike, Space fight) {
        double ourLoss = fight.ourLoss() + shipCost(seat, strike.cargo());
        return new StrikeEstimate(
                0,
                false,
                true,
                false,
                Map.of(),
                Map.of(),
                Map.of(),
                ourLoss,
                fight.ourLossIfWin(),
                fight.theirLoss(),
                fight.killChance(),
                0,
                fight.ourLossChance(),
                Map.of());
    }

    private static boolean escapes(Game game, Strike strike, Space fight) {
        Player opponent = strike.opponent();
        Tile target = strike.target();
        if (!opponent.hasAbility(FORESIGHT) || opponent.getStrategicCC() <= 0) return false;
        boolean shipsPresent =
                !BoardView.ships(BoardView.space(target), opponent).isEmpty();
        if (!shipsPresent || fight.win() < FORESIGHT_ESCAPE_WIN) return false;
        for (String position : FoWHelper.getAdjacentTiles(game, target.getPosition(), opponent, false)) {
            Tile neighbour = game.getTileByPosition(position);
            if (neighbour == null || neighbour == target || !MovementGraph.canEnter(game, opponent, neighbour)) {
                continue;
            }
            if (!BoardView.hasEnemyShips(game, opponent, neighbour)) return true;
        }
        return false;
    }

    private static Space spaceCombat(
            Game game,
            Player seat,
            Strike strike,
            Side attacking,
            Side defending,
            int theirCannon,
            boolean openingFire) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        UnitHolder space = BoardView.space(target);
        Map<UnitType, Integer> defendersDamaged = BattleMath.damagedAmong(space, opponent, defending.units());
        if (CombatForces.combatants(game, target, space, defending, attacking, defendersDamaged)
                .isEmpty()) {
            return new Space(0, shipCost(seat, strike.ships()), 0, 0, 0, 1);
        }
        Side cannonedDefenders = BattleMath.afterAssaultCannon(defending, attacking);
        Side cannonedAttackers = BattleMath.afterAssaultCannon(attacking, defending);
        Side barragedDefenders = BattleMath.afterBarrage(cannonedDefenders, cannonedAttackers);
        Side barragedAttackers = BattleMath.afterBarrage(cannonedAttackers, cannonedDefenders);
        List<Combatant> defendersBefore =
                CombatForces.combatants(game, target, space, barragedDefenders, barragedAttackers, defendersDamaged);
        List<Combatant> defenders = BattleMath.afterCannonFire(
                defendersBefore, openingFire ? BattleMath.ownCannonHits(game, seat, target, strike.origins()) : 0);
        List<Combatant> attackersBefore =
                CombatForces.combatants(game, target, space, barragedAttackers, barragedDefenders, strike.damaged());
        List<Combatant> attackers = BattleMath.afterCannonFire(attackersBefore, theirCannon);
        double preOurLoss = shipCost(seat, attacking.units())
                - shipCost(seat, barragedAttackers.units())
                + costOf(attackersBefore)
                - costOf(attackers);
        double preTheirLoss = shipCost(opponent, defending.units())
                - shipCost(opponent, barragedDefenders.units())
                + costOf(defendersBefore)
                - costOf(defenders);
        Force defence = CombatForces.force(opponent, defenders, false);
        Force attack = CombatForces.withExpectedCopy(
                game, seat, opponent, CombatForces.force(seat, attackers, false), defence, true, false);
        Detailed detailed = CombatOdds.resolveDetailed(attack, defence);
        double win = detailed.outcome().attackerWins();
        double groundInSpace = groundInSpaceCost(opponent, space);
        return new Space(
                win,
                preOurLoss + detailed.attackerCostLost(),
                preOurLoss + detailed.attackerCostLostIfWin(),
                preTheirLoss + detailed.defenderCostLost() + win * groundInSpace,
                preTheirLoss > 0 ? 1 : detailed.defenderLosesUnit(),
                preOurLoss > 0 ? 1 : detailed.attackerLosesUnit());
    }

    private static Map<UnitType, Integer> survivors(Player seat, Map<UnitType, Integer> ships, double lossIfWin) {
        Map<UnitType, Integer> kept = new EnumMap<>(UnitType.class);
        kept.putAll(ships);
        List<UnitType> cheapestFirst = new ArrayList<>(kept.keySet());
        cheapestFirst.sort(Comparator.comparingDouble(type -> unitCost(seat, type)));
        double budget = lossIfWin + COST_TOLERANCE;
        for (UnitType type : cheapestFirst) {
            double cost = unitCost(seat, type);
            while (kept.get(type) > 0 && cost <= budget) {
                kept.merge(type, -1, Integer::sum);
                budget -= cost;
            }
        }
        kept.values().removeIf(count -> count <= 0);
        return kept;
    }

    private static Ground ground(
            Game game,
            Player seat,
            Strike strike,
            Space fight,
            Map<UnitType, Integer> survivors,
            Map<UnitType, Integer> bombarding) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        StrikeRules rules = strike.rules();
        double spaceWin = fight.win();
        Optional<BombardmentRules.Target> bombardment = BombardmentRules.target(game, seat, target, bombarding);
        Map<UnitType, Integer> remaining = new EnumMap<>(UnitType.class);
        remaining.putAll(strike.cargo());
        Map<UnitType, Integer> dropped = dropExcessCargo(seat, remaining, survivors);
        strike.pulled().forEach((type, count) -> remaining.merge(type, count, Integer::sum));
        Map<String, Integer> landings = new LinkedHashMap<>();
        Map<String, Double> wins = new LinkedHashMap<>();
        Map<String, Integer> sentTo = new LinkedHashMap<>();
        double ourLoss = 0;
        double theirLoss = 0;
        double noKill = 1;
        boolean fought = false;
        List<Planet> planets = new ArrayList<>();
        if (!TacticalPlanner.isAnotherPlayersHome(game, seat, target) || rules.homeLandings()) {
            List<Planet> contested = TacticalPlanner.enemyPlanets(game, seat, target);
            contested.stream()
                    .filter(planet -> TacticalPlanner.heldOnlyBy(game, seat, planet, opponent))
                    .forEach(planets::add);
            planets.addAll(freePlanets(game, seat, target, rules, contested));
        }
        Map<UnitType, Integer> extraGround = new EnumMap<>(UnitType.class);
        extraGround.putAll(strike.extraDefenderGround());
        for (Planet planet : planets) {
            int ground = count(remaining);
            if (ground == 0 || structuresForbid(game, seat, planet, rules)) continue;
            Map<UnitType, Integer> defenderGround = groundOn(planet, opponent);
            if (BoardView.controller(game, planet.getName()) == opponent || !defenderGround.isEmpty()) {
                extraGround.forEach((type, count) -> defenderGround.merge(type, count, Integer::sum));
                extraGround.clear();
            }
            int defenders = count(defenderGround);
            int hits = L4Disruptors.shieldsInvaders(seat)
                    ? 0
                    : (int) Math.round(BattleMath.spaceCannonHits(opponent, planet));
            int sent = defenders == 0 ? Math.min(ground, hits + 1) : ground;
            Map<UnitType, Integer> landing = new EnumMap<>(UnitType.class);
            landing.putAll(remaining);
            trim(landing, sent);
            Map<UnitType, Integer> shotDown = removeLanders(landing, hits);
            if (count(landing) == 0) continue;
            Detailed detailed = UNDEFENDED;
            if (defenders > 0) {
                boolean bombarded = bombardment
                        .filter(found -> found.planet().equals(planet.getName()))
                        .isPresent();
                int bombardmentHits = bombarded ? BombardmentRules.expectedHits(seat, bombarding) : 0;
                detailed =
                        groundDetailed(game, target, planet, seat, opponent, landing, defenderGround, bombardmentHits);
            }
            double groundWin = detailed.outcome().attackerWins();
            if (groundWin < rules.landMinWin()) continue;
            takeUnits(remaining, sent);
            landings.put(planet.getName(), BoardView.groundForces(planet, seat) + sent);
            sentTo.put(planet.getName(), sent);
            wins.put(planet.getName(), groundWin);
            fought |= defenders > 0;
            ourLoss += spaceWin * (detailed.attackerCostLost() + shipCost(seat, shotDown));
            theirLoss += spaceWin * detailed.defenderCostLost()
                    + spaceWin * groundWin * PlanetStake.structures(planet, opponent);
            noKill *= 1 - spaceWin * detailed.defenderLosesUnit();
        }
        return new Ground(landings, wins, sentTo, fought, ourLoss, theirLoss, noKill, dropped);
    }

    private static boolean structuresForbid(Game game, Player seat, Planet planet, StrikeRules rules) {
        if (!BoardView.enemyStructuresOn(game, seat, planet)) return false;
        return !rules.structuresAllowed() && !game.mecatols().contains(planet.getName());
    }

    private static List<Planet> freePlanets(
            Game game, Player seat, Tile target, StrikeRules rules, List<Planet> alreadyListed) {
        return target.getPlanetUnitHolders().stream()
                .filter(planet -> !alreadyListed.contains(planet))
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet) || rules.canPayCustodians())
                .sorted(IconStake.claimOrder(game, seat))
                .toList();
    }

    private static Map<UnitType, Integer> dropExcessCargo(
            Player seat, Map<UnitType, Integer> cargo, Map<UnitType, Integer> survivors) {
        int capacity = 0;
        int fighters = 0;
        for (Map.Entry<UnitType, Integer> ship : survivors.entrySet()) {
            capacity += ship.getValue() * BoardView.capacity(seat, ship.getKey());
            if (ship.getKey() == UnitType.Fighter) fighters += ship.getValue();
        }
        int room = Math.max(0, capacity - fighters);
        Map<UnitType, Integer> dropped = new EnumMap<>(UnitType.class);
        int excess = count(cargo) - room;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int removed = Math.min(Math.max(0, excess), cargo.getOrDefault(type, 0));
            if (removed <= 0) continue;
            cargo.merge(type, -removed, Integer::sum);
            dropped.put(type, removed);
            excess -= removed;
        }
        return dropped;
    }

    private static void trim(Map<UnitType, Integer> units, int keep) {
        int left = keep;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int kept = Math.min(left, units.getOrDefault(type, 0));
            units.put(type, kept);
            left -= kept;
        }
    }

    private static Map<UnitType, Integer> removeLanders(Map<UnitType, Integer> landing, int hits) {
        Map<UnitType, Integer> removed = new EnumMap<>(UnitType.class);
        int left = hits;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int taken = Math.min(left, landing.getOrDefault(type, 0));
            if (taken <= 0) continue;
            landing.merge(type, -taken, Integer::sum);
            removed.put(type, taken);
            left -= taken;
        }
        return removed;
    }

    static Map<UnitType, Integer> bombardingShips(Player seat, UnitHolder space, Side attacking) {
        Map<UnitType, Integer> bombarding = new EnumMap<>(UnitType.class);
        bombarding.putAll(BoardView.ships(space, seat));
        attacking.units().forEach((type, count) -> bombarding.merge(type, count, Integer::sum));
        return bombarding;
    }

    private static Detailed groundDetailed(
            Game game,
            Tile tile,
            Planet planet,
            Player seat,
            Player opponent,
            Map<UnitType, Integer> sent,
            Map<UnitType, Integer> defenderGround,
            int bombardmentHits) {
        Side attacking = new Side(seat, sent);
        Side defending = new Side(opponent, defenderGround);
        Map<UnitType, Integer> defendersDamaged = BattleMath.damagedAmong(planet, opponent, defending.units());
        Force defence = CombatForces.force(
                opponent,
                BattleMath.afterCannonFire(
                        CombatForces.combatants(game, tile, planet, defending, attacking, defendersDamaged),
                        bombardmentHits),
                true);
        Force attack = CombatForces.withExpectedCopy(
                game,
                seat,
                opponent,
                CombatForces.force(
                        seat, CombatForces.combatants(game, tile, planet, attacking, defending, Map.of()), true),
                defence,
                true,
                true);
        return CombatOdds.resolveDetailed(attack, defence);
    }

    private static Map<UnitType, Integer> groundOn(UnitHolder planet, Player player) {
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) {
            int count = BoardView.count(planet, player, type);
            if (count > 0) ground.put(type, count);
        }
        return ground;
    }

    private static double groundInSpaceCost(Player opponent, UnitHolder space) {
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) ground.put(type, BoardView.count(space, opponent, type));
        return shipCost(opponent, ground);
    }

    private static Map<UnitType, Integer> merged(Map<UnitType, Integer> first, Map<UnitType, Integer> second) {
        Map<UnitType, Integer> merged = new EnumMap<>(UnitType.class);
        merged.putAll(first);
        second.forEach((type, count) -> merged.merge(type, count, Integer::sum));
        merged.values().removeIf(count -> count <= 0);
        return merged;
    }

    private static int count(Map<UnitType, Integer> units) {
        return units.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void takeUnits(Map<UnitType, Integer> units, int wanted) {
        int left = wanted;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int taken = Math.min(left, units.getOrDefault(type, 0));
            if (taken > 0) units.merge(type, -taken, Integer::sum);
            left -= taken;
        }
    }

    private static double costOf(List<Combatant> combatants) {
        return combatants.stream().mapToDouble(Combatant::cost).sum();
    }

    private static double unitCost(Player player, UnitType type) {
        return BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
    }

    static StrikeEstimate afterSpaceWon(Game game, Player seat, Strike strike, double spaceWin) {
        Space won = new Space(spaceWin, 0, 0, 0, 0, 0);
        UnitHolder space = BoardView.space(strike.target());
        Ground ground = ground(
                game, seat, strike, won, strike.ships(), bombardingShips(seat, space, new Side(seat, strike.ships())));
        Map<String, Integer> landings = new LinkedHashMap<>();
        Map<String, Double> wins = new LinkedHashMap<>();
        Map<String, Integer> sent = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> landing : ground.landings().entrySet()) {
            if (!(strike.target().getUnitHolders().get(landing.getKey()) instanceof Planet planet)
                    || !heldBy(game, planet, strike.opponent())) {
                continue;
            }
            landings.put(landing.getKey(), landing.getValue());
            wins.put(landing.getKey(), ground.wins().get(landing.getKey()));
            sent.put(landing.getKey(), ground.sent().get(landing.getKey()));
        }
        return new StrikeEstimate(
                spaceWin,
                false,
                false,
                ground.fought(),
                landings,
                wins,
                sent,
                ground.ourLoss(),
                0,
                ground.theirLoss(),
                0,
                1 - ground.noKill(),
                0,
                strike.ships());
    }

    private static boolean heldBy(Game game, Planet planet, Player opponent) {
        return BoardView.controller(game, planet.getName()) == opponent || BoardView.groundForces(planet, opponent) > 0;
    }

    static Map<UnitType, Integer> cargoLeft(Player seat, Strike strike, StrikeEstimate estimate) {
        Map<UnitType, Integer> left = new EnumMap<>(UnitType.class);
        left.putAll(strike.cargo());
        dropExcessCargo(seat, left, estimate.survivors());
        strike.pulled().forEach((type, count) -> left.merge(type, count, Integer::sum));
        takeUnits(
                left,
                estimate.groundSent().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
        left.values().removeIf(count -> count <= 0);
        return left;
    }

    static double shipCost(Player player, Map<UnitType, Integer> units) {
        double cost = 0;
        for (Map.Entry<UnitType, Integer> unit : units.entrySet()) {
            cost += unit.getValue() * unitCost(player, unit.getKey());
        }
        return cost;
    }
}
