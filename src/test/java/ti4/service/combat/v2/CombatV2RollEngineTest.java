package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.helpers.DiceHelper;
import ti4.model.UnitModel;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalDiceBasis;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.v2.CombatV2DiceData.HitMatch;
import ti4.service.combat.v2.CombatV2DiceData.HitRule;
import ti4.service.combat.v2.CombatV2DiceData.HitRuleTiming;
import ti4.service.combat.v2.CombatV2DiceData.HitTotalOperation;
import ti4.service.combat.v2.CombatV2DiceData.HitTotalRule;
import ti4.service.combat.v2.CombatV2DiceData.ModifierDuration;
import ti4.service.combat.v2.CombatV2DiceData.ModifierEffect;
import ti4.service.combat.v2.CombatV2DiceData.RerollRule;
import ti4.service.combat.v2.CombatV2DiceData.RerollSelector;
import ti4.service.combat.v2.CombatV2DiceData.RollModifier;
import ti4.service.combat.v2.CombatV2DiceData.RollPlan;
import ti4.service.combat.v2.CombatV2DiceData.RollResult;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.v2.CombatV2DiceData.ValueModifier;

class CombatV2RollEngineTest {

    @Test
    void appliesModifiersAndSuccessBasedHitRules() {
        UnitRollPlan unit = unit(2, 1, 8, 1, List.of(new HitRule(HitMatch.SUCCESS, 0, 1)), List.of());
        try (MockedStatic<DiceHelper> ignored = mockDice(6, 7, 10)) {
            RollResult result = CombatV2RollEngine.roll(new RollPlan(List.of(unit), 0, List.of()));
            assertEquals(4, result.totalHits());
            assertEquals(1, result.totalMisses());
        }
    }

    @Test
    void commanderRerollIsDataNotEngineBranching() {
        RerollRule commander = new RerollRule(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES, 0, true);
        UnitRollPlan unit = unit(2, 0, 8, 0, List.of(), List.of(commander));
        try (MockedStatic<DiceHelper> ignored = mockDice(5, 8, 9)) {
            RollResult result = CombatV2RollEngine.roll(new RollPlan(List.of(unit), 0, List.of()));
            assertEquals(2, result.totalHits());
            assertEquals(1, result.totalMisses());
            assertEquals(2, result.units().getFirst().segments().size());
        }
    }

    @Test
    void rerollingHitsCanReplaceAResult() {
        RerollRule proxima = new RerollRule(RollSource.JOL_NAR_COMMANDER_HITS, RerollSelector.HITS, 0, true);
        UnitRollPlan unit = unit(1, 0, 8, 0, List.of(), List.of(proxima));
        try (MockedStatic<DiceHelper> ignored = mockDice(9, 2)) {
            RollResult result = CombatV2RollEngine.roll(new RollPlan(List.of(unit), 0, List.of()));
            assertEquals(0, result.totalHits());
            assertEquals(0, result.totalMisses());
        }
    }

    @Test
    void recursiveFollowUpDiceAreDataDriven() {
        AdditionalRollRule exploding = new AdditionalRollRule(
                RollSource.SIGMA_JOL_NAR_FLAGSHIP, AdditionalDiceBasis.HITS, HitMatch.SUCCESS, 0, 1, true);
        UnitRollPlan unit = new UnitRollPlan(new UnitModel(), null, 1, 1, 8, RollSource.PRIMARY, List.of(exploding));
        try (MockedStatic<DiceHelper> ignored = mockDice(10, 9, 2)) {
            RollResult result = CombatV2RollEngine.roll(new RollPlan(List.of(unit), 0, List.of()));
            assertEquals(2, result.totalHits());
            assertEquals(3, result.units().getFirst().segments().size());
        }
    }

    @Test
    void appliesOrderedFinalHitRulesAfterPerDieBonusHits() {
        UnitRollPlan unit = unit(1, 0, 8, 0, List.of(new HitRule(HitMatch.SUCCESS, 0, 2)), List.of());
        RollPlan plan = new RollPlan(
                List.of(unit),
                0,
                List.of(
                        new HitTotalRule("x89", HitTotalOperation.MULTIPLY, 2),
                        new HitTotalRule("shard", HitTotalOperation.ADD_IF_HIT, 1)));
        try (MockedStatic<DiceHelper> ignored = mockDice(9)) {
            RollResult result = CombatV2RollEngine.roll(plan);
            assertEquals(3, result.rawHits());
            assertEquals(7, result.totalHits());
        }
    }

    @Test
    void initialHitRulesDoNotLeakOntoCommanderRerolls() {
        HitRule valor = new HitRule(HitMatch.EXACT_RESULT, 10, 1);
        RerollRule commander = new RerollRule(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES, 0, true);
        UnitRollPlan unit = unit(1, 0, 8, 0, List.of(valor), List.of(commander));
        try (MockedStatic<DiceHelper> ignored = mockDice(2, 10)) {
            RollResult result = CombatV2RollEngine.roll(new RollPlan(List.of(unit), 0, List.of()));
            assertEquals(1, result.totalHits());
        }
    }

    @Test
    void gloryValorAppliesToGeneratedDiceAndMunitionsButNotCommanderRerolls() {
        HitRule glory = new HitRule(HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS_AND_MUNITIONS);
        AdditionalRollRule generated = new AdditionalRollRule(
                RollSource.SIGMA_JOL_NAR_FLAGSHIP, AdditionalDiceBasis.HITS, HitMatch.SUCCESS, 0, 1, false);
        RerollRule munitions = new RerollRule(RollSource.MUNITIONS_RESERVES, RerollSelector.MISSES, 0, true);
        UnitRollPlan unit = new UnitRollPlan(
                new UnitModel(), null, 1, 2, 8, RollSource.PRIMARY, List.of(glory, generated, munitions));
        try (MockedStatic<DiceHelper> ignored = mockDice(10, 2, 10, 3, 10, 10)) {
            RollResult result = CombatV2RollEngine.roll(new RollPlan(List.of(unit), 0, List.of()));
            assertEquals(8, result.totalHits());
        }
    }

    private static UnitRollPlan unit(
            int dice, int extraDice, int hitsOn, int modifier, List<HitRule> hitRules, List<RerollRule> rerolls) {
        List<RollModifier> modifiers = new ArrayList<>();
        if (extraDice != 0) modifiers.add(rollModifier(ModifierEffect.EXTRA_DICE, extraDice));
        if (modifier != 0) modifiers.add(rollModifier(ModifierEffect.TO_HIT, modifier));
        modifiers.addAll(hitRules);
        modifiers.addAll(rerolls);
        return new UnitRollPlan(new UnitModel(), null, 1, dice, hitsOn, RollSource.PRIMARY, modifiers);
    }

    private static RollModifier rollModifier(ModifierEffect effect, int value) {
        return new ValueModifier("test", effect, value, ModifierDuration.PERMANENT, "Test");
    }

    private static MockedStatic<DiceHelper> mockDice(int... results) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int result : results) queue.add(result);
        MockedStatic<DiceHelper> dice = mockStatic(DiceHelper.class, CALLS_REAL_METHODS);
        dice.when(() -> DiceHelper.rollDice(anyInt(), anyInt())).thenAnswer(invocation -> {
            int threshold = invocation.getArgument(0);
            int count = invocation.getArgument(1);
            List<DiceHelper.Die> rolls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Integer result = queue.poll();
                if (result == null) throw new AssertionError("Not enough queued dice.");
                rolls.add(DiceHelper.spoof(threshold, result));
            }
            return rolls;
        });
        return dice;
    }
}
