package ti4.service.combat.v2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalDiceBasis;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.v2.CombatV2DiceData.DieResult;
import ti4.service.combat.v2.CombatV2DiceData.HitRule;
import ti4.service.combat.v2.CombatV2DiceData.HitRuleTiming;
import ti4.service.combat.v2.CombatV2DiceData.HitTotalAdjustment;
import ti4.service.combat.v2.CombatV2DiceData.HitTotalRule;
import ti4.service.combat.v2.CombatV2DiceData.RerollRule;
import ti4.service.combat.v2.CombatV2DiceData.RollPlan;
import ti4.service.combat.v2.CombatV2DiceData.RollResult;
import ti4.service.combat.v2.CombatV2DiceData.RollSegment;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollResult;

/** Performs only dice operations described by a plain roll plan. */
@UtilityClass
class CombatV2RollEngine {

    private record ActiveDie(DieResult die, RollSource source) {}

    static RollResult roll(RollPlan plan) {
        List<UnitRollResult> unitResults = new ArrayList<>();
        int hits = plan.flatHits();
        int misses = 0;
        int maximumHits = plan.flatHits();
        boolean openEnded = false;
        double allHitChance = 1.0;
        double allMissChance = 1.0;

        for (UnitRollPlan unit : plan.units()) {
            UnitRollResult result = rollUnit(unit);
            unitResults.add(result);
            hits += result.hits();
            misses += result.initialMisses();
            maximumHits += result.maximumHits();
            openEnded |= result.openEnded();
            double hitChance = (11 - unit.threshold()) / 10.0;
            allHitChance *= Math.pow(hitChance, unit.diceCount());
            allMissChance *= Math.pow(1 - hitChance, unit.diceCount());
        }

        int rawHits = hits;
        List<HitTotalAdjustment> adjustments = new ArrayList<>();
        for (HitTotalRule rule : plan.hitTotalRules()) {
            int before = hits;
            hits = switch (rule.operation()) {
                case MULTIPLY -> hits * rule.value();
                case ADD_IF_HIT -> hits > 0 ? hits + rule.value() : hits;
            };
            if (hits != before) adjustments.add(new HitTotalAdjustment(rule.id(), before, hits));
        }

        boolean whiff = maximumHits > 0 && rawHits == 0;
        boolean slam = !openEnded && maximumHits > 0 && rawHits == maximumHits;
        boolean surprisingWhiff = whiff && allMissChance <= 0.02;
        boolean surprisingSlam = slam && allHitChance <= 0.02;
        return new RollResult(
                unitResults,
                rawHits,
                hits,
                misses,
                maximumHits,
                whiff,
                slam,
                surprisingWhiff,
                surprisingSlam,
                adjustments);
    }

    private static UnitRollResult rollUnit(UnitRollPlan plan) {
        List<RollSegment> segments = new ArrayList<>();
        List<ActiveDie> activeDice = rollDice(plan.diceCount(), plan.threshold(), plan.initialSource());
        int initialMisses =
                (int) activeDice.stream().filter(die -> !die.die().success()).count();
        segments.add(segment(plan.initialSource(), activeDice, plan.hitRules()));

        for (AdditionalRollRule additional : plan.additionalRolls()) {
            applyAdditionalRolls(plan, activeDice, segments, additional);
        }

        List<RerollRule> rerolls = new ArrayList<>(plan.rerolls());
        rerolls.sort(java.util.Comparator.comparingInt(CombatV2RollEngine::rerollPriority));
        for (RerollRule reroll : rerolls) {
            List<ActiveDie> selected = select(activeDice, reroll);
            if (selected.isEmpty()) continue;
            if (reroll.replaceSelectedDice()) removeSelected(activeDice, selected);
            List<ActiveDie> rerolled = rollDice(selected.size(), plan.threshold(), reroll.source());
            activeDice.addAll(rerolled);
            segments.add(segment(reroll.source(), rerolled, plan.hitRules()));
        }

        int hits = countHits(activeDice, plan.hitRules()) + plan.flatHits();
        int misses =
                (int) activeDice.stream().filter(die -> !die.die().success()).count();
        List<HitRule> hitRules = plan.hitRules();
        int maximumBonus = 0;
        for (HitRule hitRule : hitRules) maximumBonus += hitRule.bonusHits();
        int maximumHits = plan.diceCount() * (1 + maximumBonus) + plan.flatHits();
        maximumHits = Math.max(maximumHits, hits);
        boolean openEnded = false;
        for (AdditionalRollRule additional : plan.additionalRolls()) openEnded |= additional.repeat();
        return new UnitRollResult(plan, segments, hits, initialMisses, misses, maximumHits, openEnded);
    }

