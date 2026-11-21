package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.combat.sustain.HitSource;
import ti4.service.combat.sustain.SustainContext;
import ti4.service.combat.sustain.SustainEligibility;
import ti4.testUtils.BaseTi4Test;

class SustainDamageAssignmentTest extends BaseTi4Test {

    private static final String POSITION = "101";
    private static final String PLANET = "wellon";

    private final Game game = new Game();
    private final Tile tile = new Tile("19", POSITION);

    SustainDamageAssignmentTest() {
        game.setTile(tile);
    }

    // Publicize Weapon Schematics: war suns lose SUSTAIN DAMAGE. The late "remaining sustains" pass, which runs when
    // the opponent could Direct Hit, used to sustain the war sun anyway.
    @Test
    void schematicsWarSunDoesNotSustainInLatePassWhenOpponentCanDirectHit() {
        Player player = addPlayer("red", "sol");
        player.addOwnedUnitByID("warsun");
        player.addOwnedUnitByID("dreadnought");
        UnitKey warSun = Units.getUnitKey(UnitType.Warsun, "red");
        UnitKey dreadnought = Units.getUnitKey(UnitType.Dreadnought, "red");
        tile.addUnit(Constants.SPACE, warSun, 1);
        tile.addUnit(Constants.SPACE, dreadnought, 1);
        Player opponent = addOpponentWithDestroyer("blue", "hacan");
        opponent.setActionCard("sabo1");
        game.addLaw("schematics", null);

        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 2, null, false);

