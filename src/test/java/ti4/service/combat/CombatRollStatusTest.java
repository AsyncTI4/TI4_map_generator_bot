package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class CombatRollStatusTest extends BaseTi4Test {

    @Test
    void missingHolderReturnsInvalidLocationWithoutRolling() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Tile tile = harness.tile("19");
        CombatRollTestSupport.StoppedRollSnapshot snapshot = CombatRollTestSupport.stoppedSnapshot(player, tile);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice();
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    player, harness.game, harness.event, tile, "missing", CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).stopped(CombatRollStatus.INVALID_LOCATION);
            assertEquals(0, dice.rolledDice());
            snapshot.assertUnchanged(tile);
            CombatRollTestSupport.assertNoButtonsSent(messages);
            dice.assertExhausted();
        }
    }

    @Test
    void spaceCannonDefenceAgainstSpaceReturnsInvalidLocationWithoutRolling() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Tile tile = harness.tile("19");
        CombatRollTestSupport.StoppedRollSnapshot snapshot = CombatRollTestSupport.stoppedSnapshot(player, tile);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice();
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    player,
                    harness.game,
                    harness.event,
                    tile,
                    Constants.SPACE,
                    CombatRollType.SpaceCannonDefence,
                    false);

            CombatRollTestSupport.assertThat(result).stopped(CombatRollStatus.INVALID_LOCATION);
            assertEquals(0, dice.rolledDice());
            snapshot.assertUnchanged(tile);
            CombatRollTestSupport.assertNoButtonsSent(messages);
        }
    }

    @Test
    void realEmptyUnitSelectionReturnsNoEligibleUnitsWithoutRolling() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        harness.player("mentak");
        Tile tile = harness.tile("19");
        CombatRollTestSupport.StoppedRollSnapshot snapshot = CombatRollTestSupport.stoppedSnapshot(player, tile);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice();
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    player, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).stopped(CombatRollStatus.NO_ELIGIBLE_UNITS);
            assertEquals(0, dice.rolledDice());
            snapshot.assertUnchanged(tile);
            CombatRollTestSupport.assertNoButtonsSent(messages);
        }
    }

    @Test
    void netrunnerFlagshipEmpBlocksRealSpaceCannonPayloadWithoutRolling() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player shooter = harness.player("sol");
        Player netrunners = harness.player("netrunners");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, shooter, UnitType.Pds, 1);
        harness.addToSpace(tile, netrunners, UnitType.Flagship, 1);
        CombatRollTestSupport.StoppedRollSnapshot snapshot = CombatRollTestSupport.stoppedSnapshot(shooter, tile);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice();
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    shooter,
                    harness.game,
                    harness.event,
                    tile,
                    Constants.SPACE,
                    CombatRollType.SpaceCannonOffence,
                    false);

            CombatRollTestSupport.assertThat(result).stopped(CombatRollStatus.BLOCKED);
            assertEquals(0, dice.rolledDice());
            snapshot.assertUnchanged(tile);
            CombatRollTestSupport.assertNoButtonsSent(messages);
        }
    }
}
