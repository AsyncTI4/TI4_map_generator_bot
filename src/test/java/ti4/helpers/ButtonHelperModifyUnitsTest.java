package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ButtonHelperModifyUnitsTest extends BaseTi4Test {
    private final Game game = new Game();
    private final Tile tile = new Tile("tile 1", null, null, null, null);

    @Test
    void testAutoAssignSpaceCombatHits_SpaceCombat_DuraniumArmor() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, true, true);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_NoUnitDamage() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, true, false);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_NoUnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = Units.getUnitKey(UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertFalse(actualMessage.contains("Would repair 1 <normalEmoji> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_AutoAssignNoneToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_AutoAssignSomeToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_AutoAssignAllDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertFalse(actualMessage.contains("Would repair 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = Units.getUnitKey(UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <warsun> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_DuraniumArmor_UnitDamage_DuraniumPreference() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = Units.getUnitKey(UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);

        assertTrue(actualMessage.contains("Would repair 1 <warsun> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_NoUnitDamage() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false, false);

        assertFalse(actualMessage.contains("Would repair 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_NoUnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = Units.getUnitKey(UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertFalse(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_AutoAssignNoneToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_AutoAssignSomeToDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_AutoAssignAllDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("dreadnought");

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 1);

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 2);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertFalse(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_MultipleTypesOfDamagedUnits() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = Units.getUnitKey(UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);
        assertTrue(actualMessage.contains("Repaired 1 <warsun> due to _Duranium Armor_"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_DuraniumPreference() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        UnitKey warsunUnitKey = Units.getUnitKey(UnitType.Warsun, "red");
        tile.addUnit(Constants.SPACE, warsunUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, warsunUnitKey, 1);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <warsun> due to _Duranium Armor_"));
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

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
        assertEquals(1, tile.getUnitHolders().get(Constants.SPACE).getUnitCount(dreadnoughtUnitKey));
        assertEquals(0, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_DestroyedShips() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        player.addOwnedUnitByID("warsun");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 2);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, false, false);

        assertFalse(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
        assertEquals(0, tile.getUnitHolders().get(Constants.SPACE).getUnitCount(dreadnoughtUnitKey));
        assertEquals(0, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
    }

    @Test
    void testAutoAssignSpaceCombatHits_DuraniumArmor_UnitDamage_ExtraSameShipTargeting() {
        Player player = createPlayerWithDuraniumArmor(game, "red");
        player.addOwnedUnitByID("dreadnought");

        UnitKey dreadnoughtUnitKey = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, dreadnoughtUnitKey, 4);
        tile.addUnitDamage(Constants.SPACE, dreadnoughtUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 4, null, false, false);

        assertTrue(actualMessage.contains("Repaired 1 <dreadnought> due to _Duranium Armor_"));
        assertEquals(3, tile.getUnitHolders().get(Constants.SPACE).getUnitCount(dreadnoughtUnitKey));
        assertEquals(2, tile.getUnitHolders().get(Constants.SPACE).getDamagedUnitCount(dreadnoughtUnitKey));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_NeutralOpponent_SustainsFlagshipInsteadOfLosingFighters() {
        Player player = createPlayer(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("flagship");

        Player neutral = game.setupNeutralPlayer("blue");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, neutral.getColor()), 1);

        UnitKey fighterUnitKey = Units.getUnitKey(UnitType.Fighter, "red");
        tile.addUnit(Constants.SPACE, fighterUnitKey, 3);

        UnitKey flagshipUnitKey = Units.getUnitKey(UnitType.Flagship, "red");
        tile.addUnit(Constants.SPACE, flagshipUnitKey, 1);

        String actualMessage =
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, true, false);

        assertTrue(actualMessage.contains("Would sustain 1 <flagship>"));
        assertFalse(actualMessage.contains("Would destroy"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_SpaceCannonOffence_AdjacentShooterWithNoActionCards() {
        Game scoGame = new Game();
        Player player = createSpaceCannonTarget(scoGame);
        createAdjacentPds2Shooter(scoGame);

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                player, scoGame, scoGame.getTileByPosition("101"), 1, null, true, true);

        assertTrue(actualMessage.contains("Would sustain 1 <flagship>"));
        assertFalse(actualMessage.contains("Would destroy"));
    }

    @Test
    void testAutoAssignSpaceCombatHits_Summarizing_SpaceCannonOffence_AdjacentShooterHoldingActionCards() {
        Game scoGame = new Game();
        Player player = createSpaceCannonTarget(scoGame);
        Player shooter = createAdjacentPds2Shooter(scoGame);
        shooter.setActionCard("sabo1");

        String actualMessage = ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                player, scoGame, scoGame.getTileByPosition("101"), 1, null, true, true);

        assertFalse(actualMessage.contains("Would sustain"));
        assertTrue(actualMessage.contains("Would destroy 1 <fighter>"));
    }

    private static Player createSpaceCannonTarget(Game game) {
        Player player = createPlayer(game, "red");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("flagship");

        Tile combatTile = new Tile("19", "101");
        game.setTile(combatTile);
        combatTile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, "red"), 3);
        combatTile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, "red"), 1);
        return player;
    }

    private static Player createAdjacentPds2Shooter(Game game) {
        Player shooter = game.addPlayer("202", "shooterUser");
        shooter.setFaction("sol");
        shooter.setColor("blue");
        shooter.addOwnedUnitByID("pds2");

        Tile pdsTile = new Tile("20", "102");
        game.setTile(pdsTile);
        pdsTile.addUnit("vefutii", Units.getUnitKey(UnitType.Pds, "blue"), 1);
        return shooter;
    }

    private static Player createPlayer(Game game, String color) {
        Player player = new Player("101", "testUser", game);
        player.setFactionEmoji("a");
        player.setColor(color);
        return player;
    }

    private static Player createPlayerWithDuraniumArmor(Game game, String color) {
        Player player = new Player("101", "testUser", game);
        player.setFactionEmoji("a");
        player.setTechs(List.of("da"));
        player.setColor(color);
        return player;
    }
}
