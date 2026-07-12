package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

class UnitRollBookkeepingTest extends BaseTi4Test {

    @Test
    void primaryRollCommitsBonusHitsWithoutCreatingNegativeMisses() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;

        double pipelineChanceBeforeDice = unit.context.chanceOfAllHits;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 8), DiceHelper.spoof(7, 9)));
        double groupProbability = roll.currentAllHitsProbability();
        roll.recordBonusHitOutcome(2, 2, 1.0);
        roll.commitPrimaryRollTotals(unit);

        assertEquals(4, unit.context.totalHits);
        assertEquals(0, unit.context.totalMisses);
        assertEquals(4, unit.context.maximumHits);
        assertEquals(8, fixture.player().getExpectedHitsTimes10());
        assertEquals(pipelineChanceBeforeDice * groupProbability, unit.context.chanceOfAllHits);
        assertTrue(unit.context.chanceOfAllMiss >= 0);
    }

    @Test
    void additionalDiceUpdateCountsProbabilitiesAndExpectedHitsExactlyOnce() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 10)));
        int expectedAfterPrimary = fixture.player().getExpectedHitsTimes10();
        double allHitsAfterPrimary = roll.currentAllHitsProbability();

        roll.recordAdditionalDice(unit, List.of(DiceHelper.spoof(7, 10), DiceHelper.spoof(7, 1)), 1);
        roll.commitPrimaryRollTotals(unit);

        assertEquals(3, roll.currentDiceCount());
        assertEquals(expectedAfterPrimary + 8, fixture.player().getExpectedHitsTimes10());
        assertTrue(roll.currentAllHitsProbability() < allHitsAfterPrimary);
        assertEquals(1, unit.context.totalMisses);
        assertEquals(2, unit.context.totalHits);
        assertEquals(3, unit.context.maximumHits);
    }

    @Test
    void primaryGroupsCommitDeltasWithoutCopyingPipelineTotalsIntoTheUnit() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;

        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 10)));
        roll.recordBonusHitOutcome(0, 1, 1.0);
        roll.commitPrimaryRollTotals(unit);
        double afterFirstSegment = unit.context.chanceOfAllHits;

        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 1)));
        roll.commitPrimaryRollTotals(unit);

        assertEquals(3, unit.context.maximumHits);
        assertEquals(1, unit.context.totalHits);
        assertEquals(1, unit.context.totalMisses);
        assertTrue(unit.context.chanceOfAllHits < afterFirstSegment);
    }

    @Test
    void rerollResultsAreLocalWhileHistoryRetainsEveryRerolledDie() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 1)));
        roll.commitPrimaryRollTotals(unit);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 2)) {
            UnitRollExecution.RerollResult first = roll.rollMisses(unit, 1);
            UnitRollExecution.RerollResult second = roll.rollReplacementDice(unit, 1);

            assertEquals(
                    List.of(10),
                    first.dice().stream().map(DiceHelper.Die::getResult).toList());
            assertEquals(1, first.hits());
            assertEquals(
                    List.of(2),
                    second.dice().stream().map(DiceHelper.Die::getResult).toList());
            assertEquals(0, second.hits());
            assertEquals(
                    List.of(10, 2),
                    roll.rerollDiceHistory().stream()
                            .map(DiceHelper.Die::getResult)
                            .toList());
            dice.assertExhausted();
        }
    }

    @Test
    void replacementRerollsDoNotIncreaseMaximumHitCapacity() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 1)));
        roll.commitPrimaryRollTotals(unit);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10)) {
            roll.rollMisses(unit, 1);

            assertEquals(1, unit.context.maximumHits);
            dice.assertExhausted();
        }
    }

    @Test
    void replacingMissesUpdatesDiceMissesAndBonusHitsAsOneOperation() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 1)));
        roll.recordBonusHitOutcome(0, 1, 1.0);
        roll.commitPrimaryRollTotals(unit);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10)) {
            UnitRollExecution.RerollResult reroll = roll.rollMisses(unit, roll.currentMisses());
            roll.replaceMissesWith(unit, reroll, 2);

            assertEquals(2, roll.currentHits());
            assertEquals(0, roll.currentMisses());
            assertEquals(2, unit.context.totalHits);
            assertEquals(0, unit.context.totalMisses);
            assertEquals(2, unit.context.maximumHits);
            assertEquals(
                    List.of(10),
                    roll.activeDice().stream().map(DiceHelper.Die::getResult).toList());
            dice.assertExhausted();
        }
    }

    @Test
    void replacingSelectedDiceUpdatesBothCurrentAndPipelineTotals() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        DiceHelper.Die miss = DiceHelper.spoof(7, 1);
        roll.recordPrimaryRoll(unit, List.of(miss, DiceHelper.spoof(7, 10)));
        roll.commitPrimaryRollTotals(unit);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(8)) {
            UnitRollExecution.RerollResult reroll = roll.rollReplacementDice(unit, 1);
            roll.replaceDiceWith(unit, List.of(miss), reroll, reroll.hits());

            assertEquals(2, roll.currentHits());
            assertEquals(0, roll.currentMisses());
            assertEquals(2, unit.context.totalHits);
            assertEquals(0, unit.context.totalMisses);
            assertEquals(
                    List.of(10, 8),
                    roll.activeDice().stream().map(DiceHelper.Die::getResult).toList());
            dice.assertExhausted();
        }
    }

    @Test
    void replacingHitsRemovesTheOriginalHitContributionBeforeAddingRerollHits() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 10), DiceHelper.spoof(7, 9)));
        roll.commitPrimaryRollTotals(unit);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1)) {
            UnitRollExecution.RerollResult reroll = roll.rollReplacementDice(unit, 2);
            roll.replaceHitsWith(unit, reroll, reroll.hits());

            assertEquals(1, roll.currentHits());
            assertEquals(1, roll.currentMisses());
            assertEquals(1, unit.context.totalHits);
            assertEquals(1, unit.context.totalMisses);
            assertEquals(
                    List.of(10, 1),
                    roll.activeDice().stream().map(DiceHelper.Die::getResult).toList());
            dice.assertExhausted();
        }
    }

    @Test
    void cascadingDiceRemainInCompletePrimaryDiceHistory() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        UnitRollExecution.UnitGroupRollState roll = fixture.roll();
        unit.toHit = 7;
        roll.modifierToHit = 0;
        roll.recordPrimaryRoll(unit, List.of(DiceHelper.spoof(7, 10)));

        roll.recordAdditionalDice(unit, List.of(DiceHelper.spoof(7, 6), DiceHelper.spoof(7, 1)), 0);

        assertEquals(
                List.of(10, 6, 1),
                roll.primaryDiceHistory().stream()
                        .map(DiceHelper.Die::getResult)
                        .toList());
    }

    private static Fixture fixture() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, player, UnitType.Cruiser, 1);
        harness.addToSpace(tile, opponent, UnitType.Carrier, 1);
        CombatContext combat =
                harness.preparedState(player, opponent, tile, tile.getSpaceUnitHolder(), CombatRollType.combatround);
        UnitRollExecution.CombatRollState pipeline = new UnitRollExecution.CombatRollState(combat);
        Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry =
                pipeline.workingUnits.entrySet().iterator().next();
        return new Fixture(
                player,
                new UnitRollExecution.UnitRollState(pipeline, entry),
                new UnitRollExecution.UnitGroupRollState());
    }

    private record Fixture(
            Player player, UnitRollExecution.UnitRollState unit, UnitRollExecution.UnitGroupRollState roll) {}
}
