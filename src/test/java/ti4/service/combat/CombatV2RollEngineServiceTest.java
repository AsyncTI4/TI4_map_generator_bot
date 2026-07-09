package ti4.service.combat;

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
import ti4.service.combat.CombatV2DiceData.AdditionalDiceBasis;
import ti4.service.combat.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.CombatV2DiceData.HitMatch;
import ti4.service.combat.CombatV2DiceData.HitRule;
import ti4.service.combat.CombatV2DiceData.RerollRule;
import ti4.service.combat.CombatV2DiceData.RerollSelector;
import ti4.service.combat.CombatV2DiceData.RollPlan;
import ti4.service.combat.CombatV2DiceData.RollResult;
import ti4.service.combat.CombatV2DiceData.RollSource;
import ti4.service.combat.CombatV2DiceData.UnitRollPlan;

class CombatV2RollEngineServiceTest {

    @Test
    void appliesModifiersAndSuccessBasedHitRules() {
        UnitRollPlan unit = unit(2, 1, 8, 1, List.of(new HitRule(HitMatch.SUCCESS, 0, 1)), List.of());
        try (MockedStatic<DiceHelper> ignored = mockDice(6, 7, 10)) {
            RollResult result = CombatV2RollEngineService.roll(new RollPlan(List.of(unit), 0));
            assertEquals(4, result.totalHits());
            assertEquals(1, result.totalMisses());
        }
    }

    @Test
    void commanderRerollIsDataNotEngineBranching() {
        RerollRule commander = new RerollRule(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES, 0, true);
        UnitRollPlan unit = unit(2, 0, 8, 0, List.of(), List.of(commander));
        try (MockedStatic<DiceHelper> ignored = mockDice(5, 8, 9)) {
            RollResult result = CombatV2RollEngineService.roll(new RollPlan(List.of(unit), 0));
            assertEquals(2, result.totalHits());
            assertEquals(0, result.totalMisses());
            assertEquals(2, result.units().getFirst().segments().size());
        }
    }

    @Test
    void rerollingHitsCanReplaceAResult() {
        RerollRule proxima = new RerollRule(RollSource.JOL_NAR_COMMANDER_HITS, RerollSelector.HITS, 0, true);
        UnitRollPlan unit = unit(1, 0, 8, 0, List.of(), List.of(proxima));
        try (MockedStatic<DiceHelper> ignored = mockDice(9, 2)) {
            RollResult result = CombatV2RollEngineService.roll(new RollPlan(List.of(unit), 0));
            assertEquals(0, result.totalHits());
            assertEquals(1, result.totalMisses());
        }
    }

    @Test
    void recursiveFollowUpDiceAreDataDriven() {
        AdditionalRollRule exploding = new AdditionalRollRule(
                RollSource.SIGMA_JOL_NAR_FLAGSHIP, AdditionalDiceBasis.HITS, HitMatch.SUCCESS, 0, 1, true);
        UnitRollPlan unit = new UnitRollPlan(
                "unit",
                "unit",
                "flagship",
                "Unit",
                "",
                "emoji",
                1,
                1,
                0,
                8,
                0,
                0,
                List.of(),
                List.of(exploding),
                List.of());
        try (MockedStatic<DiceHelper> ignored = mockDice(10, 9, 2)) {
            RollResult result = CombatV2RollEngineService.roll(new RollPlan(List.of(unit), 0));
            assertEquals(2, result.totalHits());
            assertEquals(3, result.units().getFirst().segments().size());
        }
    }

    private static UnitRollPlan unit(
            int dice, int extraDice, int hitsOn, int modifier, List<HitRule> hitRules, List<RerollRule> rerolls) {
        return new UnitRollPlan(
                "unit", "unit", "cruiser", "Unit", "", "emoji", 1, dice, extraDice, hitsOn, modifier, 0, hitRules,
                List.of(), rerolls);
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
