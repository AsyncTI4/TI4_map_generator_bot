package ti4.ai.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class CombatOdds {

    private static final int MAX_ROUNDS = 60;
    private static final double NEGLIGIBLE = 1e-7;
    private static final int ATTACKER_COST = 0;
    private static final int DEFENDER_COST = 1;
    private static final int ATTACKER_COST_IF_WIN = 2;
    private static final int ATTACKER_UNIT = 3;
    private static final int DEFENDER_UNIT = 4;

    public record Combatant(
            int hitsOn,
            int dice,
            boolean sustain,
            double cost,
            @Nullable UnitType type,
            boolean retaliates) {

        public Combatant(int hitsOn, int dice, boolean sustain, double cost) {
            this(hitsOn, dice, sustain, cost, null, false);
        }

        public Combatant(int hitsOn, int dice, boolean sustain, double cost, @Nullable UnitType type) {
            this(hitsOn, dice, sustain, cost, type, false);
        }

        public Combatant retaliatingOnSustain() {
            return new Combatant(hitsOn, dice, sustain, cost, type, true);
        }

        public Combatant withoutSustain() {
            return new Combatant(hitsOn, dice, false, cost, type, false);
        }

        public Combatant rolling(int newHitsOn, int newDice) {
            return new Combatant(newHitsOn, newDice, sustain, cost, type, retaliates);
        }
    }

    public record Outcome(double attackerWins, double defenderWins, double bothDestroyed) {}

    public record Force(
            List<Combatant> units,
            int hitMultiplier,
            boolean repairs,
            @Nullable Force afterOpponentLoss,
            boolean weaves,
            int rerollRounds) {

        public Force(List<Combatant> units, int hitMultiplier, boolean repairs, @Nullable Force afterOpponentLoss) {
            this(units, hitMultiplier, repairs, afterOpponentLoss, false, 0);
        }

        public static Force of(List<Combatant> units) {
            return new Force(units, 1, false, null, false, 0);
        }

        public Force doublingHits() {
            return new Force(units, 2, repairs, afterOpponentLoss, weaves, rerollRounds);
        }

        public Force repairing() {
            return new Force(units, hitMultiplier, true, afterOpponentLoss, weaves, rerollRounds);
        }

        public Force weaving() {
            return new Force(units, hitMultiplier, repairs, afterOpponentLoss, true, rerollRounds);
        }

        public Force improvingAfterOpponentLoss(Force improved) {
            return new Force(units, hitMultiplier, repairs, improved, weaves, rerollRounds);
        }

        public Force withUnits(List<Combatant> replaced) {
            return new Force(replaced, hitMultiplier, repairs, afterOpponentLoss, weaves, rerollRounds);
        }

        public Force rerollingMissesInFirstRounds(int rounds) {
            return new Force(units, hitMultiplier, repairs, afterOpponentLoss, weaves, Math.max(0, rounds));
        }
    }

    public static Outcome resolve(List<Combatant> attacker, List<Combatant> defender) {
        return resolve(Force.of(attacker), Force.of(defender));
    }

    public record Detailed(
            Outcome outcome,
            double attackerCostLost,
            double defenderCostLost,
            double attackerCostLostIfWin,
            double attackerLosesUnit,
            double defenderLosesUnit) {}

    public static Outcome resolve(Force attacker, Force defender) {
        return resolveDetailed(attacker, defender).outcome();
    }

    public static Outcome resolveFirstRound(Force attacker, Force defender, Force firstAttacker, Force firstDefender) {
        return resolveDetailed(attacker, defender, firstAttacker, firstDefender, 0, 0)
                .outcome();
    }

    public static Detailed resolveDetailed(Force attacker, Force defender) {
        return resolveDetailed(attacker, defender, attacker, defender, 0, 0);
    }

    public static Detailed resolveDetailed(
            Force attacker, Force defender, Force firstAttacker, Force firstDefender, int absorbedA, int absorbedD) {
        Side a = new Side(attacker);
        Side d = new Side(defender);
        Side firstA = firstAttacker == attacker ? a : new Side(firstAttacker);
        Side firstD = firstDefender == defender ? d : new Side(firstDefender);
        if (a.hitPoints() == 0) return new Detailed(new Outcome(0, 1, 0), 0, 0, 0, 0, 0);
        if (d.hitPoints() == 0) return new Detailed(new Outcome(1, 0, 0), 0, 0, 0, 0, 0);
        double attackerWins = 0;
        double defenderWins = 0;
        double bothDestroyed = 0;
        double[] losses = new double[5];
        Map<Long, Double> states = new HashMap<>();
        states.put(key(absorbedA, absorbedD), 1.0);
        for (int round = 0; round < MAX_ROUNDS && !states.isEmpty(); round++) {
            Map<Long, Double> next = new HashMap<>();
            for (Map.Entry<Long, Double> state : states.entrySet()) {
                int absorbedByA = (int) (state.getKey() >> 32);
                int absorbedByD = (int) (state.getKey() & 0xffffffffL);
                Side rollingA = round == 0 ? firstA : a;
                Side rollingD = round == 0 ? firstD : d;
                double[] hitsOnD = rollingA.hitDistribution(absorbedByA, d.lostAUnit(absorbedByD), round);
                double[] hitsOnA = rollingD.hitDistribution(absorbedByD, a.lostAUnit(absorbedByA), round);
                for (int x = 0; x < hitsOnD.length; x++) {
                    if (hitsOnD[x] == 0) continue;
                    for (int y = 0; y < hitsOnA.length; y++) {
                        double p = state.getValue() * hitsOnD[x] * hitsOnA[y];
                        if (p == 0) continue;
                        boolean aLostAUnit = a.lostAUnit(absorbedByA);
                        boolean dLostAUnit = d.lostAUnit(absorbedByD);
                        int hitsByA = x + a.weavedHit(dLostAUnit, y);
                        int hitsByD = y + d.weavedHit(aLostAUnit, x);
                        int struckA = absorbedByA + hitsByD * d.hitMultiplier(aLostAUnit);
                        int struckD = absorbedByD + hitsByA * a.hitMultiplier(dLostAUnit);
                        int newA = Math.min(a.hitPoints(), struckA + d.retaliation(absorbedByD, struckD));
                        int newD = Math.min(d.hitPoints(), struckD + a.retaliation(absorbedByA, struckA));
                        boolean aDead = newA == a.hitPoints();
                        boolean dDead = newD == d.hitPoints();
                        if (aDead && dDead) {
                            bothDestroyed += p;
                            addLosses(losses, p, a, newA, d, newD, false);
                        } else if (dDead) {
                            attackerWins += p;
                            addLosses(losses, p, a, newA, d, newD, true);
                        } else if (aDead) {
                            defenderWins += p;
                            addLosses(losses, p, a, newA, d, newD, false);
                        } else {
                            int repairedA = a.afterRepair(absorbedByA, newA, d.lostAUnit(absorbedByD));
                            int repairedD = d.afterRepair(absorbedByD, newD, a.lostAUnit(absorbedByA));
                            next.merge(key(repairedA, repairedD), p, Double::sum);
                        }
                    }
                }
            }
            states = prune(next);
        }
        double remaining = 0;
        for (Map.Entry<Long, Double> state : states.entrySet()) {
            remaining += state.getValue();
            int absorbedByA = (int) (state.getKey() >> 32);
            int absorbedByD = (int) (state.getKey() & 0xffffffffL);
            addLosses(losses, state.getValue(), a, absorbedByA, d, absorbedByD, false);
        }
        double costLostIfWin = attackerWins == 0 ? 0 : losses[ATTACKER_COST_IF_WIN] / attackerWins;
        return new Detailed(
                new Outcome(attackerWins, defenderWins + remaining, bothDestroyed),
                losses[ATTACKER_COST],
                losses[DEFENDER_COST],
                costLostIfWin,
                losses[ATTACKER_UNIT],
                losses[DEFENDER_UNIT]);
    }

    private static void addLosses(
            double[] losses, double p, Side a, int absorbedByA, Side d, int absorbedByD, boolean attackerWon) {
        double attackerCost = a.costLost(absorbedByA);
        double defenderCost = d.costLost(absorbedByD);
        losses[ATTACKER_COST] += p * attackerCost;
        losses[DEFENDER_COST] += p * defenderCost;
        if (attackerWon) losses[ATTACKER_COST_IF_WIN] += p * attackerCost;
        if (attackerCost > 0) losses[ATTACKER_UNIT] += p;
        if (defenderCost > 0) losses[DEFENDER_UNIT] += p;
    }

    private static Map<Long, Double> prune(Map<Long, Double> states) {
        states.values().removeIf(p -> p < NEGLIGIBLE);
        return states;
    }

    private static long key(int absorbedByA, int absorbedByD) {
        return ((long) absorbedByA << 32) | (absorbedByD & 0xffffffffL);
    }

    private static final class Side {

        private final Force force;
        private final List<Combatant> lossOrder;
        private final List<Combatant> improvedOrder;
        private final int sustains;
        private final int retaliators;
        private final double[] lostCosts;
        private final Map<Integer, double[]> distributions = new HashMap<>();
        private final Map<Integer, double[]> improvedDistributions = new HashMap<>();
        private final Map<Integer, double[]> rerolledDistributions = new HashMap<>();
        private final Map<Integer, double[]> improvedRerolledDistributions = new HashMap<>();

        Side(Force force) {
            this.force = force;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < force.units().size(); i++) order.add(i);
            order.sort(Comparator.comparingDouble(i -> force.units().get(i).cost()));
            lossOrder = order.stream().map(force.units()::get).toList();
            Force improved = force.afterOpponentLoss();
            improvedOrder =
                    improved != null && improved.units().size() == force.units().size()
                            ? order.stream().map(improved.units()::get).toList()
                            : lossOrder;
            sustains = (int) force.units().stream().filter(Combatant::sustain).count();
            retaliators = (int) force.units().stream()
                    .filter(unit -> unit.sustain() && unit.retaliates())
                    .count();
            lostCosts = new double[lossOrder.size() + 1];
            for (int i = 0; i < lossOrder.size(); i++)
                lostCosts[i + 1] = lostCosts[i] + lossOrder.get(i).cost();
        }

        double costLost(int absorbed) {
            return lostCosts[Math.min(lossOrder.size(), Math.max(0, absorbed - sustains))];
        }

        int hitPoints() {
            return lossOrder.size() + sustains;
        }

        boolean lostAUnit(int absorbed) {
            return absorbed > sustains;
        }

        int weavedHit(boolean opponentLostAUnit, int opponentHits) {
            return active(opponentLostAUnit).weaves() && opponentHits > 0 ? 1 : 0;
        }

        int retaliation(int absorbedBefore, int absorbedAfter) {
            return Math.max(0, Math.min(absorbedAfter, retaliators) - Math.min(absorbedBefore, retaliators));
        }

        int hitMultiplier(boolean opponentLostAUnit) {
            return active(opponentLostAUnit).hitMultiplier();
        }

        int afterRepair(int before, int after, boolean opponentLostAUnit) {
            boolean damagedEarlier = Math.min(before, sustains) > 0;
            if (!active(opponentLostAUnit).repairs() || !damagedEarlier || after > sustains) return after;
            return after - 1;
        }

        double[] hitDistribution(int absorbed, boolean opponentLostAUnit, int round) {
            boolean improved = opponentLostAUnit && force.afterOpponentLoss() != null;
            boolean rerolling = round < active(opponentLostAUnit).rerollRounds();
            Map<Integer, double[]> cache = improved
                    ? (rerolling ? improvedRerolledDistributions : improvedDistributions)
                    : (rerolling ? rerolledDistributions : distributions);
            List<Combatant> units = improved ? improvedOrder : lossOrder;
            return cache.computeIfAbsent(absorbed, count -> computeDistribution(units, count, rerolling));
        }

        private Force active(boolean opponentLostAUnit) {
            return opponentLostAUnit && force.afterOpponentLoss() != null ? force.afterOpponentLoss() : force;
        }

        private double[] computeDistribution(List<Combatant> units, int absorbed, boolean rerollingMisses) {
            int destroyed = Math.max(0, absorbed - sustains);
            double[] distribution = {1.0};
            for (Combatant unit : units.subList(Math.min(destroyed, units.size()), units.size())) {
                double single = Math.clamp((11 - unit.hitsOn()) / 10.0, 0.0, 1.0);
                double hitChance = rerollingMisses ? 1 - (1 - single) * (1 - single) : single;
                for (int die = 0; die < unit.dice(); die++) distribution = addDie(distribution, hitChance);
            }
            return distribution;
        }

        private static double[] addDie(double[] distribution, double hitChance) {
            double[] result = new double[distribution.length + 1];
            for (int hits = 0; hits < distribution.length; hits++) {
                result[hits] += distribution[hits] * (1 - hitChance);
                result[hits + 1] += distribution[hits] * hitChance;
            }
            return result;
        }
    }
}
