package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.JdaService;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.spring.context.SpringContext;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.ObjectMapper;

class CombatRollPublicationCoverageTest extends BaseTi4Test {

    @Test
    void groundHitPublishesRealAssignmentButtonsAndReturnsStructuredResult() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.add(tile, planet, mentak, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, planet.getName(), CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed().hits(1);
            mocks.verifyButtonsContaining("autoassign");
            mocks.verifyReplayPayloadUnchanged(result.payload());
            dice.assertExhausted();
        }
    }

    @Test
    void zeroHitGroundRoundPublishesNextRoundInsteadOfAssignment() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.add(tile, planet, mentak, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, planet.getName(), CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed().hits(0);
            mocks.verifyButtonsContaining("roll dice for combat round");
            dice.assertExhausted();
        }
    }

    @Test
    void dummySpaceOpponentGetsDummySpecificAssignmentButton() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player dummy = harness.player("mentak");
        dummy.setDummy(true);
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, sol, UnitType.Cruiser, 1);
        harness.addToSpace(tile, dummy, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("dummyPlayerSpoof");
            dice.assertExhausted();
        }
    }

    @Test
    void afbHitPublishesFighterAssignmentFromRealDestroyerRoll() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player argent = harness.player("argent");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, argent, UnitType.Destroyer, 1);
        harness.addToSpace(tile, sol, UnitType.Fighter, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    argent, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.AFB, false);

            CombatRollTestSupport.assertThat(result).completed();
            assertTrue(result.totalHits() > 0);
            mocks.verifyLegacyButtonsContaining("assign");
            dice.assertExhausted();
        }
    }

    @Test
    void multiPlanetBombardmentReturnsTheLastCompletedTargetResult() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player mentak = harness.player("mentak");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("27");
        var planets = tile.getPlanetUnitHolders();
        harness.addToSpace(tile, mentak, UnitType.Dreadnought, 2);
        for (UnitHolder planet : planets) {
            sol.addPlanet(planet.getName());
            harness.add(tile, planet, sol, UnitType.Infantry, 1);
        }
        List<BombardmentAssignment> assignments = List.of(
                new BombardmentAssignment("dn", planets.get(0).getName(), false, BombardmentAssignmentType.UNIT),
                new BombardmentAssignment("dn", planets.get(1).getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue(
                "assignedBombardment" + mentak.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1, 10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.secondHalfOfCombatRoll(
                    mentak, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment);

            CombatRollTestSupport.assertThat(result).completed();
            assertEquals(1, result.totalHits(), "batch entry returns the last bombarded planet's result");
            dice.assertExhausted();
        }
    }

    @Test
    void defendersTwilightsFallProximaCancelsThePublishedBombardmentHit() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player mentak = harness.player("mentak");
        Player sol = harness.player("sol");
        sol.addTech("proxima");
        sol.addTech("tf-proxima");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        sol.addPlanet(planet.getName());
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.addToSpace(tile, mentak, UnitType.Dreadnought, 1);
        List<BombardmentAssignment> assignments =
                List.of(new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue("bombardmentTarget" + mentak.getFaction(), planet.getName());
        harness.game.setStoredValue(
                "assignedBombardment" + mentak.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    mentak, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment, false);

            CombatRollTestSupport.assertThat(result).completed().hits(0);
            assertTrue(result.message().contains("canceled 1 hit automatically"));
            dice.assertExhausted();
        }
    }

    @Test
    void privateFowSpaceCannonRelaysRealParsedResultOnlyToPlayerChannels() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        harness.game.setFowMode(true);
        Player shooter = harness.player("sol");
        Player target = harness.player("mentak");
        TextChannel shooterChannel = mock(TextChannel.class);
        TextChannel targetChannel = mock(TextChannel.class);
        shooter.setPrivateChannelID("101");
        target.setPrivateChannelID("102");
        when(JdaService.jda.getTextChannelById("101")).thenReturn(shooterChannel);
        when(JdaService.jda.getTextChannelById("102")).thenReturn(targetChannel);
        when(harness.event.getMessageChannel()).thenReturn(shooterChannel);
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, shooter, UnitType.Pds, 1);
        harness.addToSpace(tile, target, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            mocks.enableRealFogOfWarRollParsing();
            CombatRollResult result = CombatRollService.runCombatRoll(
                    shooter,
                    harness.game,
                    harness.event,
                    tile,
                    Constants.SPACE,
                    CombatRollType.SpaceCannonOffence,
                    false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.messages.verify(() -> MessageHelper.sendMessageToChannel(
                    eq(targetChannel), argThat(message -> message != null && message.contains("rolled for"))));
            mocks.messages.verify(() -> MessageHelper.sendMessageToChannel(
                    eq(shooterChannel),
                    argThat(message -> message != null && message.contains("Roll result was sent"))));
            dice.assertExhausted();
        }
    }

    @Test
    void fowAutomatedCombatWithDummyUsesDummyAssignmentWithoutPublicFlow() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        harness.game.setFowMode(true);
        Player shooter = harness.player("sol");
        Player dummy = harness.player("mentak");
        dummy.setDummy(true);
        TextChannel shooterChannel = mock(TextChannel.class);
        shooter.setPrivateChannelID("201");
        when(JdaService.jda.getTextChannelById("201")).thenReturn(shooterChannel);
        when(harness.event.getMessageChannel()).thenReturn(shooterChannel);
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, shooter, UnitType.Cruiser, 1);
        harness.addToSpace(tile, dummy, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    shooter, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, true);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("dummyPlayerSpoof");
            assertEquals(
                    "1",
                    harness.game.getStoredValue(
                            "combatRoundTracker" + shooter.getFaction() + tile.getPosition() + Constants.SPACE));
            dice.assertExhausted();
        }
    }

    @Test
    void groundDummyAndValkyrieBranchesComeFromTheRealCombatResult() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player dummy = harness.player("mentak");
        dummy.setDummy(true);
        dummy.addTech("vpw");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.add(tile, planet, dummy, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, planet.getName(), CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("dummyPlayerSpoof");
            mocks.verifyButtonsMessageContaining("Valkyrie Particle Weave");
            dice.assertExhausted();
        }
    }

    @Test
    void spaceZeroHitOffersTheNextRoundAndTracksTheRollerRound() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, sol, UnitType.Cruiser, 1);
        harness.addToSpace(tile, mentak, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(1);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed().hits(0);
            assertEquals(
                    "1",
                    harness.game.getStoredValue(
                            "combatRoundTracker" + sol.getFaction() + tile.getPosition() + Constants.SPACE));
            mocks.verifyButtonsContaining("roll dice for combat round");
            dice.assertExhausted();
        }
    }

    @Test
    void spaceAssignmentIncludesRelicAndDefensiveArchitectureReminders() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player crystellum = harness.player("crystellum");
        crystellum.addRelic("metalivoidshielding");
        crystellum.setBreakthroughUnlocked("crystellumbt", true);
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, sol, UnitType.Cruiser, 1);
        harness.addToSpace(tile, crystellum, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonsMessageContaining("Metali Void Shielding");
            mocks.verifyButtonsMessageContaining("Defensive Architecture");
            dice.assertExhausted();
        }
    }

    @Test
    void bombardmentPublishesMeteorKaloraAndX89EffectsFromOneRealHit() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player kalora = harness.player("kalora");
        Player target = harness.player("sol");
        kalora.addAbility("meteor_slings");
        kalora.addTech("x89c4");
        kalora.setBreakthroughUnlocked("kalorabt", true);
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        target.addPlanet(planet.getName());
        harness.add(tile, planet, target, UnitType.Infantry, 1);
        harness.addToSpace(tile, kalora, UnitType.Dreadnought, 1);
        harness.addToSpace(tile, kalora, UnitType.Infantry, 1);
        List<BombardmentAssignment> assignments =
                List.of(new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue("bombardmentTarget" + kalora.getFaction(), planet.getName());
        harness.game.setStoredValue(
                "assignedBombardment" + kalora.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    kalora, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("meteorSlings_");
            mocks.verifyButtonIdContaining("kaloraCommitInfantry_");
            mocks.verifyMessageContaining("X-89 Bacterial Weapon");
            assertTrue(!target.hasPlanetReady(planet.getName()), "X-89 must exhaust the real bombarded planet");
            dice.assertExhausted();
        }
    }

    @Test
    void bombardmentAgainstTheNeutralDummyOffersDummyAssignment() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player attacker = harness.player("mentak");
        Player neutral = harness.game.setupNeutralPlayer("gray");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, neutral, UnitType.Infantry, 1);
        harness.addToSpace(tile, attacker, UnitType.Dreadnought, 1);
        List<BombardmentAssignment> assignments =
                List.of(new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue("bombardmentTarget" + attacker.getFaction(), planet.getName());
        harness.game.setStoredValue(
                "assignedBombardment" + attacker.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    attacker, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("dummyPlayerSpoof");
            dice.assertExhausted();
        }
    }

    @Test
    void dummyAfbAndVyserixMorayOffersAreBothPublishedFromARealAfbHit() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player vyserix = harness.player("vyserix");
        Player dummy = harness.player("sol");
        dummy.setDummy(true);
        vyserix.setBreakthroughUnlocked("vyserixbt", true);
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, vyserix, UnitType.Destroyer, 1);
        harness.addToSpace(tile, dummy, UnitType.Fighter, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 1);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    vyserix, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.AFB, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyLegacyButtonIdContaining("dummyPlayerSpoof");
            mocks.verifyButtonIdContaining("vyserixMoraySystem_");
            dice.assertExhausted();
        }
    }

    @Test
    void crystellumGroundAndSpaceDefencesAddTheirRealAbilityButtons() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player crystellum = harness.player("crystellum");
        Tile groundTile = harness.tile("19");
        UnitHolder planet = groundTile.getPlanetUnitHolders().getFirst();
        harness.add(groundTile, planet, sol, UnitType.Infantry, 1);
        harness.add(groundTile, planet, crystellum, UnitType.Mech, 1);
        harness.addToSpace(groundTile, crystellum, UnitType.Fighter, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, groundTile, planet.getName(), CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("crystellumUseRefractum_");
            dice.assertExhausted();
        }

        Tile adjacent = harness.tileAt(
                "20",
                PositionMapper.getAdjacentTilePositions(groundTile.getPosition()).stream()
                        .filter(position -> position != null && !"-1".equals(position))
                        .findFirst()
                        .orElseThrow());
        harness.addToSpace(adjacent, crystellum, UnitType.Cruiser, 1);
        harness.addToSpace(groundTile, sol, UnitType.Cruiser, 1);
        harness.addToSpace(groundTile, crystellum, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, groundTile, Constants.SPACE, CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("crystellumOfferRefraction");
            dice.assertExhausted();
        }
    }

    @Test
    void ashenCommanderAndHeroPublicationUseTheRealBombardmentContext() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player ashen = harness.player("ashen");
        Player sol = harness.player("sol");
        harness.unlockLeader(ashen, "ashencommander");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        sol.addPlanet(planet.getName());
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.addToSpace(tile, ashen, UnitType.Dreadnought, 1);
        List<BombardmentAssignment> assignments =
                List.of(new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue("bombardmentTarget" + ashen.getFaction(), planet.getName());
        harness.game.setStoredValue("ashenHeroBombardmentAssign_" + ashen.getFaction(), planet.getName());
        harness.game.setStoredValue(
                "assignedBombardment" + ashen.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    ashen, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonsMessageContaining("Karos");
            mocks.verifyButtonsMessageContaining("please assign the BOMBARDMENT hit");
            dice.assertExhausted();
        }
    }

    @Test
    void normalProximaCancelsHitsForTheDefendersGalvanizedUnits() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player mentak = harness.player("mentak");
        Player sol = harness.player("sol");
        sol.addTech("proxima");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        sol.addPlanet(planet.getName());
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        tile.addGalvanize(planet.getName(), ti4.helpers.Units.getUnitKey(UnitType.Infantry, sol.getColorID()), 1);
        harness.addToSpace(tile, mentak, UnitType.Dreadnought, 2);
        List<BombardmentAssignment> assignments = List.of(
                new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT),
                new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue("bombardmentTarget" + mentak.getFaction(), planet.getName());
        harness.game.setStoredValue(
                "assignedBombardment" + mentak.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10, 10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    mentak, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment, false);

            CombatRollTestSupport.assertThat(result).completed().hits(1);
            assertTrue(result.message().contains("canceled 1 hit automatically"));
            dice.assertExhausted();
        }
    }

    @Test
    void fromTheAshesIsOfferedOnlyFromARealThirdPartyPromissoryNote() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Player ashen = harness.player("ashen");
        ashen.addOwnedPromissoryNoteByID("bepnashen");
        mentak.setPromissoryNote("bepnashen");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        harness.add(tile, planet, mentak, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, planet.getName(), CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyButtonIdContaining("ashenFromTheAshes_");
            dice.assertExhausted();
        }
    }

    @Test
    void refractionIsNotOfferedWithoutAnAdjacentNonFighterShip() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player sol = harness.player("sol");
        Player crystellum = harness.player("crystellum");
        Tile tile = harness.tile("19");
        harness.addToSpace(tile, sol, UnitType.Cruiser, 1);
        harness.addToSpace(tile, crystellum, UnitType.Carrier, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    sol, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyNoButtonIdContaining("crystellumOfferRefraction");
            dice.assertExhausted();
        }
    }

    @Test
    void kaloraCommitIsNotOfferedWithoutInfantryInSpace() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player kalora = harness.player("kalora");
        Player sol = harness.player("sol");
        kalora.setBreakthroughUnlocked("kalorabt", true);
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.addToSpace(tile, kalora, UnitType.Dreadnought, 1);
        harness.add(tile, planet, sol, UnitType.Infantry, 1);
        List<BombardmentAssignment> assignments =
                List.of(new BombardmentAssignment("dn", planet.getName(), false, BombardmentAssignmentType.UNIT));
        harness.game.setStoredValue("bombardmentTarget" + kalora.getFaction(), planet.getName());
        harness.game.setStoredValue(
                "assignedBombardment" + kalora.getFaction(), new ObjectMapper().writeValueAsString(assignments));

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    kalora, harness.game, harness.event, tile, Constants.SPACE, CombatRollType.bombardment, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyNoButtonIdContaining("kaloraCommitInfantry_");
            dice.assertExhausted();
        }
    }

    @Test
    void fromTheAshesIsNotOfferedWhenItsOwnerIsTheAttacker() {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player ashen = harness.player("ashen");
        Player mentak = harness.player("mentak");
        ashen.addOwnedPromissoryNoteByID("bepnashen");
        mentak.setPromissoryNote("bepnashen");
        Tile tile = harness.tile("19");
        UnitHolder planet = tile.getPlanetUnitHolders().getFirst();
        harness.add(tile, planet, ashen, UnitType.Infantry, 1);
        harness.add(tile, planet, mentak, UnitType.Infantry, 1);

        try (CombatRollTestSupport.ScriptedDice dice = CombatRollTestSupport.dice(10);
                PublicationMocks mocks = new PublicationMocks()) {
            CombatRollResult result = CombatRollService.runCombatRoll(
                    ashen, harness.game, harness.event, tile, planet.getName(), CombatRollType.combatround, false);

            CombatRollTestSupport.assertThat(result).completed();
            mocks.verifyNoButtonIdContaining("ashenFromTheAshes_");
            dice.assertExhausted();
        }
    }

    private static final class PublicationMocks implements AutoCloseable {
        private final MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
        private final MockedStatic<FOWCombatThreadMirroring> fow = mockStatic(FOWCombatThreadMirroring.class);
        private final MockedStatic<SpringContext> spring = mockStatic(SpringContext.class);
        private final CombatReplayService replay = mock(CombatReplayService.class);

        private PublicationMocks() {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);
        }

        private void enableRealFogOfWarRollParsing() {
            fow.when(() -> FOWCombatThreadMirroring.parseCombatRollMessage(any(), any()))
                    .thenCallRealMethod();
        }

        private void verifyButtonsContaining(String text) {
            messages.verify(
                    () -> MessageHelper.sendMessageToChannelWithButtons(
                            any(),
                            argThat(message ->
                                    message != null && message.toLowerCase().contains(text.toLowerCase())),
                            any()),
                    times(1));
        }

        private void verifyReplayPayloadUnchanged(CombatRollPayload publishedPayload) {
            ArgumentCaptor<CombatRollPayload> payload = ArgumentCaptor.forClass(CombatRollPayload.class);
            org.mockito.Mockito.verify(replay)
                    .mirrorCombatRoll(
                            any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), payload.capture());
            assertEquals(payload.getValue(), publishedPayload, "publication changed recorded dice or reroll history");
        }

        private void verifyButtonIdContaining(String text) {
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                    any(), any(), argThat(buttons -> containsButtonId(buttons, text))));
        }

        private void verifyNoButtonIdContaining(String text) {
            messages.verify(
                    () -> MessageHelper.sendMessageToChannelWithButtons(
                            any(), any(), argThat(buttons -> containsButtonId(buttons, text))),
                    never());
        }

        private void verifyLegacyButtonIdContaining(String text) {
            messages.verify(() -> MessageHelper.sendMessageToChannel(
                    any(), any(), argThat(buttons -> containsButtonId(buttons, text))));
        }

        private void verifyMessageContaining(String text) {
            messages.verify(
                    () -> MessageHelper.sendMessageToChannel(
                            any(), argThat(message -> message != null && message.contains(text))),
                    atLeastOnce());
        }

        private void verifyButtonsMessageContaining(String text) {
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                    any(), argThat(message -> message != null && message.contains(text)), any()));
        }

        private void verifyLegacyButtonsContaining(String text) {
            messages.verify(() -> MessageHelper.sendMessageToChannel(
                    any(),
                    argThat(message -> message != null && message.toLowerCase().contains(text.toLowerCase())),
                    any()));
        }

        private static boolean containsButtonId(List<Button> buttons, String text) {
            return buttons != null
                    && buttons.stream()
                            .anyMatch(button -> button.getCustomId() != null
                                    && button.getCustomId().contains(text));
        }

        @Override
        public void close() {
            spring.close();
            fow.close();
            messages.close();
        }
    }
}
