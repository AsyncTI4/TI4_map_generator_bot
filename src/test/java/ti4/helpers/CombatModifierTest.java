package ti4.helpers;

import java.util.HashSet;
import java.util.Set;
import org.apache.commons.collections4.SetUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.model.TileModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

public class CombatModifierTest extends BaseTi4Test {

    private static Game testGame = null;
    private static Player neutral = null;
    private static Player winnu = null;
    private static Player bastion = null;
    private static Player letnev = null;

    @BeforeAll
    private static void setupTestGame() {
        if (testGame != null) return;
        testGame = new Game();
        testGame.newGameSetup();
        testGame.setName("Test Combat Modifiers");
        testGame.setCcNPlasticLimit(false);

        neutral = testGame.setupNeutralPlayer("gray");
        neutral.addPromissoryNoteToPlayArea("red_sftt"); // used for testing winnu

        winnu = setupPlayer("winnu");
        bastion = setupPlayer("bastion");
        letnev = setupPlayer("letnev");
    }

    @Test
    public void testWinnuCombatMods() {
        Tile mecatol = new Tile("112", getNextPosition());
        testGame.setTile(mecatol);
        mecatol.addUnit("space", Units.getUnitKey(UnitType.Flagship, winnu.getColorID()), 1);
        mecatol.addUnit("space", Units.getUnitKey(UnitType.Carrier, neutral.getColorID()), 1);

        Set<String> combatModifiers = getCombatMods(winnu, neutral, mecatol, "space", CombatRollType.combatround);
        Set<String> expectedModifiers = Set.of(
                "plus_2_mr_legendary_home_conditional",
                "roll_1_for_every_enemy_non_fighter",
                "plus1_per_opponent_sftt");

        assertListsEqual("WinnuMods", combatModifiers, expectedModifiers);
    }

    @Test
    public void testBastionCombatMods() {
        Tile t = new Tile("92", getNextPosition());
        testGame.setTile(t);
        t.addUnit("space", Units.getUnitKey(UnitType.Flagship, bastion.getColorID()), 1);
        t.addUnit("space", Units.getUnitKey(UnitType.Carrier, neutral.getColorID()), 1);
        t.addGalvanize("space", Units.getUnitKey(UnitType.Flagship, bastion.getColorID()), 1);
        testGame.setActivePlayerID(neutral.getUserID());

        Set<String> combatModifiers = getCombatMods(bastion, neutral, t, "space", CombatRollType.combatround);
        Set<String> expectedModifiers =
                Set.of("plus1_roll_in_nebula", "plus1_for_each_system_with_planets", "roll_1_for_galvanize_combat");

        assertListsEqual("BastionMods", combatModifiers, expectedModifiers);
    }

    @Test
    @Disabled("Doesn't work")
    public void testLetnevCombatMods() {
        Tile arcprime = new Tile("10", getNextPosition());
        testGame.setTile(arcprime);
        arcprime.addUnit("space", Units.getUnitKey(UnitType.Flagship, letnev.getColorID()), 1);
        arcprime.addUnit("space", Units.getUnitKey(UnitType.Destroyer, letnev.getColorID()), 1);

        arcprime.addUnit("space", Units.getUnitKey(UnitType.Destroyer, neutral.getColorID()), 1);

        Set<String> combatModifiers = getCombatMods(letnev, neutral, arcprime, "space", CombatRollType.combatround);
        Set<String> expectedModifiers = Set.of("plusX_letnev_breakthrough");

        assertListsEqual("LetnevMods", combatModifiers, expectedModifiers);
    }

    private static Set<String> getCombatMods(Player p1, Player p2, Tile tile, String uh, CombatRollType type) {
        TileModel model = tile.getTileModel();
        var unitsMap =
                CombatRollService.getUnitsInCombat(tile, tile.getUnitHolders().get(uh), p1, null, type, testGame);
        var oppUnits =
                CombatRollService.getUnitsInCombat(tile, tile.getUnitHolders().get(uh), p2, null, type, testGame);

        Set<String> modifiers = new HashSet<>();
        CombatModHelper.getModifiers(p1, p2, unitsMap, oppUnits, model, testGame, type, Constants.COMBAT_MODIFIERS)
                .forEach(mod -> modifiers.add(mod.getModifier().getAlias()));
        CombatModHelper.getModifiers(p1, p2, unitsMap, oppUnits, model, testGame, type, Constants.COMBAT_EXTRA_ROLLS)
                .forEach(mod -> modifiers.add(mod.getModifier().getAlias()));
        CombatModHelper.getModifiers(p1, p2, unitsMap, oppUnits, model, testGame, type, "bonus_hits")
                .forEach(mod -> modifiers.add(mod.getModifier().getAlias()));
        return modifiers;
    }

    private static void assertListsEqual(String name, Set<String> actual, Set<String> expected) {
        if (SetUtils.disjunction(actual, expected).size() > 0) {
            System.out.println(actual);
            System.out.println(expected);
        }
        for (String a : actual)
            Assertions.assertTrue(expected.contains(a), "Unexpected combat modifier for method [" + name + "]: " + a);
        for (String e : expected)
            Assertions.assertTrue(actual.contains(e), "Missing combat modifier for method [" + name + "]: " + e);
    }

    private static Player setupPlayer(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        ColorModel color = testGame.getUnusedColors().getFirst();
        var player = testGame.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(testGame, faction);
        player.setFactionEmoji("<" + faction + ">");
        player.setColor(color.getName());
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        player.setBreakthroughID(model.getBreakthrough());
        player.setBreakthroughUnlocked(true);
        player.setCommoditiesBase(model.getCommodities());
        player.setPlanets(model.getHomePlanets());
        player.setFactionTechs(model.getFactionTech());

        // unlock leaders
        for (Leader ll : player.getLeaders()) {
            ll.setLocked(false);
        }

        // techs: add all faction techs
        if (model.getStartingTech() != null) {
            player.setTechs(model.getStartingTech());
        }
        for (String tech : player.getFactionTechs()) {
            player.addTech(tech);
        }
        return player;
    }

    private static String getNextPosition() {
        for (String pos : PositionMapper.getTilePositions()) if (testGame.getTileByPosition(pos) == null) return pos;
        return null;
    }
}
