package ti4.service.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.FactionModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatStatsServiceTest extends BaseTi4Test {

    private static Game testGame;
    private static Player neutral;
    private static Player bluetf;
    private static Player winnu;

    @BeforeAll
    static void setupTestGame() {
        if (testGame != null) return;
        testGame = new Game();
        testGame.newGameSetup();
        testGame.setName("Test Combat Stats");
        testGame.setCcNPlasticLimit(false);

        neutral = testGame.setupNeutralPlayer("gray");
        bluetf = setupPlayer("bluetf");
        winnu = setupPlayer("winnu");
    }

    @Test
    void baseWinnuScalingIsDisplayOnlyForCombatProfiles() {
        Tile tile = new Tile("112", getNextPosition());
        testGame.setTile(tile);
        tile.addUnit("space", Units.getUnitKey(UnitType.Flagship, winnu.getColorID()), 1);
        tile.addUnit("space", Units.getUnitKey(UnitType.Carrier, neutral.getColorID()), 1);
        tile.addUnit("space", Units.getUnitKey(UnitType.Destroyer, neutral.getColorID()), 2);

        TileModel tileModel = tile.getTileModel();
        Map<UnitModel, Integer> winnuUnits = CombatRollService.getUnitsInCombat(
                tile, tile.getUnitHolders().get("space"), winnu, null, CombatRollType.combatround, testGame);
        Map<UnitModel, Integer> neutralUnits = CombatRollService.getUnitsInCombat(
                tile, tile.getUnitHolders().get("space"), neutral, null, CombatRollType.combatround, testGame);
        UnitModel winnuFlagship = winnuUnits.keySet().stream()
                .filter(unit -> "winnu_flagship".equals(unit.getId()))
                .findFirst()
                .orElseThrow();

        CombatStatsService.CombatRoundProfile displayProfile =
                CombatStatsService.getCombatRoundProfile(true, winnuFlagship, winnu, tile, neutral, true);
        CombatStatsService.CombatRoundProfile rollProfile =
                CombatStatsService.getCombatRoundProfile(true, winnuFlagship, winnu, tile, neutral, false);

        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                winnu,
                neutral,
                winnuUnits,
                neutralUnits,
                tileModel,
                testGame,
                CombatRollType.combatround,
                Constants.COMBAT_EXTRA_ROLLS);
        int extraRollCount = CombatModHelper.getCombinedModifierForUnit(
                winnuFlagship,
                1,
                extraRolls,
                winnu,
                neutral,
                testGame,
                new ArrayList<>(winnuUnits.keySet()),
                CombatRollType.combatround,
                tile,
                tile.getUnitHolders().get("space"));

        Assertions.assertEquals(3, displayProfile.diceCount());
        Assertions.assertEquals(0, rollProfile.diceCount());
        Assertions.assertEquals(3, extraRollCount);
    }

    @Test
    void echoOfAscensionDoesNotDoubleApplyCombatThreshold() {
        Tile tile = new Tile("112", getNextPosition());
        testGame.setTile(tile);

        bluetf.addOwnedUnitByID("tf-echoofascension");
        try {
            tile.addUnit("space", Units.getUnitKey(UnitType.Flagship, bluetf.getColorID()), 1);
            tile.addUnit("space", Units.getUnitKey(UnitType.Mech, bluetf.getColorID()), 4);
            tile.addUnit("space", Units.getUnitKey(UnitType.Carrier, neutral.getColorID()), 1);

            Map<UnitModel, Integer> bluetfUnits = CombatRollService.getUnitsInCombat(
                    tile, tile.getUnitHolders().get("space"), bluetf, null, CombatRollType.combatround, testGame);
            Map<UnitModel, Integer> neutralUnits = CombatRollService.getUnitsInCombat(
                    tile, tile.getUnitHolders().get("space"), neutral, null, CombatRollType.combatround, testGame);

            String rollOutput = getCombatRollOutput(tile, bluetfUnits, neutralUnits);

            Assertions.assertTrue(rollOutput.contains("Tizona 2 rolls (+4 rolls), hits on **2**"));
            Assertions.assertFalse(rollOutput.contains("always hits (+1 mods)"));
        } finally {
            bluetf.removeOwnedUnitByID("tf-echoofascension");
        }
    }

    @Test
    void superchargeOnlyAppliesItsOwnModifierOnTopOfIntrinsicCombatStats() {
        Tile tile = new Tile("112", getNextPosition());
        testGame.setTile(tile);

        bluetf.addOwnedUnitByID("tf-echoofascension");
        bluetf.addTech("tf-supercharge");
        try {
            tile.addUnit("space", Units.getUnitKey(UnitType.Flagship, bluetf.getColorID()), 1);
            tile.addUnit("space", Units.getUnitKey(UnitType.Mech, bluetf.getColorID()), 4);
            tile.addUnit("space", Units.getUnitKey(UnitType.Carrier, neutral.getColorID()), 1);

            Map<UnitModel, Integer> bluetfUnits = CombatRollService.getUnitsInCombat(
                    tile, tile.getUnitHolders().get("space"), bluetf, null, CombatRollType.combatround, testGame);
            Map<UnitModel, Integer> neutralUnits = CombatRollService.getUnitsInCombat(
                    tile, tile.getUnitHolders().get("space"), neutral, null, CombatRollType.combatround, testGame);

            String rollOutput = getCombatRollOutput(tile, bluetfUnits, neutralUnits);

            Assertions.assertTrue(rollOutput.contains("Applied +2 to the rolls of 1 unit with _Supercharge_."));
            Assertions.assertTrue(rollOutput.contains("Tizona 2 rolls (+4 rolls), always hits (+2 mods)"));
            Assertions.assertFalse(rollOutput.contains("always hits (+3 mods)"));
        } finally {
            bluetf.removeOwnedUnitByID("tf-echoofascension");
            bluetf.removeTech("tf-supercharge");
        }
    }

    private static String getCombatRollOutput(
            Tile tile, Map<UnitModel, Integer> playerUnits, Map<UnitModel, Integer> opponentUnits) {
        TileModel tileModel = tile.getTileModel();
        List<NamedCombatModifierModel> modifiers = CombatModHelper.getModifiers(
                bluetf,
                neutral,
                playerUnits,
                opponentUnits,
                tileModel,
                testGame,
                CombatRollType.combatround,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                bluetf,
                neutral,
                playerUnits,
                opponentUnits,
                tileModel,
                testGame,
                CombatRollType.combatround,
                Constants.COMBAT_EXTRA_ROLLS);

        return CombatRollService.rollForUnits(
                playerUnits,
                extraRolls,
                modifiers,
                List.of(),
                bluetf,
                neutral,
                testGame,
                CombatRollType.combatround,
                null,
                tile,
                tile.getUnitHolders().get("space"));
    }

    private static Player setupPlayer(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        var player = testGame.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(testGame, faction);
        player.setFactionEmoji("<" + faction + ">");
        player.setColor(PlayerColorService.getPreferredColor(player));
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        player.addBreakthrough(model.getBreakthrough());
        player.setBreakthroughUnlocked(model.getBreakthrough(), true);
        player.setCommoditiesBase(model.getCommodities());
        player.setPlanets(model.getHomePlanets());
        player.setFactionTechs(model.getFactionTech());

        for (Leader leader : player.getLeaders()) {
            leader.setLocked(false);
        }

        if (model.getStartingTech() != null) {
            player.setTechs(model.getStartingTech());
        }
        for (String tech : player.getFactionTechs()) {
            player.addTech(tech);
        }
        return player;
    }

    private static String getNextPosition() {
        for (String pos : PositionMapper.getTilePositions()) {
            if (testGame.getTileByPosition(pos) == null) return pos;
        }
        return null;
    }
}