    private static int rerollPriority(RerollRule reroll) {
        return switch (reroll.source()) {
            case JOL_NAR_COMMANDER_HITS, JOL_NAR_COMMANDER_MISSES -> 10;
            case IRON_COMMANDER_MISSES -> 20;
            case MUNITIONS_RESERVES -> 30;
            case KALTRIM_COMMANDER_ONES -> 40;
            default -> 50;
        };
    }

    private static void applyAdditionalRolls(
            UnitRollPlan plan, List<ActiveDie> activeDice, List<RollSegment> segments, AdditionalRollRule rule) {
        List<ActiveDie> triggeringDice = new ArrayList<>(activeDice);
        while (!triggeringDice.isEmpty()) {
            int triggers = additionalRollTriggers(triggeringDice, plan.hitRules(), rule);
            if (triggers == 0) return;
            List<ActiveDie> additionalDice =
                    rollDice(triggers * rule.dicePerTrigger(), plan.threshold(), rule.source());
            activeDice.addAll(additionalDice);
            segments.add(segment(rule.source(), additionalDice, plan.hitRules()));
            if (!rule.repeat()) return;
            triggeringDice = additionalDice;
        }
    }

    private static int additionalRollTriggers(List<ActiveDie> dice, List<HitRule> hitRules, AdditionalRollRule rule) {
        if (rule.basis() == AdditionalDiceBasis.HITS) return countHits(dice, hitRules);
        int matches = 0;
        HitRule trigger = new HitRule(rule.match(), rule.result(), 0);
        for (ActiveDie die : dice) {
            if (matches(die.die(), trigger)) matches++;
        }
        return matches;
    }

    private static List<ActiveDie> rollDice(int count, int threshold, RollSource source) {
        if (count < 1) return new ArrayList<>();
        List<ActiveDie> results = new ArrayList<>();
        for (Die die : DiceHelper.rollDice(threshold, count)) {
            results.add(new ActiveDie(new DieResult(die.getResult(), threshold, die.isSuccess()), source));
        }
        return results;
    }

    private static List<ActiveDie> select(List<ActiveDie> dice, RerollRule rule) {
        List<ActiveDie> selected = new ArrayList<>();
        for (ActiveDie activeDie : dice) {
            DieResult die = activeDie.die();
            boolean matches =
                    switch (rule.selector()) {
                        case MISSES -> !die.success();
                        case HITS -> die.success();
                        case ONES -> die.result() == 1;
                    };
            if (matches) selected.add(activeDie);
            if (rule.maxDice() > 0 && selected.size() == rule.maxDice()) break;
        }
        return selected;
    }

    private static void removeSelected(List<ActiveDie> dice, List<ActiveDie> selected) {
        for (ActiveDie die : selected) dice.remove(die);
    }

    private static RollSegment segment(RollSource source, List<ActiveDie> dice, List<HitRule> rules) {
        return new RollSegment(source, dice.stream().map(ActiveDie::die).toList(), countHits(dice, rules));
    }

    private static int countHits(List<ActiveDie> dice, List<HitRule> rules) {
        int hits = 0;
        for (ActiveDie activeDie : dice) {
            DieResult die = activeDie.die();
            if (die.success()) hits++;
            for (HitRule rule : rules) {
                if (applies(rule.timing(), activeDie.source()) && matches(die, rule)) hits += rule.bonusHits();
            }
        }
        return hits;
    }

    private static boolean applies(HitRuleTiming timing, RollSource source) {
        return switch (timing) {
            case ALL_ROLLS -> true;
            case BEFORE_REROLLS_AND_MUNITIONS -> beforeRerolls(source) || source == RollSource.MUNITIONS_RESERVES;
            case BEFORE_REROLLS -> beforeRerolls(source);
            case INITIAL_ROLL -> initialRoll(source);
        };
    }

    private static boolean beforeRerolls(RollSource source) {
        return initialRoll(source) || source == RollSource.SIGMA_JOL_NAR_FLAGSHIP;
    }

    private static boolean initialRoll(RollSource source) {
        return switch (source) {
            case PRIMARY, SUPERCHARGE_SELECTED_UNIT, SUPERCHARGE_REST, GRAVLEASH_SELECTED_UNIT, GRAVLEASH_REST -> true;
            default -> false;
        };
    }

    private static boolean matches(DieResult die, HitRule rule) {
        return switch (rule.match()) {
            case SUCCESS -> die.success();
            case EXACT_RESULT -> die.result() == rule.result();
            case AT_LEAST_RESULT -> die.result() >= rule.result();
        };
    }
}
