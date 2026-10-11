package ti4.ai.tactical;

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
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class StrikeEvaluator {

    private static final Detailed UNDEFENDED = new Detailed(new Outcome(1, 0, 0), 0, 0, 0, 0, 0);

    private record Space(
            double win,
            double ourLoss,
            double ourLossIfWin,
            double theirLoss,
            double killChance,
            double ourLossChance) {}

    static StrikeEstimate estimate(Game game, Player seat, Strike strike) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        UnitHolder space = BoardView.space(target);
        Side attacking = new Side(seat, strike.ships());
        Map<UnitType, Integer> defenderShips = merged(BoardView.ships(space, opponent), strike.extraDefenderShips());
        boolean spaceCombat = !defenderShips.isEmpty();
        int theirCannon = BattleMath.expectedCannonHits(game, seat, target);
        List<Combatant> arriving =
                CombatForces.combatants(game, target, space, attacking, new Side(opponent, Map.of()), strike.damaged());
        List<Combatant> survivingCannon = BattleMath.afterCannonFire(arriving, theirCannon);
        if (theirCannon > 0 && survivingCannon.isEmpty()) return wipedOut(seat, strike, spaceCombat);
        double cannonLoss = costOf(arriving) - costOf(survivingCannon);
        Space fight = spaceCombat
                ? spaceCombat(game, seat, strike, attacking, new Side(opponent, defenderShips), theirCannon)
                : new Space(1, cannonLoss, cannonLoss, 0, 0, cannonLoss > 0 ? 1 : 0);
        if (fight.win() == 0 && spaceCombat) return lost(seat, strike, fight);
        return withGround(game, seat, strike, fight, spaceCombat, bombardingShips(seat, space, attacking));
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

    private static Space spaceCombat(
            Game game, Player seat, Strike strike, Side attacking, Side defending, int theirCannon) {
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
                defendersBefore, BattleMath.ownCannonHits(game, seat, target, strike.origins()));
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

    private static StrikeEstimate withGround(
            Game game,
            Player seat,
            Strike strike,
            Space fight,
            boolean spaceCombat,
            Map<UnitType, Integer> bombarding) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        double spaceWin = fight.win();
        Optional<BombardmentRules.Target> bombardment = BombardmentRules.target(game, seat, target, bombarding);
        Map<UnitType, Integer> remaining = new EnumMap<>(UnitType.class);
        remaining.putAll(strike.cargo());
        Map<String, Integer> landings = new LinkedHashMap<>();
        Map<String, Double> groundWins = new LinkedHashMap<>();
        double ourLoss = fight.ourLoss();
        double theirLoss = fight.theirLoss();
        double noKill = 1;
        boolean groundCombat = false;
        for (Planet planet : TacticalPlanner.enemyPlanets(game, seat, target)) {
            int ground = count(remaining);
            if (ground == 0
                    || (BoardView.enemyStructuresOn(game, seat, planet)
                            && !strike.rules().structuresAllowed())) {
                continue;
            }
            int defenders = BoardView.groundForces(planet, opponent);
            int sent = defenders == 0 ? 1 : ground;
            boolean bombarded = bombardment
                    .filter(found -> found.planet().equals(planet.getName()))
                    .isPresent();
            int bombardmentHits = bombarded ? BombardmentRules.expectedHits(seat, bombarding) : 0;
            Detailed detailed = defenders == 0
                    ? UNDEFENDED
                    : groundDetailed(game, target, planet, seat, opponent, remaining, bombardmentHits);
            double groundWin = detailed.outcome().attackerWins();
            if (groundWin < strike.rules().landMinWin()) continue;
            takeUnits(remaining, sent);
            landings.put(planet.getName(), sent);
            groundWins.put(planet.getName(), groundWin);
            groundCombat |= defenders > 0;
            ourLoss += spaceWin * detailed.attackerCostLost();
            theirLoss += spaceWin * detailed.defenderCostLost();
            noKill *= 1 - spaceWin * detailed.defenderLosesUnit();
        }
        return new StrikeEstimate(
                spaceWin,
                false,
                spaceCombat,
                groundCombat,
                landings,
                groundWins,
                landings,
                ourLoss,
                fight.ourLossIfWin(),
                theirLoss,
                fight.killChance(),
                1 - noKill,
                fight.ourLossChance(),
                strike.ships());
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
            int bombardmentHits) {
        Side attacking = new Side(seat, sent);
        Side defending = new Side(opponent, groundOn(planet, opponent));
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
        for (UnitType type : BoardView.GROUND_FORCES) ground.put(type, BoardView.count(planet, player, type));
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

    static double cannonLoss(Game game, Player seat, Strike strike) {
        int hits = BattleMath.expectedCannonHits(game, seat, strike.target());
        List<Combatant> arriving = CombatForces.combatants(
                game,
                strike.target(),
                BoardView.space(strike.target()),
                new Side(seat, strike.ships()),
                new Side(strike.opponent(), Map.of()),
                strike.damaged());
        return costOf(arriving) - costOf(BattleMath.afterCannonFire(arriving, hits));
    }

    static double shipCost(Player player, Map<UnitType, Integer> units) {
        double cost = 0;
        for (Map.Entry<UnitType, Integer> unit : units.entrySet()) {
            cost += unit.getValue()
                    * BoardView.model(player, unit.getKey())
                            .map(UnitModel::getCost)
                            .orElse(0f);
        }
        return cost;
    }
}
