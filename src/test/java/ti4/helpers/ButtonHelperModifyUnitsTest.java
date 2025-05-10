package ti4.helpers;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ButtonHelperModifyUnitsTest extends BaseTi4Test {
    private final Game game = new Game();
    private final Tile tile = new Tile("tile 1", null, null, null, null);

    @Test
    void testAutoAssignSpaceCombatHits_SpaceCombat_DuraniumArmor() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, true, true);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_NoUnitDamage() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, true, false);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_NoUnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = new UnitKey(Units.UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = new UnitKey(Units.UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_AutoAssignNoneToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_AutoAssignSomeToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_AutoAssignAllDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = new UnitKey(Units.UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = new UnitKey(Units.UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Disabled("Need a way to mock emoji's to prove actual message string")
    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_DuraniumPreference() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = new UnitKey(Units.UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = new UnitKey(Units.UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_NoUnitDamage() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false, false);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_NoUnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = new UnitKey(Units.UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = new UnitKey(Units.UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertFalse(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_AutoAssignNoneToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_AutoAssignSomeToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_AutoAssignAllDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = new UnitKey(Units.UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertFalse(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = new UnitKey(Units.UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = new UnitKey(Units.UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);
        assertTrue(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_DuraniumPreference() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = new UnitKey(Units.UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = new UnitKey(Units.UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
        assertEquals(1, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(warsunUnitKey));
        assertEquals(2, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
        assertEquals(2, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(flagshipUnitKey));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_SameShipTargeting() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
        assertEquals(1, tile.getUnitHolders().get(Constants.SPACE).getUnitCount(dreadnoughtUnitKey));
        assertEquals(0, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_DestroyedShips() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertFalse(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
        assertEquals(0, tile.getUnitHolders().get(Constants.SPACE).getUnitCount(dreadnoughtUnitKey));
        assertEquals(0, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_ExtraSameShipTargeting() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");

        UnitKey dreadnoughtUnitKey = new UnitKey(Units.UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 4);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 4, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <normalEmoji> due to _Duranium Armor_"));
        assertEquals(3, tile.getUnitHolders().get(Constants.SPACE).getUnitCount(dreadnoughtUnitKey));
        assertEquals(2, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
    }

    private static Player createPlayerWithDuraniumArmor(Game game, String color) {
        Player player = new Player("101", "testUser", game);
        player.setFactionEmoji("a");
        player.setTechs(List.of("da"));
        player.setColor(color);
        return player;
    }
}