        UnitHolder space = tile.getSpaceUnitHolder();
        assertEquals(0, space.getDamagedUnitCount(warSun));
        assertEquals(1, space.getUnitCount(warSun));
        assertEquals(0, space.getUnitCount(dreadnought));
    }

    @Test
    void dryRunLeavesStoredValuesUnchanged() {
        Player player = addPlayer("red", "sol");
        player.addOwnedUnitByID("dreadnought");
        player.addOwnedUnitByID("flagship");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Dreadnought, "red"), 2);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, "red"), 1);
        Player opponent = addOpponentWithDestroyer("blue", "hacan");
        game.setStoredValue(opponent.getFaction() + "graviton", "yes");
        Map<String, String> before = new HashMap<>(game.getStoredValueMap());

        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, false);
        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 3, null, true, true);

        assertEquals(before, game.getStoredValueMap());
        assertEquals("", game.getStoredValue("stuffNotToSustainFor" + player.getFaction()));
    }

    // The Cavalry (Nomad promissory note) is its own unit type, so it was missing from the kill order and leftover
    // hits vanished.
    @Test
    void cavalryTakesTheHitWhenItIsTheOnlyShip() {
        Player player = addPlayer("red", "sol");
        player.addOwnedUnitByID("cavalry1");
        UnitKey cavalry = Units.getUnitKey(UnitType.Cavalry, "red");
        tile.addUnit(Constants.SPACE, cavalry, 1);
        tile.addUnitDamage(Constants.SPACE, cavalry, 1);

        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, 1, null, false);

        assertEquals(0, tile.getSpaceUnitHolder().getUnitCount(cavalry));
    }

    @Test
    void shipsMissingFromHitOrderAreDestroyedCheapestFirst() {
        Player player = addPlayer("red", "sol");
        for (String unit : List.of("fighter", "destroyer", "cruiser", "dreadnought", "infantry")) {
            player.addOwnedUnitByID(unit);
        }
        Map<UnitKey, Integer> units = new HashMap<>();
        units.put(Units.getUnitKey(UnitType.Dreadnought, "red"), 1);
        units.put(Units.getUnitKey(UnitType.Cruiser, "red"), 2);
        units.put(Units.getUnitKey(UnitType.Destroyer, "red"), 1);
        units.put(Units.getUnitKey(UnitType.Fighter, "red"), 3);
        units.put(Units.getUnitKey(UnitType.Infantry, "red"), 2);
        units.put(Units.getUnitKey(UnitType.Cruiser, "blue"), 1);

        List<UnitKey> fallback = ButtonHelperModifyUnits.shipsOutsideHitOrder(player, units, List.of("fighter"));

        assertEquals(
                List.of(
                        Units.getUnitKey(UnitType.Destroyer, "red"),
                        Units.getUnitKey(UnitType.Cruiser, "red"),
                        Units.getUnitKey(UnitType.Dreadnought, "red")),
                fallback);
    }

    // Magen Defense Grid / Ruthless buttons asked whether the hitter's units could sustain, not the owner's.
    @Test
    void opposingGroundHitButtonsUseTheOwnersSustain() {
        Player hitter = addPlayer("red", "sol");
        hitter.addRelic("metalivoidshielding");
        Player owner = addPlayer("blue", "hacan");
        owner.addOwnedUnitByID("pds");
        tile.addUnit(PLANET, Units.getUnitKey(UnitType.Pds, "blue"), 1);

        List<Button> buttons =
                ButtonHelperModifyUnits.getOpposingUnitsToHitOnGround(hitter, game, tile, PLANET, "magen");

        assertEquals(1, buttons.size());
        assertTrue(
                buttons.getFirst().getLabel().startsWith("Destroy 1 "),
                buttons.getFirst().getLabel());
    }

    // LRR 10.2: anti-fighter barrage hits are assigned to ships in the space area; units on planets are never hit.
    @Test
    void antiFighterBarrageButtonsSkipPlanetsAndGroundForces() {
        Player player = addPlayer("red", "sol");
        for (String unit : List.of("fighter", "dreadnought", "mech", "infantry")) {
            player.addOwnedUnitByID(unit);
        }
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, "red"), 2);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Dreadnought, "red"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Infantry, "red"), 1);
        tile.addUnit(PLANET, Units.getUnitKey(UnitType.Mech, "red"), 1);

        List<String> ids = buttonIds(ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, "afb"));

        assertTrue(ids.stream().noneMatch(id -> id.contains(PLANET)), ids.toString());
        assertTrue(ids.stream().noneMatch(id -> id.contains("_gf_") || id.contains("_mf_")), ids.toString());
        assertTrue(ids.stream().anyMatch(id -> id.contains("assignHits_" + POSITION + "_1_ff")), ids.toString());
    }

    // Quantum Manipulator: may SUSTAIN DAMAGE to cancel any combat hit against the Nomad player's ships, including
    // anti-fighter barrage hits (Nomad ruling II), but it is not a ship, so it cannot be assigned the hit itself.
    @Test
    void antiFighterBarrageButtonsLetQuantumManipulatorInSpaceSustainOnly() {
        Player player = addPlayer("red", "nomad");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("nomad_mech");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, "red"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Mech, "red"), 1);

        List<String> ids = buttonIds(ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, "afb"));

        assertTrue(ids.stream().anyMatch(id -> id.contains("assignDamage_" + POSITION + "_1_mf")), ids.toString());
        assertTrue(ids.stream().noneMatch(id -> id.contains("assignHits_" + POSITION + "_1_mf")), ids.toString());
    }

    @Test
    void antiFighterBarrageButtonsDropQuantumManipulatorUnderArticlesOfWar() {
        Player player = addPlayer("red", "nomad");
        player.addOwnedUnitByID("fighter");
        player.addOwnedUnitByID("nomad_mech");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, "red"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Mech, "red"), 1);
        game.addLaw("articles_war", null);

        List<String> ids = buttonIds(ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, "afb"));

        assertTrue(ids.stream().noneMatch(id -> id.contains("_mf_")), ids.toString());
    }

    // The Alastor II: ground forces participate "as though they were ships", so a mech with SUSTAIN DAMAGE in the
    // system can use it during space combat. Participating does not hand out the ability: Z-Grav Eidolon has none.
    @Test
    void nekroAlastorTwoLetsParticipatingMechSustainButGrantsNoAbility() {
        Player player = addPlayer("red", "nekro");
        player.addOwnedUnitByID("sigma_nekro_flagship_2");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, "red"), 1);
        SustainContext spaceCombat = SustainContext.of(game, player, tile, null, HitSource.SPACE_COMBAT);

        assertTrue(SustainEligibility.canSustain(spaceCombat, Mapper.getUnit("mech")));
        assertFalse(SustainEligibility.canSustain(spaceCombat, Mapper.getUnit("naaz_mech_space")));
    }

    // The Alastor I: ground forces in the space area participate "as though they were fighters". They keep their own
    // SUSTAIN DAMAGE, but fighters have none to lend, so Z-Grav Eidolon still cannot sustain.
    @Test
    void nekroAlastorOneKeepsPrintedSustainButGrantsNoAbility() {
        Player player = addPlayer("red", "nekro");
        player.addOwnedUnitByID("sigma_nekro_flagship_1");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, "red"), 1);
        SustainContext spaceCombat = SustainContext.of(game, player, tile, null, HitSource.SPACE_COMBAT);

        assertTrue(SustainEligibility.canSustain(spaceCombat, Mapper.getUnit("mech")));
        assertFalse(SustainEligibility.canSustain(spaceCombat, Mapper.getUnit("naaz_mech_space")));
    }

    // With laws disabled, Articles of War is not in effect, so the Mentak mech still stops other players' ground
    // forces on its planet from using SUSTAIN DAMAGE.
    @Test
    void disabledArticlesOfWarDoesNotSwitchOffMentakMech() {
        Player player = addPlayer("red", "sol");
        player.addOwnedUnitByID("mech");
        player.addOwnedUnitByID("infantry");
        Player mentak = addPlayer("blue", "mentak");
        mentak.addOwnedUnitByID("mentak_mech");
        UnitKey mech = Units.getUnitKey(UnitType.Mech, "red");
        tile.addUnit(PLANET, mech, 1);
        tile.addUnit(PLANET, Units.getUnitKey(UnitType.Mech, "blue"), 1);
        game.addLaw("articles_war", null);
        game.setStoredValue("lawsDisabled", "yes");

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, PLANET, 1, event);
        }

        UnitHolder planet = tile.getUnitHolders().get(PLANET);
        assertEquals(0, planet.getUnitCount(mech));
    }

    private Player addPlayer(String color, String faction) {
        Player player = game.addPlayer(color + "Id", color + "User");
        player.setFaction(faction);
        player.setColor(color);
        player.setFactionEmoji("a");
        return player;
    }

    private Player addOpponentWithDestroyer(String color, String faction) {
        Player opponent = addPlayer(color, faction);
        opponent.addOwnedUnitByID("destroyer");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, color), 1);
        return opponent;
    }

    private static List<String> buttonIds(List<Button> buttons) {
        return buttons.stream().map(Button::getCustomId).toList();
    }
}
