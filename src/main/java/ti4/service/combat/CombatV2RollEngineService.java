package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.service.combat.CombatV2DiceData.AdditionalDiceBasis;
import ti4.service.combat.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.CombatV2DiceData.DieResult;
import ti4.service.combat.CombatV2DiceData.HitRule;
import ti4.service.combat.CombatV2DiceData.RerollRule;
import ti4.service.combat.CombatV2DiceData.RollPlan;
import ti4.service.combat.CombatV2DiceData.RollResult;
import ti4.service.combat.CombatV2DiceData.RollSegment;
import ti4.service.combat.CombatV2DiceData.RollSource;
import ti4.service.combat.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.CombatV2DiceData.UnitRollResult;

/** Performs only dice operations described by a plain roll plan. */
@UtilityClass
class CombatV2RollEngineService {

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
            misses += result.misses();
            maximumHits += result.maximumHits();
            openEnded |= result.openEnded();
            double hitChance = (11 - unit.threshold()) / 10.0;
            allHitChance *= Math.pow(hitChance, unit.diceCount());
            allMissChance *= Math.pow(1 - hitChance, unit.diceCount());
        }

        boolean whiff = hits == 0 && allMissChance <= 0.02;
        boolean slam = !openEnded && hits == maximumHits && allHitChance <= 0.02;
        return new RollResult(unitResults, hits, misses, maximumHits, whiff, slam);
    }

    private static UnitRollResult rollUnit(UnitRollPlan plan) {
        List<RollSegment> segments = new ArrayList<>();
        List<DieResult> activeDice = rollDice(plan.diceCount(), plan.threshold());
        segments.add(new RollSegment(RollSource.PRIMARY, activeDice, countHits(activeDice, plan.hitRules())));

        for (AdditionalRollRule additional : plan.additionalRolls()) {
            applyAdditionalRolls(plan, activeDice, segments, additional);
        }

        for (RerollRule reroll : plan.rerolls()) {
            List<DieResult> selected = select(activeDice, reroll);
            if (selected.isEmpty()) continue;
            if (reroll.replaceSelectedDice()) removeSelected(activeDice, selected);
            List<DieResult> rerolled = rollDice(selected.size(), plan.threshold());
            activeDice.addAll(rerolled);
            segments.add(new RollSegment(reroll.source(), rerolled, countHits(rerolled, plan.hitRules())));
        }

        int hits = countHits(activeDice, plan.hitRules()) + plan.flatHits();
        int misses = (int) activeDice.stream().filter(die -> !die.success()).count();
        List<HitRule> hitRules = plan.hitRules();
        int maximumBonus = 0;
        for (HitRule hitRule : hitRules) maximumBonus += hitRule.bonusHits();
        int maximumHits = plan.diceCount() * (1 + maximumBonus) + plan.flatHits();
        maximumHits = Math.max(maximumHits, hits);
        boolean openEnded = false;
        for (AdditionalRollRule additional : plan.additionalRolls()) openEnded |= additional.repeat();
        return new UnitRollResult(plan, segments, hits, misses, maximumHits, openEnded);
    }

    private static void applyAdditionalRolls(
            UnitRollPlan plan, List<DieResult> activeDice, List<RollSegment> segments, AdditionalRollRule rule) {
        List<DieResult> triggeringDice = new ArrayList<>(activeDice);
        while (!triggeringDice.isEmpty()) {
            int triggers = additionalRollTriggers(triggeringDice, plan.hitRules(), rule);
            if (triggers == 0) return;
            List<DieResult> additionalDice = rollDice(triggers * rule.dicePerTrigger(), plan.threshold());
            activeDice.addAll(additionalDice);
            segments.add(new RollSegment(rule.source(), additionalDice, countHits(additionalDice, plan.hitRules())));
            if (!rule.repeat()) return;
            triggeringDice = additionalDice;
        }
    }

    private static int additionalRollTriggers(List<DieResult> dice, List<HitRule> hitRules, AdditionalRollRule rule) {
        if (rule.basis() == AdditionalDiceBasis.HITS) return countHits(dice, hitRules);
        int matches = 0;
        HitRule trigger = new HitRule(rule.match(), rule.result(), 0);
        for (DieResult die : dice) {
            if (matches(die, trigger)) matches++;
        }
        return matches;
    }

    private static List<DieResult> rollDice(int count, int threshold) {
        if (count < 1) return new ArrayList<>();
        List<DieResult> results = new ArrayList<>();
        for (Die die : DiceHelper.rollDice(threshold, count)) {
            results.add(new DieResult(die.getResult(), threshold, die.isSuccess()));
        }
        return results;
    }

    private static List<DieResult> select(List<DieResult> dice, RerollRule rule) {
        List<DieResult> selected = new ArrayList<>();
        for (DieResult die : dice) {
            boolean matches =
                    switch (rule.selector()) {
                        case MISSES -> !die.success();
                        case HITS -> die.success();
                        case ONES -> die.result() == 1;
                    };
            if (matches) selected.add(die);
            if (rule.maxDice() > 0 && selected.size() == rule.maxDice()) break;
        }
        return selected;
    }

    private static void removeSelected(List<DieResult> dice, List<DieResult> selected) {
        for (DieResult die : selected) dice.remove(die);
    }

    private static int countHits(List<DieResult> dice, List<HitRule> rules) {
        int hits = 0;
        for (DieResult die : dice) {
            if (die.success()) hits++;
            for (HitRule rule : rules) {
                if (matches(die, rule)) hits += rule.bonusHits();
            }
        }
        return hits;
    }

    private static boolean matches(DieResult die, HitRule rule) {
        return switch (rule.match()) {
            case SUCCESS -> die.success();
            case EXACT_RESULT -> die.result() == rule.result();
            case AT_LEAST_RESULT -> die.result() >= rule.result();
        };
    }
}
