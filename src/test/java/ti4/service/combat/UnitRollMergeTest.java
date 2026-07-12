package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

class UnitRollMergeTest extends BaseTi4Test {

    @Test
    void sameModelIdStaysSplitWhenEffectiveProfilesDivergeByHolder() {
        Fixture fixture = fixture(6, 8);

        UnitMergeResult result = UnitRollExecution.mergeAndDetectDivergence(fixture.pipeline());

        assertEquals(2, result.units().size());
        assertTrue(result.divergingModels().contains("holder-sensitive-unit"));
    }

    @Test
    void sameModelIdMergesWhenEffectiveProfilesMatch() {
        Fixture fixture = fixture(6, 6);

        UnitMergeResult result = UnitRollExecution.mergeAndDetectDivergence(fixture.pipeline());

        assertEquals(1, result.units().size());
        assertEquals(3, result.units().values().iterator().next());
        assertTrue(result.divergingModels().isEmpty());
    }

    @Test
    void realGalvanizedHolderExtraDiePreventsInfantryProfilesFromMerging() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("mentak");
        Player opponent = harness.player("sol");
        Tile tile = harness.tile("27");
        UnitHolder first = tile.getPlanetUnitHolders().get(0);
        UnitHolder second = tile.getPlanetUnitHolders().get(1);
        UnitModel infantry = player.getUnitByBaseType("infantry");
        harness.add(tile, first, player, UnitType.Infantry, 1);
        harness.add(tile, second, player, UnitType.Infantry, 1);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>();
        units.put(Pair.of(infantry, first), 1);
        units.put(Pair.of(infantry, second), 1);
        tile.addGalvanize(first.getName(), Units.getUnitKey(UnitType.Infantry, player.getColorID()), 1);
        NamedCombatModifierModel galvanize = new NamedCombatModifierModel(
                Mapper.getCombatModifiers().get("roll_1_for_galvanize_combat"), "Galvanized");
        CombatContext combat = new CombatContext(
                player, harness.game, harness.event, tile, first.getName(), CombatRollType.combatround, false);
        combat.setCombatOnHolder(first);
        combat.setPlayerUnits(units);
        combat.setOpponent(opponent);
        combat.setModifiers(new CombatRollModifiers(List.of(), List.of(galvanize), List.of()));
        UnitRollExecution.CombatRollState pipeline = new UnitRollExecution.CombatRollState(combat);
        pipeline.mods = List.of();

        UnitMergeResult result = UnitRollExecution.mergeAndDetectDivergence(pipeline);

        assertEquals(2, result.units().size());
        assertTrue(result.divergingModels().contains(infantry.getId()));
    }

    private static Fixture fixture(int firstThreshold, int secondThreshold) {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("27");
        UnitHolder first = tile.getPlanetUnitHolders().get(0);
        UnitHolder second = tile.getPlanetUnitHolders().get(1);
        UnitModel firstModel = model(firstThreshold);
        UnitModel secondModel = model(secondThreshold);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>();
        units.put(Pair.of(firstModel, first), 1);
        units.put(Pair.of(secondModel, second), 2);
        CombatContext combat = new CombatContext(
                player, harness.game, harness.event, tile, first.getName(), CombatRollType.bombardment, false);
        combat.setCombatOnHolder(first);
        combat.setPlayerUnits(units);
        combat.setOpponent(opponent);
        combat.setModifiers(new CombatRollModifiers(List.of(), List.of(), List.of()));
        UnitRollExecution.CombatRollState pipeline = new UnitRollExecution.CombatRollState(combat);
        pipeline.mods = List.of();
        return new Fixture(pipeline);
    }

    private static UnitModel model(int threshold) {
        UnitModel model = new UnitModel();
        model.setId("holder-sensitive-unit");
        model.setAsyncId("dn");
        model.setBaseType("dreadnought");
        model.setName("Holder-sensitive unit");
        model.setBombardHitsOn(threshold);
        model.setBombardDieCount(1);
        return model;
    }

    private record Fixture(UnitRollExecution.CombatRollState pipeline) {}
}
