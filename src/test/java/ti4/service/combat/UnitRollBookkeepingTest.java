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
        unit.toHit = 7;
        unit.modifierToHit = 0;

        double pipelineChanceBeforeDice = unit.pipeline.chanceOfAllHits;
        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 8), DiceHelper.spoof(7, 9)));
        double groupProbability = unit.groupAllHitsProbability;
        unit.addHits(2);
        unit.addMaximumHits(2);
        unit.commitPrimaryRollTotals();

        assertEquals(4, unit.pipeline.totalHits);
        assertEquals(0, unit.pipeline.totalMisses);
        assertEquals(4, unit.pipeline.maximumHits);
        assertEquals(8, fixture.player().getExpectedHitsTimes10());
        assertEquals(pipelineChanceBeforeDice * groupProbability, unit.pipeline.chanceOfAllHits);
        assertTrue(unit.pipeline.chanceOfAllMiss >= 0);
    }

    @Test
    void additionalDiceUpdateCountsProbabilitiesAndExpectedHitsExactlyOnce() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        unit.toHit = 7;
        unit.modifierToHit = 0;
        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 10)));
        int expectedAfterPrimary = fixture.player().getExpectedHitsTimes10();
        double allHitsAfterPrimary = unit.groupAllHitsProbability;

        unit.recordAdditionalDice(List.of(DiceHelper.spoof(7, 10), DiceHelper.spoof(7, 1)));
        unit.addHits(1);
        unit.commitPrimaryRollTotals();

        assertEquals(3, unit.numRolls);
        assertEquals(expectedAfterPrimary + 8, fixture.player().getExpectedHitsTimes10());
        assertTrue(unit.groupAllHitsProbability < allHitsAfterPrimary);
        assertEquals(1, unit.pipeline.totalMisses);
        assertEquals(2, unit.pipeline.totalHits);
        assertEquals(3, unit.pipeline.maximumHits);
    }

    @Test
    void primaryGroupsCommitDeltasWithoutCopyingPipelineTotalsIntoTheUnit() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        unit.toHit = 7;
        unit.modifierToHit = 0;

        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 10)));
        unit.addMaximumHits(1);
        unit.commitPrimaryRollTotals();
        double afterFirstSegment = unit.pipeline.chanceOfAllHits;

        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 1)));
        unit.commitPrimaryRollTotals();

        assertEquals(3, unit.pipeline.maximumHits);
        assertEquals(1, unit.pipeline.totalHits);
        assertEquals(1, unit.pipeline.totalMisses);
        assertTrue(unit.pipeline.chanceOfAllHits < afterFirstSegment);
    }

    @Test
    void rerollResultsAreLocalWhileHistoryRetainsEveryRerolledDie() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        unit.toHit = 7;
        unit.modifierToHit = 0;
        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 1)));
        unit.commitPrimaryRollTotals();

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 2)) {
            UnitRollExecution.RerollResult first = unit.rollMisses(1);
            UnitRollExecution.RerollResult second = unit.rollReplacementDice(1);

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
                    unit.rerollDiceHistory.stream()
                            .map(DiceHelper.Die::getResult)
                            .toList());
            dice.assertExhausted();
        }
    }

    @Test
    void replacementRerollsDoNotIncreaseMaximumHitCapacity() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        unit.toHit = 7;
        unit.modifierToHit = 0;
        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 1)));
        unit.commitPrimaryRollTotals();

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10)) {
            unit.rollMisses(1);

            assertEquals(1, unit.pipeline.maximumHits);
            dice.assertExhausted();
        }
    }

    @Test
    void cascadingDiceRemainInCompletePrimaryDiceHistory() {
        Fixture fixture = fixture();
        UnitRollExecution.UnitRollState unit = fixture.unit();
        unit.toHit = 7;
        unit.modifierToHit = 0;
        unit.recordPrimaryRoll(List.of(DiceHelper.spoof(7, 10)));

        unit.recordAdditionalDice(List.of(DiceHelper.spoof(7, 6), DiceHelper.spoof(7, 1)));

        assertEquals(
                List.of(10, 6, 1),
                unit.primaryDiceHistory.stream().map(DiceHelper.Die::getResult).toList());
    }

    private static Fixture fixture() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, player, UnitType.Cruiser, 1);
        harness.addToSpace(tile, opponent, UnitType.Carrier, 1);
        CombatRollPipelineState combat =
                harness.preparedState(player, opponent, tile, tile.getSpaceUnitHolder(), CombatRollType.combatround);
        UnitRollExecution.UnitRollPipelineState pipeline = new UnitRollExecution.UnitRollPipelineState(combat);
        Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry =
                pipeline.playerUnits.entrySet().iterator().next();
        return new Fixture(player, new UnitRollExecution.UnitRollState(pipeline, entry));
    }

    private record Fixture(Player player, UnitRollExecution.UnitRollState unit) {}
}
