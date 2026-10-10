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

    public record Combatant(
            int hitsOn,
            int dice,
            boolean sustain,
            double cost,
            @Nullable UnitType type) {

        public Combatant(int hitsOn, int dice, boolean sustain, double cost) {
            this(hitsOn, dice, sustain, cost, null);
        }

        public Combatant withoutSustain() {
            return new Combatant(hitsOn, dice, false, cost, type);
        }

        public Combatant rolling(int newHitsOn, int newDice) {
            return new Combatant(newHitsOn, newDice, sustain, cost, type);
        }
    }

    public record Outcome(double attackerWins, double defenderWins, double bothDestroyed) {}

    public record Force(
            List<Combatant> units,
            int hitMultiplier,
            boolean repairs,
            @Nullable Force afterOpponentLoss) {

        public static Force of(List<Combatant> units) {
            return new Force(units, 1, false, null);
        }

        public Force doublingHits() {
            return new Force(units, 2, repairs, afterOpponentLoss);
        }

        public Force repairing() {
            return new Force(units, hitMultiplier, true, afterOpponentLoss);
        }

        public Force improvingAfterOpponentLoss(Force improved) {
            return new Force(units, hitMultiplier, repairs, improved);
        }

        public Force withUnits(List<Combatant> replaced) {
            return new Force(replaced, hitMultiplier, repairs, afterOpponentLoss);
        }
    }

    public static Outcome resolve(List<Combatant> attacker, List<Combatant> defender) {
        return resolve(Force.of(attacker), Force.of(defender));
    }

    public static Outcome resolve(Force attacker, Force defender) {
        Side a = new Side(attacker);
        Side d = new Side(defender);
        if (a.hitPoints() == 0) return new Outcome(0, 1, 0);
        if (d.hitPoints() == 0) return new Outcome(1, 0, 0);
        double attackerWins = 0;
        double defenderWins = 0;
        double bothDestroyed = 0;
        Map<Long, Double> states = new HashMap<>();
        states.put(key(0, 0), 1.0);
        for (int round = 0; round < MAX_ROUNDS && !states.isEmpty(); round++) {
            Map<Long, Double> next = new HashMap<>();
            for (Map.Entry<Long, Double> state : states.entrySet()) {
                int absorbedByA = (int) (state.getKey() >> 32);
                int absorbedByD = (int) (state.getKey() & 0xffffffffL);
                double[] hitsOnD = a.hitDistribution(absorbedByA, d.lostAUnit(absorbedByD));
                double[] hitsOnA = d.hitDistribution(absorbedByD, a.lostAUnit(absorbedByA));
                for (int x = 0; x < hitsOnD.length; x++) {
                    if (hitsOnD[x] == 0) continue;
                    for (int y = 0; y < hitsOnA.length; y++) {
                        double p = state.getValue() * hitsOnD[x] * hitsOnA[y];
                        if (p == 0) continue;
                        int newA = Math.min(a.hitPoints(), absorbedByA + y * d.hitMultiplier(a.lostAUnit(absorbedByA)));
                        int newD = Math.min(d.hitPoints(), absorbedByD + x * a.hitMultiplier(d.lostAUnit(absorbedByD)));
                        boolean aDead = newA == a.hitPoints();
                        boolean dDead = newD == d.hitPoints();
                        if (aDead && dDead) bothDestroyed += p;
                        else if (dDead) attackerWins += p;
                        else if (aDead) defenderWins += p;
                        else {
                            int repairedA = a.afterRepair(absorbedByA, newA, d.lostAUnit(absorbedByD));
                            int repairedD = d.afterRepair(absorbedByD, newD, a.lostAUnit(absorbedByA));
                            next.merge(key(repairedA, repairedD), p, Double::sum);
                        }
                    }
                }
            }
            states = prune(next);
        }
        double remaining =
                states.values().stream().mapToDouble(Double::doubleValue).sum();
        return new Outcome(attackerWins, defenderWins + remaining, bothDestroyed);
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
        private final Map<Integer, double[]> distributions = new HashMap<>();
        private final Map<Integer, double[]> improvedDistributions = new HashMap<>();

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
        }

        int hitPoints() {
            return lossOrder.size() + sustains;
        }

        boolean lostAUnit(int absorbed) {
            return absorbed > sustains;
        }

        int hitMultiplier(boolean opponentLostAUnit) {
            return active(opponentLostAUnit).hitMultiplier();
        }

        int afterRepair(int before, int after, boolean opponentLostAUnit) {
            boolean damagedEarlier = Math.min(before, sustains) > 0;
            if (!active(opponentLostAUnit).repairs() || !damagedEarlier || after > sustains) return after;
            return after - 1;
        }

        double[] hitDistribution(int absorbed, boolean opponentLostAUnit) {
            boolean improved = opponentLostAUnit && force.afterOpponentLoss() != null;
            Map<Integer, double[]> cache = improved ? improvedDistributions : distributions;
            List<Combatant> units = improved ? improvedOrder : lossOrder;
            return cache.computeIfAbsent(absorbed, count -> computeDistribution(units, count));
        }

        private Force active(boolean opponentLostAUnit) {
            return opponentLostAUnit && force.afterOpponentLoss() != null ? force.afterOpponentLoss() : force;
        }

        private double[] computeDistribution(List<Combatant> units, int absorbed) {
            int destroyed = Math.max(0, absorbed - sustains);
            double[] distribution = {1.0};
            for (Combatant unit : units.subList(Math.min(destroyed, units.size()), units.size())) {
                double hitChance = Math.clamp((11 - unit.hitsOn()) / 10.0, 0.0, 1.0);
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
