package ti4.ai.letnev;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class LetnevSpaceCombat {

    public static final String MUNITIONS_RESERVES = "munitions";
    public static final int MUNITIONS_COST = 2;
    private static final String ARC_SECUNDUS = "letnev_flagship";
    private static final String GRAVLEASH_MANEUVERS = "letnevbt";
    private static final int PLANNED_REROLL_ROUNDS = 3;

    public static Force applyTo(Player player, Force force) {
        Force result = force;
        if (repairsFlagship(player, force)) result = result.repairing();
        int rounds = plannedRerolls(player);
        if (rounds > 0) result = result.rerollingMissesInFirstRounds(rounds);
        if (player.hasUnlockedBreakthrough(GRAVLEASH_MANEUVERS)) {
            result = result.withUnits(gravleashed(result.units()));
        }
        return result;
    }

    public static int plannedRerolls(Player player) {
        if (!player.hasAbility(MUNITIONS_RESERVES)) return 0;
        return Math.min(PLANNED_REROLL_ROUNDS, player.getTg() / MUNITIONS_COST);
    }

    public static boolean repairsFlagship(Player player, Force force) {
        return player.hasUnit(ARC_SECUNDUS)
                && force.units().stream().anyMatch(unit -> unit.type() == UnitType.Flagship);
    }

    public static int shipTypes(List<Combatant> units) {
        return (int) units.stream()
                .map(Combatant::type)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    static List<Combatant> gravleashed(List<Combatant> units) {
        int bonus = shipTypes(units);
        int chosen = mostDiceLastToDie(units);
        if (bonus == 0 || chosen < 0) return units;
        List<Combatant> boosted = new ArrayList<>(units);
        Combatant unit = units.get(chosen);
        boosted.set(chosen, unit.rolling(unit.hitsOn() - bonus, unit.dice()));
        return boosted;
    }

    private static int mostDiceLastToDie(List<Combatant> units) {
        int chosen = -1;
        for (int i = 0; i < units.size(); i++) {
            Combatant unit = units.get(i);
            if (unit.type() == null) continue;
            if (chosen < 0 || beats(unit, units.get(chosen))) chosen = i;
        }
        return chosen;
    }

    private static boolean beats(Combatant candidate, Combatant current) {
        if (candidate.dice() != current.dice()) return candidate.dice() > current.dice();
        return candidate.cost() >= current.cost();
    }
}
