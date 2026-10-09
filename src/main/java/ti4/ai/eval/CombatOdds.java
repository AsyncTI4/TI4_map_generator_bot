package ti4.ai.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CombatOdds {

    private static final int MAX_ROUNDS = 60;
    private static final double NEGLIGIBLE = 1e-7;

    public record Combatant(int hitsOn, int dice, boolean sustain, double cost) {}

    public record Outcome(double attackerWins, double defenderWins, double bothDestroyed) {}

    public static Outcome resolve(List<Combatant> attacker, List<Combatant> defender) {
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
                double[] hitsOnD = a.hitDistribution(absorbedByA);
                double[] hitsOnA = d.hitDistribution(absorbedByD);
                for (int x = 0; x < hitsOnD.length; x++) {
                    if (hitsOnD[x] == 0) continue;
                    for (int y = 0; y < hitsOnA.length; y++) {
                        double p = state.getValue() * hitsOnD[x] * hitsOnA[y];
                        if (p == 0) continue;
                        int newA = Math.min(a.hitPoints(), absorbedByA + y);
                        int newD = Math.min(d.hitPoints(), absorbedByD + x);
                        boolean aDead = newA == a.hitPoints();
                        boolean dDead = newD == d.hitPoints();
                        if (aDead && dDead) bothDestroyed += p;
                        else if (dDead) attackerWins += p;
                        else if (aDead) defenderWins += p;
                        else next.merge(key(newA, newD), p, Double::sum);
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

        private final List<Combatant> lossOrder;
        private final int sustains;
        private final Map<Integer, double[]> distributions = new HashMap<>();

        Side(List<Combatant> units) {
            lossOrder = new ArrayList<>(units);
            lossOrder.sort(Comparator.comparingDouble(Combatant::cost));
            sustains = (int) units.stream().filter(Combatant::sustain).count();
        }

        int hitPoints() {
            return lossOrder.size() + sustains;
        }

        double[] hitDistribution(int absorbed) {
            return distributions.computeIfAbsent(absorbed, this::computeDistribution);
        }

        private double[] computeDistribution(int absorbed) {
            int destroyed = Math.max(0, absorbed - sustains);
            double[] distribution = {1.0};
            for (Combatant unit : lossOrder.subList(Math.min(destroyed, lossOrder.size()), lossOrder.size())) {
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
