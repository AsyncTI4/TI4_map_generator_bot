package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.contest.replay.core.CombatRollPayload.UnitRollType;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.testUtils.BaseTi4Test;

class CombatRollAbilityCoverageTest extends BaseTi4Test {

    @Test
    void jolNarFlagshipNineAddsTwoHitsWhileEightDoesNot() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, jolnar, UnitType.Flagship, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(8, 9);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(jolnar, sol, tile, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(4);
            CombatRollTestSupport.assertThat(result).maximumHits(6);
            assertEquals(6, result.payload().unitRolls().getFirst().printedHitsOn());
            assertEquals(7, result.payload().unitRolls().getFirst().effectiveThreshold());
            dice.assertExhausted();
        }
    }

    @Test
    void tekklarEliteDoublesOnlySuccessfulInfantryDice() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sardakk = harness.player("sardakk");
        Player sol = harness.player("sol");
        harness.ownUnit(sardakk, "tk-tekklarelite");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sardakk, UnitType.Infantry, 2);
        harness.add(tile, planet, sol, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(5, 6);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(sardakk, sol, tile, planet, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(2);
            CombatRollTestSupport.assertThat(result).maximumHits(4);
            dice.assertExhausted();
        }
    }

    @Test
    void owningJusticerRailDoesNotApplyItsRestrictionToAnExperimentalBattlestationRoll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        harness.ownUnit(sol, "tf-justicerrail");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sol, UnitType.Spacedock, 1);
        harness.addToSpace(tile, mentak, UnitType.Carrier, 1);
        harness.game.setStoredValue("EBSFaction", sol.getFaction());

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(sol, mentak, tile, CombatRollType.SpaceCannonOffence);

            CombatRollTestSupport.assertThat(result).hits(1).diceRolled(3);
            assertTrue(
                    harness.game.getStoredValue(sol.getFaction() + "graviton").isEmpty());
            dice.assertExhausted();
        }
    }

    @Test
    void anActiveJusticerRailMissDoesNotApplyItsRestriction() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player winnu = harness.player("winnu");
        Player sol = harness.player("sol");
        harness.ownUnit(winnu, "tf-justicerrail");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, winnu, UnitType.Pds, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(winnu, sol, tile, CombatRollType.SpaceCannonOffence);

            CombatRollTestSupport.assertThat(result).hits(0).includesUnit("tf-justicerrail");
            assertTrue(
                    harness.game.getStoredValue(winnu.getFaction() + "graviton").isEmpty());
            dice.assertExhausted();
        }
    }

    @Test
    void anActiveJusticerRailHitAppliesItsNonFighterRestriction() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player winnu = harness.player("winnu");
        Player sol = harness.player("sol");
        harness.ownUnit(winnu, "tf-justicerrail");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, winnu, UnitType.Pds, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result =
                    harness.execute(winnu, sol, tile, tile.getSpaceUnitHolder(), CombatRollType.SpaceCannonOffence);

            CombatRollTestSupport.assertThat(result).hits(1).includesUnit("tf-justicerrail");
            assertEquals("yes", harness.game.getStoredValue(winnu.getFaction() + "graviton"));
            dice.assertExhausted();
        }
    }

    @Test
    void vadenFlagshipGainsOneTradeGoodFromMultipleBombardmentHits() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player vaden = harness.player("vaden");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, vaden, UnitType.Flagship, 1);
        int originalTradeGoods = vaden.getTg();

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(5, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(vaden, sol, tile, CombatRollType.bombardment);
            CombatRollTestSupport.assertThat(result).hits(2);
            assertEquals(originalTradeGoods + 1, vaden.getTg());
            dice.assertExhausted();
        }
    }

    @Test
    void vadenFlagshipUsesModifierAwareBombardmentSuccess() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player vaden = harness.player("vaden");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, vaden, UnitType.Flagship, 1);
        UnitRollExecution.UnitRollState unit =
                preparedUnit(harness, vaden, sol, tile, tile.getSpaceUnitHolder(), CombatRollType.bombardment);
        int originalTradeGoods = vaden.getTg();

        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            unit.activeDice = List.of(DiceHelper.spoof(4, 4));
            UnitRollAbilities.resolveVadenFlagshipTradeGood(unit);
            assertEquals(originalTradeGoods + 1, vaden.getTg(), "a modified 4 that succeeds must trigger the flagship");

            unit.activeDice = List.of(DiceHelper.spoof(6, 5));
            UnitRollAbilities.resolveVadenFlagshipTradeGood(unit);
            assertEquals(originalTradeGoods + 1, vaden.getTg(), "a raw 5 that fails after modifiers must not trigger");
        }
    }

    @Test
    void uzeanWardogAbilityIsReachedByRealAfbUnitSelection() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player belkosea = harness.player("belkosea");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, belkosea, UnitType.Mech, 1);
        harness.addToSpace(tile, sol, UnitType.Fighter, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(6);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(belkosea, sol, tile, CombatRollType.AFB);
            CombatRollTestSupport.assertThat(result).hits(1).includesUnit("belkosea_mech");
            dice.assertExhausted();
        }
    }

    @Test
    void ironCommanderRerollsAnActualMechMiss() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player iron = harness.player("iron");
        Player sol = harness.player("sol");
        harness.unlockLeader(iron, "ironcommander");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, iron, UnitType.Mech, 1);
        harness.add(tile, planet, sol, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(iron, sol, tile, planet, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(1).hasRollType(UnitRollType.IRON_COMMANDER_REROLL_MISSES);
            dice.assertExhausted();
        }
    }

    @Test
    void personalValorAppliesToANaturalTenFromAnIronCommanderCombatReroll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player iron = harness.player("iron");
        Player sol = harness.player("sol");
        harness.unlockLeader(iron, "ironcommander");
        iron.addTech("tf-valortf");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, iron, UnitType.Mech, 1);
        harness.add(tile, planet, sol, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(iron, sol, tile, planet, CombatRollType.combatround);

            CombatRollTestSupport.assertThat(result)
                    .hits(2)
                    .maximumHits(2)
                    .hasRollType(UnitRollType.IRON_COMMANDER_REROLL_MISSES);
            dice.assertExhausted();
        }
    }

    @Test
    void kaltrimAndMunitionsExecuteInRealTimingOrder() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player kaltrim = harness.player("kaltrim");
        Player sol = harness.player("sol");
        harness.unlockLeader(kaltrim, "kaltrimcommander");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, kaltrim, UnitType.Cruiser, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        harness.game.setStoredValue("munitionsReserves", kaltrim.getFaction());

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 1, 1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(kaltrim, sol, tile, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result)
                    .hits(1)
                    .hasRollType(UnitRollType.KALTRIM_COMMANDER_REROLL_ONES)
                    .hasRollType(UnitRollType.MUNITIONS_RESERVES_REROLL);
            dice.assertExhausted();
        }
    }

    @Test
    void kaltrimDoesNotRerollItsOwnReplacementWithoutMunitions() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player kaltrim = harness.player("kaltrim");
        Player sol = harness.player("sol");
        harness.unlockLeader(kaltrim, "kaltrimcommander");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, kaltrim, UnitType.Cruiser, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(kaltrim, sol, tile, CombatRollType.combatround);

            CombatRollTestSupport.assertThat(result)
                    .hits(1)
                    .rollTypeCount(UnitRollType.KALTRIM_COMMANDER_REROLL_ONES, 1)
                    .lacksRollType(UnitRollType.MUNITIONS_RESERVES_REROLL);
            dice.assertExhausted();
        }
    }

    @Test
    void personalValorAppliesToANaturalTenFromAKaltrimCombatReroll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player kaltrim = harness.player("kaltrim");
        Player sol = harness.player("sol");
        harness.unlockLeader(kaltrim, "kaltrimcommander");
        kaltrim.addTech("tf-valortf");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, kaltrim, UnitType.Cruiser, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(kaltrim, sol, tile, CombatRollType.combatround);

            CombatRollTestSupport.assertThat(result)
                    .hits(2)
                    .maximumHits(2)
                    .hasRollType(UnitRollType.KALTRIM_COMMANDER_REROLL_ONES);
            dice.assertExhausted();
        }
    }

    @Test
    void sigmaJolNarFlagshipSuccessRollsAnotherDieUntilTheChainMisses() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        jolnar.removeOwnedUnitByID("jolnar_flagship");
        harness.ownUnit(jolnar, "sigma_jolnar_flagship_1");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, jolnar, UnitType.Flagship, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(jolnar, sol, tile, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(1);
            CombatRollTestSupport.assertThat(result).diceRolled(2);
            dice.assertExhausted();
        }
    }

    @Test
    void personalValorAddsAHitForANaturalTen() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        sol.addTech("tf-valortf");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, sol, UnitType.Cruiser, 1);
        harness.addToSpace(tile, mentak, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(sol, mentak, tile, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(2);
            dice.assertExhausted();
        }
    }

    @Test
    void mercenaryCaptainsRewardTriggersFromNeutralCombatHitOnlyOnce() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player neutral = harness.player("neutral");
        Player sol = harness.player("sol");
        sol.addTech("tf-mercenarycaptains");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, neutral, UnitType.Cruiser, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        int commodities = sol.getCommodities();

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(neutral, sol, tile, CombatRollType.combatround);
            assertEquals(commodities + 1, sol.getCommodities());
            assertEquals("yes", harness.game.getStoredValue("mercenarycaptaintrigged"));
            dice.assertExhausted();
        }
    }

    @Test
    void gledgePdsNaturalNineOffersExplorationFromRealSpaceCannonRoll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player gledge = harness.player("gledge");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, gledge, UnitType.Pds, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(9);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(
                    gledge, sol, tile, tile.getUnitHolders().get("space"), CombatRollType.SpaceCannonOffence);
            CombatRollTestSupport.assertThat(result).hits(1);
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), any()));
            dice.assertExhausted();
        }
    }

    @Test
    void dragonFreedHitOffersAssignmentOnEveryRealAdjacentEnemyPlanet() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player obsidian = harness.player("obsidian");
        Player sol = harness.player("sol");
        harness.ownUnit(obsidian, "tf-dragonfreed");
        Tile origin = harness.tile("19");
        harness.addToSpace(origin, obsidian, UnitType.Warsun, 1);
        java.util.List<String> adjacentPositions =
                PositionMapper.getAdjacentTilePositions(origin.getPosition()).stream()
                        .filter(position -> position != null && !"-1".equals(position))
                        .limit(2)
                        .toList();
        assertEquals(2, adjacentPositions.size());
        Tile firstAdjacent = harness.tileAt("20", adjacentPositions.get(0));
        Tile secondAdjacent = harness.tileAt("21", adjacentPositions.get(1));
        harness.add(firstAdjacent, firstAdjacent.getPlanetUnitHolders().getFirst(), sol, UnitType.Infantry, 1);
        harness.add(secondAdjacent, secondAdjacent.getPlanetUnitHolders().getFirst(), sol, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(3, 1, 1);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(obsidian, sol, origin, CombatRollType.bombardment);
            CombatRollTestSupport.assertThat(result).hits(1);
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), any()), times(2));
            dice.assertExhausted();
        }
    }

    @Test
    void tacticalBrillianceUsesTheJolNarCommanderRerollPathWithoutTheCommander() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player mentak = harness.player("mentak");
        Player sol = harness.player("sol");
        mentak.addTech("tf-tacticalbrilliance");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, mentak, UnitType.Destroyer, 1);
        harness.addToSpace(tile, sol, UnitType.Fighter, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 1, 10, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(mentak, sol, tile, CombatRollType.AFB);

            CombatRollTestSupport.assertThat(result).hits(2).hasRollType(UnitRollType.JOL_NAR_COMMANDER_REROLL_MISSES);
            dice.assertExhausted();
        }
    }

    @Test
    void proximaSelfBombardmentRerollsHitsAndCanLoseThem() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player jolnar = harness.player("jolnar");
        harness.unlockLeader(jolnar, "jolnarcommander");
        jolnar.addTech("proxima");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, jolnar, UnitType.Dreadnought, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(jolnar, jolnar, tile, CombatRollType.bombardment);

            CombatRollTestSupport.assertThat(result).hits(0).hasRollType(UnitRollType.JOL_NAR_COMMANDER_REROLL_HITS);
            dice.assertExhausted();
        }
    }

    @Test
    void systemValorAddsAnExtraHitToAMunitionsRerollNaturalTen() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player roller = harness.player("sol");
        Player valorHolder = harness.player("kjalengard");
        valorHolder.addAbility("valor");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        tile.getSpaceUnitHolder().addToken("token_ds_glory.png");
        harness.addToSpace(tile, roller, UnitType.Cruiser, 1);
        harness.addToSpace(tile, opponent, UnitType.Carrier, 1);
        harness.game.setStoredValue("munitionsReserves", roller.getFaction());

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(roller, opponent, tile, CombatRollType.combatround);

            CombatRollTestSupport.assertThat(result).hits(2).hasRollType(UnitRollType.MUNITIONS_RESERVES_REROLL);
            dice.assertExhausted();
        }
    }

    @Test
    void thalnosRerollStateSuppressesMunitionsRecursion() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player naalu = harness.player("naalu");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, naalu, UnitType.Cruiser, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        harness.game.setStoredValue("thalnosPlusOne", "true");
        harness.game.setStoredValue("munitionsReserves", naalu.getFaction());
        harness.game.setSpecificThalnosUnit(tile.getPosition() + "_space_cruiser", 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(naalu, sol, tile, CombatRollType.combatround);

            CombatRollTestSupport.assertThat(result).hits(0).lacksRollType(UnitRollType.MUNITIONS_RESERVES_REROLL);
            assertEquals(
                    0,
                    tile.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, naalu.getColor()),
                    "a real Thalnos miss must destroy the selected unit");
            dice.assertExhausted();
        }
    }

    @Test
    void sigmaJolNarCascadeAndPersonalValorBothAccountForNaturalTens() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        jolnar.removeOwnedUnitByID("jolnar_flagship");
        harness.ownUnit(jolnar, "sigma_jolnar_flagship_1");
        jolnar.addTech("tf-valortf");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, jolnar, UnitType.Flagship, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 10, 1);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(jolnar, sol, tile, CombatRollType.combatround);

            CombatRollTestSupport.assertThat(result).hits(4);
            CombatRollTestSupport.assertThat(result).diceRolled(3);
            dice.assertExhausted();
        }
    }

    @Test
    void staleSuperchargeSelectionIsReplacedAndStillProducesFlatSelectedAndRemainderGroups() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player bluetf = harness.player("bluetf");
        Player sol = harness.player("sol");
        bluetf.addTech("tf-supercharge");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, bluetf, UnitType.Cruiser, 2);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        harness.game.setStoredValue("highestValueSingleUnit" + bluetf.getFaction(), "ws");

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(8, 8);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(bluetf, sol, tile, CombatRollType.combatround);
            assertTrue(harness.game
                    .getStoredValue("highestValueSingleUnit" + bluetf.getFaction())
                    .isEmpty());
            var groups = result.payload().unitRolls().stream()
                    .sorted(java.util.Comparator.comparingInt(
                            ti4.contest.replay.core.CombatRollPayload.UnitRoll::modifier))
                    .toList();
            assertEquals(2, groups.size());
            var remaining = groups.getFirst();
            var selected = groups.getLast();
            assertEquals(1, selected.quantity());
            assertEquals(1, remaining.quantity());
            assertEquals(remaining.modifier() + 2, selected.modifier());
            assertTrue(groups.stream()
                    .allMatch(
                            roll -> roll.rollType() == ti4.contest.replay.core.CombatRollPayload.UnitRollType.PRIMARY));
            dice.assertExhausted();
        }
    }

    @Test
    void gravleashUsesTheSameSelectedAndRemainderGroupsWithoutApplyingTwice() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player letnev = harness.player("letnev");
        Player sol = harness.player("sol");
        letnev.setBreakthroughUnlocked("letnevbt", true);
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, letnev, UnitType.Cruiser, 2);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(8, 8);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(letnev, sol, tile, CombatRollType.combatround);
            assertEquals(2, result.payload().unitRolls().size());
            assertEquals(2, result.payload().total().diceRolled());
            assertEquals(
                    2,
                    result.payload().unitRolls().stream()
                            .mapToInt(ti4.contest.replay.core.CombatRollPayload.UnitRoll::quantity)
                            .sum());
            assertTrue(result.payload().unitRolls().stream()
                    .allMatch(
                            roll -> roll.rollType() == ti4.contest.replay.core.CombatRollPayload.UnitRollType.PRIMARY));
            assertEquals(
                    2,
                    result.payload().unitRolls().stream()
                            .map(ti4.contest.replay.core.CombatRollPayload.UnitRoll::modifier)
                            .distinct()
                            .count());
            dice.assertExhausted();
        }
    }

    @Test
    void theSameUnitsWithoutGravleashRemainOneGroupWithTheSameTotalDice() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player letnev = harness.player("letnev");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, letnev, UnitType.Cruiser, 2);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(8, 8);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(letnev, sol, tile, CombatRollType.combatround);

            assertEquals(1, result.payload().unitRolls().size());
            assertEquals(2, result.payload().unitRolls().getFirst().quantity());
            assertEquals(2, result.payload().total().diceRolled());
            assertEquals(
                    ti4.contest.replay.core.CombatRollPayload.UnitRollType.PRIMARY,
                    result.payload().unitRolls().getFirst().rollType());
            dice.assertExhausted();
        }
    }

    @Test
    void tekklarEliteAppliesToASuccessfulMunitionsReroll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sardakk = harness.player("sardakk");
        Player sol = harness.player("sol");
        harness.ownUnit(sardakk, "tk-tekklarelite");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sardakk, UnitType.Infantry, 1);
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.game.setStoredValue("munitionsReserves", sardakk.getFaction());

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(sardakk, sol, tile, planet, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(2).maximumHits(2);
            assertTrue(result.payload().unitRolls().stream()
                    .flatMap(roll -> roll.dice().stream())
                    .anyMatch(die -> die.source()
                            == ti4.contest.replay.core.CombatRollPayload.DieRollSource.MUNITIONS_RESERVES));
            assertTrue(harness.game.getStoredValue("munitionsReserves").isEmpty());
            dice.assertExhausted();
        }
    }

    @Test
    void gloriousHallsAndPersonalValorStackAndKeepTheirDistinctNames() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        harness.game.setTwilightsFallMode(true);
        Player roller = harness.player("sol");
        Player hallsHolder = harness.player("kjalengard");
        Player opponent = harness.player("mentak");
        roller.addTech("tf-valortf");
        hallsHolder.addTech("tf-glorioushalls");
        Tile tile = harness.tile("19");
        tile.getSpaceUnitHolder().addToken("token_ds_glory.png");
        harness.addToSpace(tile, roller, UnitType.Cruiser, 1);
        harness.addToSpace(tile, opponent, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(roller, opponent, tile, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(3);
            messages.verify(() -> MessageHelper.sendMessageToChannel(
                    any(), org.mockito.ArgumentMatchers.contains("**Glorious Halls**")));
            messages.verify(() ->
                    MessageHelper.sendMessageToChannel(any(), org.mockito.ArgumentMatchers.contains("**Valor**")));
            dice.assertExhausted();
        }
    }

    @Test
    void sigmaJolNarCascadeStopsAtItsHundredHitSafetyBoundary() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        jolnar.removeOwnedUnitByID("jolnar_flagship");
        harness.ownUnit(jolnar, "sigma_jolnar_flagship_1");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, jolnar, UnitType.Flagship, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        int[] allHits = new int[100];
        Arrays.fill(allHits, 10);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(allHits);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(jolnar, sol, tile, CombatRollType.combatround);
            CombatRollTestSupport.assertThat(result).hits(100);
            CombatRollTestSupport.assertThat(result).diceRolled(100);
            dice.assertExhausted();
        }
    }

    @Test
    void zephyrionNaturalTenCreatesAHitEvenWhenTheRawRollMisses() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player zephyrion = harness.player("zephyrion");
        Player argent = harness.player("argent");
        harness.unlockLeader(zephyrion, "zephyrioncommander");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, zephyrion, UnitType.Cruiser, 1);
        harness.addToSpace(tile, argent, UnitType.Carrier, 1);
        CombatRollPipelineState state = harness.preparedState(
                zephyrion, argent, tile, tile.getSpaceUnitHolder(), CombatRollType.SpaceCannonOffence);
        CombatModifierModel penalty = new CombatModifierModel();
        penalty.setAlias("test_stacked_space_cannon_penalty");
        penalty.setType("mods");
        penalty.setValue(-3);
        penalty.setScope("");
        penalty.setForCombatAbility(CombatRollType.SpaceCannonOffence);
        state.setModifiers(new CombatRollModifiers(
                java.util.List.of(new NamedCombatModifierModel(penalty, "Stacked defensive penalties")),
                java.util.List.of(),
                java.util.List.of()));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = UnitRollExecution.rollForUnitsWithResult(state);
            assertEquals(11, result.payload().unitRolls().getFirst().effectiveThreshold());
            assertEquals(1, result.totalHits(), "the raw threshold is 11, so only the commander creates this hit");
            dice.assertExhausted();
        }
    }

    @Test
    void gravleashSegmentsCannotRewardMercenaryCaptainsMoreThanOnce() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player neutral = harness.player("neutral");
        Player captain = harness.player("sol");
        Player opponent = harness.player("mentak");
        neutral.setBreakthroughUnlocked("letnevbt", true);
        captain.addTech("tf-mercenarycaptains");
        captain.setCommodities(0);
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, neutral, UnitType.Cruiser, 2);
        harness.addToSpace(tile, opponent, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 10);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            CombatRollResult result = harness.execute(neutral, opponent, tile, CombatRollType.combatround);
            assertEquals(1, captain.getCommodities());
            assertEquals("yes", harness.game.getStoredValue("mercenarycaptaintrigged"));
            dice.assertExhausted();
        }
    }

    @Test
    void thalnosExtraRollAmbiguityReportsButDoesNotDestroyAnUntraceableUnit() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player naalu = harness.player("naalu");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, naalu, UnitType.Cruiser, 1);
        harness.addToSpace(tile, sol, UnitType.Carrier, 1);
        harness.game.setStoredValue("thalnosPlusOne", "true");
        harness.game.setSpecificThalnosUnit(tile.getPosition() + "_space_cruiser", 1);
        CombatModifierModel extraDie = new CombatModifierModel();
        extraDie.setAlias("test_untraceable_extra_die");
        extraDie.setType("extrarolls");
        extraDie.setValue(1);
        extraDie.setScope("ca");
        extraDie.setForCombatAbility(CombatRollType.combatround);
        CombatRollPipelineState state =
                harness.preparedState(naalu, sol, tile, tile.getSpaceUnitHolder(), CombatRollType.combatround);
        state.setModifiers(new CombatRollModifiers(
                java.util.List.of(),
                java.util.List.of(new NamedCombatModifierModel(extraDie, "Untraceable extra die")),
                java.util.List.of()));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            CombatRollResult result = UnitRollExecution.rollForUnitsWithResult(state);
            assertEquals(1, tile.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, naalu.getColor()));
            messages.verify(() -> MessageHelper.sendMessageToChannel(
                    any(), org.mockito.ArgumentMatchers.contains("no units were removed due to extra rolls")));
            dice.assertExhausted();
        }
    }

    private static UnitRollExecution.UnitRollState preparedUnit(
            CombatRollTestSupport.Harness harness,
            Player player,
            Player opponent,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType) {
        UnitRollExecution.UnitRollPipelineState pipeline = new UnitRollExecution.UnitRollPipelineState(
                harness.preparedState(player, opponent, tile, holder, rollType));
        return new UnitRollExecution.UnitRollState(
                pipeline, pipeline.playerUnits.entrySet().iterator().next());
    }
}
