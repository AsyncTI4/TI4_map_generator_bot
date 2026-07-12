package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

class CombatRollRemainingCoverageTest extends BaseTi4Test {

    @Test
    void hacanFlagshipThalnosBuildsTheNearMissEncodingWithoutCountingTheNearMissAsDestroyed() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player hacan = harness.player("hacan");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, hacan, UnitType.Flagship, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        UnitRollExecution.UnitRollState unit = thalnosUnit(harness, hacan, sol, tile, UnitType.Flagship);
        unit.activeDice = new ArrayList<>(List.of(DiceHelper.spoof(7, 6), DiceHelper.spoof(7, 1)));
        unit.numMisses = 2;

        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            UnitRollAbilities.resolveThalnosMisses(unit);
        }

        assertEquals(1, unit.numMisses, "the spendable near miss must not be treated as a forced destruction");
        assertEquals(1, unit.pipeline.hacanFsButtons.size());
        assertTrue(unit.pipeline.hacanFsButtons.getFirst().getCustomId().contains("hacanFlagship_"));
        assertTrue(unit.pipeline.hacanFsButtons.getFirst().getCustomId().endsWith("_1"));
        assertTrue(unit.pipeline.hacanFsThalnosDestroyTypes.contains(UnitType.Flagship));
    }

    @Test
    void fallOfKenaraThalnosDefersAllDestructionForItsDedicatedResolution() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player hacan = harness.player("hacan");
        Player sol = harness.player("sol");
        hacan.removeOwnedUnitByID("warsun");
        harness.ownUnit(hacan, "tk-fallofkenara");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, hacan, UnitType.Warsun, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        UnitRollExecution.UnitRollState unit = thalnosUnit(harness, hacan, sol, tile, UnitType.Warsun);
        unit.activeDice =
                new ArrayList<>(List.of(DiceHelper.spoof(3, 1), DiceHelper.spoof(3, 2), DiceHelper.spoof(3, 10)));
        unit.numMisses = 2;

        UnitRollAbilities.resolveThalnosMisses(unit);

        assertEquals(0, unit.numMisses);
        assertTrue(unit.pipeline.hacanFsThalnosDestroyTypes.contains(UnitType.Warsun));
    }

    @Test
    void owningHacanFlagshipDoesNotActivateItsAbilityForADifferentActiveFlagshipModel() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        jolnar.removeOwnedUnitByID("jolnar_flagship");
        harness.ownUnit(jolnar, "sigma_jolnar_flagship_1");
        harness.ownUnit(jolnar, "hacan_flagship");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, jolnar, UnitType.Flagship, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        UnitRollExecution.UnitRollPipelineState pipeline = new UnitRollExecution.UnitRollPipelineState(
                harness.preparedState(jolnar, sol, tile, tile.getSpaceUnitHolder(), CombatRollType.combatround));

        assertFalse(pipeline.hacanFlagship);
    }

    @Test
    void experimentalBattlestationRollsThreeDiceOnceAcrossMultipleSpaceDocks() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("27");
        for (UnitHolder planet : tile.getPlanetUnitHolders()) {
            harness.add(tile, planet, sol, UnitType.Spacedock, 1);
        }
        harness.addToSpace(tile, mentak, UnitType.Carrier, 1);
        harness.game.setStoredValue("EBSFaction", sol.getFaction());

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(sol, mentak, tile, CombatRollType.SpaceCannonOffence);

            CombatRollTestSupport.assertThat(result).diceRolled(3);
            assertTrue(harness.game.getStoredValue("EBSFaction").isEmpty());
            dice.assertExhausted();
        }
    }

    @Test
    void tnelisAgentUsesOneDestroyerProfileEvenWhenSeveralDestroyersArePresent() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player argent = harness.player("argent");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, argent, UnitType.Destroyer, 3);
        harness.add(tile, tile.getPlanetUnitHolders().getFirst(), sol, UnitType.Infantry, 1);
        harness.game.setStoredValue("TnelisAgentFaction", argent.getFaction());

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(argent, sol, tile, CombatRollType.bombardment);

            CombatRollTestSupport.assertThat(result).diceRolled(2);
            assertTrue(harness.game.getStoredValue("TnelisAgentFaction").isEmpty());
            dice.assertExhausted();
        }
    }

    @Test
    void metaliVoidArmamentsContributesExactlyItsSyntheticThreeDice() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        sol.addRelic("metalivoidarmaments");
        harness.addToSpace(tile, sol, UnitType.Carrier, 2);
        harness.addToSpace(tile, mentak, UnitType.Fighter, 1);
        CombatRollPipelineState state = new CombatRollPipelineState(
                sol, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.AFB, false);
        CombatRollPreparation.validateCombatRollLocation(state);
        CombatRollPreparation.prepareCombatRoll(state);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = UnitRollExecution.rollForUnitsWithResult(state);

            CombatRollTestSupport.assertThat(result)
                    .completed()
                    .diceRolled(3)
                    .unitRolls(1)
                    .includesUnit("MetaliAFB");
            dice.assertExhausted();
        }
    }

    @Test
    void abandonedConventionsOfWarTriplesBombardmentHitsAndMaximumHits() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        harness.game.setConventionsOfWarAbandonedMode(true);
        Player mentak = harness.player("mentak");
        Player sol = harness.player("sol");
        Tile tile = bombardmentTile(harness, mentak, sol);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(mentak, sol, tile, CombatRollType.bombardment);

            CombatRollTestSupport.assertThat(result).hits(3).maximumHits(3);
            dice.assertExhausted();
        }
    }

    @Test
    void razeDoublesBombardmentHitsAndMaximumHits() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player mentak = harness.player("mentak");
        Player sol = harness.player("sol");
        mentak.setStoredValue("RazeFaction", "active");
        Tile tile = bombardmentTile(harness, mentak, sol);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(mentak, sol, tile, CombatRollType.bombardment);

            CombatRollTestSupport.assertThat(result).hits(2).maximumHits(2);
            dice.assertExhausted();
        }
    }

    @Test
    void shardVolleyAddsExactlyOneHitToANonzeroBombardmentRoll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player zelian = harness.player("zelian");
        Player sol = harness.player("sol");
        zelian.addTech("dszelir");
        Tile tile = bombardmentTile(harness, zelian, sol);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(zelian, sol, tile, CombatRollType.bombardment);

            CombatRollTestSupport.assertThat(result).hits(2).maximumHits(2).messageContains("Shard Volley");
            dice.assertExhausted();
        }
    }

    @Test
    void shardSaturationAddsExactlyOneHitToANonCombatAbilityRoll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player zelian = harness.player("zelian");
        Player sol = harness.player("sol");
        zelian.addTech("tf-shardsaturation");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, zelian, UnitType.Destroyer, 1);
        harness.addToSpace(tile, sol, UnitType.Fighter, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(zelian, sol, tile, CombatRollType.AFB);

            CombatRollTestSupport.assertThat(result).hits(2).maximumHits(3).messageContains("Shard Saturation");
            dice.assertExhausted();
        }
    }

    @Test
    void naazVoltronRemovesRealMechDamageAtTheStartOfTheRound() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player naaz = harness.player("naaz");
        Player sol = harness.player("sol");
        naaz.removeOwnedUnitByID("naaz_mech");
        naaz.removeOwnedUnitByID("naaz_mech_space");
        harness.ownUnit(naaz, "naaz_voltron");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, naaz, UnitType.Mech, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        tile.addUnitDamage(Constants.SPACE, Units.getUnitKey(UnitType.Mech, naaz.getColorID()), 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 10, 10, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(naaz, sol, tile, CombatRollType.combatround);

            assertEquals(0, tile.getSpaceUnitHolder().getDamagedUnitCount(UnitType.Mech, naaz.getColorID()));
            CombatRollTestSupport.assertThat(result)
                    .messageContains("Eidolon Maximum self-repaired")
                    .hasNoteFrom("naaz_voltron");
            dice.assertExhausted();
        }
    }

    private static Tile bombardmentTile(CombatRollTestSupport.Harness harness, Player attacker, Player defender) {
        Tile tile = harness.tile("19");
        attacker.removeTech("ps");
        attacker.removeTech("absol_ps");
        harness.addToSpace(tile, attacker, UnitType.Dreadnought, 1);
        harness.add(tile, tile.getPlanetUnitHolders().getFirst(), defender, UnitType.Infantry, 1);
        return tile;
    }

    private static UnitRollExecution.UnitRollState thalnosUnit(
            CombatRollTestSupport.Harness harness, Player player, Player opponent, Tile tile, UnitType unitType) {
        harness.game.setStoredValue("thalnosPlusOne", "true");
        UnitModel model = player.getUnitByType(unitType);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>();
        Pair<UnitModel, UnitHolder> key = Pair.of(model, tile.getSpaceUnitHolder());
        units.put(key, 1);
        CombatRollPipelineState combat = new CombatRollPipelineState(
                player, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, false);
        combat.setCombatOnHolder(tile.getSpaceUnitHolder());
        combat.setPlayerUnits(units);
        combat.setOpponent(opponent);
        combat.setModifiers(new CombatRollModifiers(List.of(), List.of(), List.of()));
        UnitRollExecution.UnitRollPipelineState pipeline = new UnitRollExecution.UnitRollPipelineState(combat);
        return new UnitRollExecution.UnitRollState(pipeline, Map.entry(key, 1));
    }
}
