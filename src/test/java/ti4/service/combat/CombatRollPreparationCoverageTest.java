package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.ObjectMapper;

class CombatRollPreparationCoverageTest extends BaseTi4Test {

    @Test
    void specialAfbSourcesAreAddedToTheRealPipelineState() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Tile tile = harness.tile("19");
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        player.addRelic("metalivoidarmaments");
        player.addTech("tf-projectionofpow");
        CombatContext state = state(harness, player, tile, space, CombatRollType.AFB);

        CombatRollPreparation.addMetaliVoidArmamentsUnit(state);
        CombatRollPreparation.addProjectionOfPowerTechUnit(state);

        assertEquals(2, state.playerUnits.size());
        assertTrue(state.playerUnits.keySet().stream()
                .map(pair -> pair.getLeft().getId())
                .anyMatch("MetaliAFB"::equals));
        assertTrue(state.playerUnits.keySet().stream()
                .map(pair -> pair.getLeft().getId())
                .anyMatch(id -> id.contains("projection")));
    }

    @Test
    void projectionOfPowerRequiresRealSpaceDockAdjacency() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("dihmohn");
        player.addAbility("projection_of_power");
        Tile target = harness.tile("19");
        UnitHolder targetSpace = target.getUnitHolders().get(Constants.SPACE);
        CombatContext state = state(harness, player, target, targetSpace, CombatRollType.combatround);

        CombatRollPreparation.addProjectionOfPowerAbilityUnit(state);
        assertTrue(state.playerUnits.isEmpty());

        String adjacentPosition = ti4.image.PositionMapper.getAdjacentTilePositions(target.getPosition()).stream()
                .filter(position -> position != null && !"-1".equals(position))
                .findFirst()
                .orElseThrow();
        Tile dockSystem = harness.tileAt("20", adjacentPosition);
        UnitHolder dockPlanet = dockSystem.getPlanetUnitHolders().getFirst();
        harness.add(dockSystem, dockPlanet, player, UnitType.Spacedock, 1);

        CombatRollPreparation.addProjectionOfPowerAbilityUnit(state);
        assertEquals(1, state.playerUnits.size());
        assertTrue(state.playerUnits.keySet().stream()
                .map(pair -> pair.getLeft().getId())
                .anyMatch(id -> id.contains("projection")));
    }

    @Test
    void zelianBreakthroughBuildsAPlanetCombatUnitFromControlledPlanetResources() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player zelian = harness.player("zelian");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        zelian.setBreakthroughUnlocked("zelianbt", true);
        zelian.setBreakthroughActive("zelianbt", true);
        zelian.addPlanet(planet.getName());
        CombatContext state =
                state(harness, zelian, tile, tile.getUnitHolders().get(Constants.SPACE), CombatRollType.combatround);

        CombatRollPreparation.addZelianBreakthroughPlanetUnits(state);

        assertEquals(1, state.playerUnits.size());
        assertTrue(state.playerUnits.keySet().stream()
                .map(pair -> pair.getLeft().getId())
                .anyMatch(id -> id.contains("zelian")));
    }

    @Test
    void hostilePlanetoidsAddsControlledPlanetsOnlyToSpaceCombat() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        player.addTech("tf-hostileplanetoids");
        Tile tile = harness.tile("27");
        UnitHolder controlled = tile.getPlanetUnitHolders().getFirst();
        player.addPlanet(controlled.getName());
        CombatContext state =
                state(harness, player, tile, tile.getUnitHolders().get(Constants.SPACE), CombatRollType.combatround);

        CombatRollPreparation.addHostilePlanetoidsPlanetUnits(state);

        assertEquals(1, state.playerUnits.size());
        assertTrue(state.playerUnits.keySet().stream()
                .map(pair -> pair.getLeft().getId())
                .allMatch("zelianplanet"::equals));
    }

    @Test
    void articlesOfWarRemovesOnlyTheDisabledMechModel() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("naaz");
        Tile tile = harness.tile("19");
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        harness.game.getLaws().put("articles_war", 1);
        CombatContext state = state(harness, player, tile, space, CombatRollType.combatround);
        UnitModel disabled = Mapper.getUnit("naaz_mech_space");
        UnitModel carrier = Mapper.getUnit("carrier");
        state.playerUnits.put(Pair.of(disabled, space), 1);
        state.playerUnits.put(Pair.of(carrier, space), 1);
        player.addRelic("metalivoidarmaments");
        CombatContext afbState = state(harness, player, tile, space, CombatRollType.AFB);
        afbState.playerUnits.putAll(state.playerUnits);
        CombatRollPreparation.addMetaliVoidArmamentsUnit(afbState);

        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollPreparation.removeUnitsDisabledByArticlesOfWar(afbState);
        }

        assertFalse(afbState.playerUnits.keySet().stream().anyMatch(pair -> pair.getLeft() == disabled));
        assertTrue(afbState.playerUnits.keySet().stream().anyMatch(pair -> pair.getLeft() == carrier));
        assertTrue(afbState.playerUnits.keySet().stream()
                .anyMatch(pair -> "MetaliAFB".equals(pair.getLeft().getId())));
    }

    @Test
    void opponentResolutionUsesActualUnitsAndFallsBackToTheRoller() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        CombatContext state = state(harness, player, tile, space, CombatRollType.combatround);

        CombatRollPreparation.resolveCombatRollOpponent(state);
        assertSame(player, state.opponent);

        state.opponent = null;
        harness.addToSpace(tile, opponent, UnitType.Carrier, 1);
        CombatRollPreparation.resolveCombatRollOpponent(state);
        assertSame(opponent, state.opponent);
    }

    @Test
    void bombardmentExtraRollsFollowActualAssignmentsForAllThreeSources() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("argent");
        Tile tile = harness.tile("19");
        String planet = tile.getPlanetUnitHolders().getFirst().getName();
        CombatContext state =
                state(harness, player, tile, tile.getUnitHolders().get(Constants.SPACE), CombatRollType.bombardment);
        state.bombardPlanet = planet;
        NamedCombatModifierModel plasma = modifier("plus1_roll_plasmascoring");
        NamedCombatModifierModel argent = modifier("plus1_roll_argent_commander_bombard");
        NamedCombatModifierModel galvanize = modifier("roll_1_for_galvanize_bombard");
        List<NamedCombatModifierModel> extraRolls = new java.util.ArrayList<>(List.of(plasma, argent, galvanize));
        List<BombardmentAssignment> assignments = List.of(
                new BombardmentAssignment("plasmascoring", planet, false, BombardmentAssignmentType.TECH),
                new BombardmentAssignment("dn", planet, true, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue(
                "assignedBombardment" + player.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        CombatRollPreparation.removeUnassignedBombardmentExtraRolls(state, extraRolls);
        assertEquals(List.of(plasma, galvanize), extraRolls);

        assignments = List.of(
                new BombardmentAssignment("plasmascoring", planet, false, BombardmentAssignmentType.TECH),
                new BombardmentAssignment("argentcommander", planet, false, BombardmentAssignmentType.LEADER),
                new BombardmentAssignment("dn", planet, true, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue(
                "assignedBombardment" + player.getFaction(), new ObjectMapper().writeValueAsString(assignments));
        extraRolls = new java.util.ArrayList<>(List.of(plasma, argent, galvanize));

        CombatRollPreparation.removeUnassignedBombardmentExtraRolls(state, extraRolls);
        assertEquals(List.of(plasma, argent, galvanize), extraRolls);
    }

    @Test
    void combatUnitResolverFiltersByHolderAndKeepsDamagedUnitsInTheRoll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.addToSpace(tile, player, UnitType.Cruiser, 2);
        harness.add(tile, planet, player, UnitType.Infantry, 3);
        tile.addUnitDamage(Constants.SPACE, ti4.helpers.Units.getUnitKey(UnitType.Cruiser, player.getColorID()), 1);

        var spaceUnits = CombatUnitResolver.getUnitsInCombatByHolder(
                tile, tile.getSpaceUnitHolder(), player, harness.event, CombatRollType.combatround, harness.game);
        var groundUnits = CombatUnitResolver.getUnitsInCombatByHolder(
                tile, planet, player, harness.event, CombatRollType.combatround, harness.game);

        assertEquals(2, spaceUnits.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(spaceUnits.keySet().stream().allMatch(pair -> pair.getRight() == tile.getSpaceUnitHolder()));
        assertEquals(
                3, groundUnits.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(groundUnits.keySet().stream().allMatch(pair -> pair.getRight() == planet));
    }

    @Test
    void pendingControlNetworkModifierIsCollectedAndConsumedByTheRealPipeline() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player shooter = harness.player("sol");
        Player target = harness.player("mentak");
        Player netrunner = harness.player("netrunners");
        netrunner.addAbility("control_network");
        Tile tile = harness.tile("19");
        UnitHolder space = tile.getSpaceUnitHolder();
        CombatContext state = state(harness, shooter, tile, space, CombatRollType.SpaceCannonOffence);
        state.opponent = target;
        harness.game.setStoredValue("controlNetworkSpaceCannonTile" + shooter.getFaction(), tile.getTileID());
        harness.game.setStoredValue("controlNetworkSpaceCannonHolder" + shooter.getFaction(), space.getName());
        harness.game.setStoredValue(
                "controlNetworkSpaceCannonRoll" + shooter.getFaction(), CombatRollType.SpaceCannonOffence.toString());

        CombatRollModifiers modifiers = CombatRollPreparation.collectRollModifiers(state, new HashMap<>());

        assertTrue(modifiers.temporaryModifiers().stream().anyMatch(modifier -> "netrunners_control_network"
                .equals(modifier.getModifier().getAlias())));
        assertTrue(harness.game
                .getStoredValue("controlNetworkSpaceCannonTile" + shooter.getFaction())
                .isEmpty());
    }

    @Test
    void armedAdvancedTargetingSystemsAddsOneRealMechDieAndConsumesItsBinding() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player iron = harness.player("iron");
        Player sol = harness.player("sol");
        iron.addTech("beironats");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, iron, UnitType.Mech, 1);
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        CombatContext state = state(harness, iron, tile, planet, CombatRollType.combatround);
        state.opponent = sol;
        state.playerUnits = CombatUnitResolver.getUnitsInCombatByHolder(
                tile, planet, iron, harness.event, CombatRollType.combatround, harness.game);
        harness.game.setStoredValue("ironATSActiveTile_" + iron.getFaction(), tile.getPosition());
        harness.game.setStoredValue("ironATSActiveOpponent_" + iron.getFaction(), sol.getFaction());
        harness.game.setStoredValue("ironATSBoundHolder_" + iron.getFaction(), planet.getName());

        CombatRollModifiers modifiers = CombatRollPreparation.collectRollModifiers(
                state,
                CombatUnitResolver.getUnitsInCombat(
                        tile, planet, sol, harness.event, CombatRollType.combatround, harness.game));

        assertTrue(modifiers.extraRolls().stream()
                .anyMatch(modifier -> "_Advanced Targeting Systems_".equals(modifier.getName())));
        assertTrue(harness.game
                .getStoredValue("ironATSActiveTile_" + iron.getFaction())
                .isEmpty());
    }

    @Test
    void playerAndOpponentTemporaryModifiersAreCollectedFromTheirActualOwners() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player attacker = harness.player("sol");
        Player defender = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        CombatContext combat = state(harness, attacker, tile, planet, CombatRollType.combatround);
        combat.opponent = defender;
        addCurrentTemporaryModifier(attacker, tile, planet, "plus1_1round_all", "action_cards", "mb1");

        CombatRollModifiers playerModifiers = CombatRollPreparation.collectRollModifiers(combat, new HashMap<>());

        assertTrue(playerModifiers.temporaryModifiers().stream().anyMatch(modifier -> "plus1_1round_all"
                .equals(modifier.getModifier().getAlias())));

        CombatContext bombardment = state(harness, attacker, tile, planet, CombatRollType.bombardment);
        bombardment.opponent = defender;
        addCurrentTemporaryModifier(defender, tile, planet, "minus4_bombard", "action_cards", "bunker");

        CombatRollModifiers opponentModifiers =
                CombatRollPreparation.collectRollModifiers(bombardment, new HashMap<>());

        assertTrue(opponentModifiers.temporaryModifiers().stream().anyMatch(modifier -> "minus4_bombard"
                .equals(modifier.getModifier().getAlias())));
    }

    private static void addCurrentTemporaryModifier(
            Player player, Tile tile, UnitHolder holder, String alias, String relatedType, String relatedId) {
        TemporaryCombatModifierModel modifier = new TemporaryCombatModifierModel(
                relatedType, relatedId, Mapper.getCombatModifiers().get(alias), player.getNumberOfTurns());
        modifier.setUseInSystem(tile.getTileID());
        modifier.setUseInUnitHolder(holder.getName());
        player.addTempCombatMod(modifier);
    }

    private static NamedCombatModifierModel modifier(String alias) {
        return new NamedCombatModifierModel(Mapper.getCombatModifiers().get(alias), alias);
    }

    private static CombatContext state(
            CombatRollTestSupport.Harness harness,
            Player player,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType) {
        CombatContext state =
                new CombatContext(player, harness.game, harness.event, tile, holder.getName(), rollType, false);
        state.setCombatOnHolder(holder);
        state.setPlayerUnits(new HashMap<Pair<UnitModel, UnitHolder>, Integer>());
        return state;
    }
}
