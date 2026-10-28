package ti4.service.leader.agent.modules.homebrew.theodisi;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class TheodisiAgentsPart2Test extends BaseTi4Test {

    @Test
    void everyAgentIsRegistered() {
        assertThat(LeaderAgents.findAgent("thronesagent")).containsInstanceOf(ThronesAgent.class);
        assertThat(LeaderAgents.findAgent("ponthousagent")).containsInstanceOf(PonthousAgent.class);
        assertThat(LeaderAgents.findAgent("scrapyardagent")).containsInstanceOf(ScrapyardAgent.class);
        assertThat(LeaderAgents.findAgent("stonebornagentdormant")).containsInstanceOf(HazorusAgent.class);
        // The unleashed Hazorus is the flipped side of the same card, so one module covers both ids.
        assertThat(LeaderAgents.findAgent("stonebornagentunleashed")).containsInstanceOf(HazorusAgent.class);
        assertThat(LeaderAgents.findAgent("morphaagent")).containsInstanceOf(GolgiAgent.class);
        assertThat(LeaderAgents.findAgent("thurvialiagent")).containsInstanceOf(HopeOfTheCosmosAgent.class);
    }

    // Malrik the Defiler (Thrones)

    @Test
    void thronesDecodesAProjectPiTargetTheirUnitAndTheDestination() {
        AgentModuleFixture fixture = new AgentModuleFixture("thrones");
        fixture.target.addOwnedUnitByID("cruiser");
        ThronesAgent thrones = new ThronesAgent();

        ThronesAgent.Placement placement = thrones.decode(
                        fixture.game,
                        fixture.user,
                        AgentModuleFixture.TARGET_FACTION + "~ca~" + AgentModuleFixture.ACTIVE_SYSTEM)
                .orElseThrow();

        assertThat(placement.target()).isEqualTo(fixture.target);
        assertThat(placement.ship().getAsyncId()).isEqualTo("ca");
        assertThat(placement.destination()).isEqualTo(fixture.tile);
        assertThat(thrones.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "~ca"))
                .isEmpty();
        assertThat(thrones.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void thronesNeedsASpaceDockSystemWithoutOtherPlayersUnits() {
        AgentModuleFixture fixture = new AgentModuleFixture("thrones");
        fixture.target.addOwnedUnitByID("cruiser");
        fixture.target.addOwnedUnitByID("spacedock");
        UnitModel cruiser = fixture.target.getUnitFromAsyncID("ca");
        ThronesAgent thrones = new ThronesAgent();
        ThronesAgent.Placement placement = new ThronesAgent.Placement(fixture.target, cruiser, fixture.tile);

        assertThat(thrones.unusableReason(fixture.game, fixture.user, placement))
                .as("no space dock yet")
                .isPresent();

        fixture.tile.addUnit("lodor", Units.getUnitKey(UnitType.Spacedock, fixture.target.getColorID()), 1);
        assertThat(thrones.unusableReason(fixture.game, fixture.user, placement))
                .isEmpty();

        fixture.user.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.user.getColorID()), 1);
        assertThat(thrones.unusableReason(fixture.game, fixture.user, placement))
                .as("another player's ship is in the system")
                .isPresent();
    }

    @Test
    void thronesShipButtonsBelongToTheTargetAndCarryTheOwner() {
        AgentModuleFixture fixture = new AgentModuleFixture("thrones");
        fixture.target.addOwnedUnitByID("cruiser");
        fixture.target.addOwnedUnitByID("warsun");

        List<Button> buttons = ThronesAgent.shipButtons(fixture.user, fixture.target);

        // The war sun costs more than 4, so only the cruiser can come back.
        assertThat(buttons).hasSize(1);
        assertOwnedBy(buttons.getFirst(), fixture.target);
        assertThat(buttons.getFirst().getCustomId()).endsWith("thronesAgentChooseShip_thrones~ca");
    }

    @Test
    void thronesButtonIsOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("thrones");

        assertOwnedAgentButton(ThronesAgent.button(fixture.user), fixture.user, "thronesAgentSelectTarget");
    }

    // Caelyn Lorris (Ponthous)

    @Test
    void ponthousDecodesTheSystemAndAProjectPiTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("ponthous");
        PonthousAgent ponthous = new PonthousAgent();

        assertThat(ponthous.decode(fixture.game, fixture.user, AgentModuleFixture.ACTIVE_SYSTEM + "|pi_hacan"))
                .contains(new PonthousAgent.Reinforcement(fixture.tile, fixture.target));
        assertThat(ponthous.decode(fixture.game, fixture.user, "999|pi_hacan")).isEmpty();
        assertThat(ponthous.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void ponthousTargetButtonsAreOwnedAgentButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("ponthous");

        List<Button> buttons =
                PonthousAgent.targetButtons(fixture.game, fixture.user, AgentModuleFixture.ACTIVE_SYSTEM);

        assertThat(buttons).hasSize(2);
        assertOwnedAgentButton(
                buttons.get(1),
                fixture.user,
                "exhaustAgent_ponthousagent_" + AgentModuleFixture.ACTIVE_SYSTEM + "|pi_hacan");
        assertOwnedAgentButton(
                PonthousAgent.button(fixture.user, fixture.tile),
                fixture.user,
                "ponthousagent_" + AgentModuleFixture.ACTIVE_SYSTEM);
    }

    // Rikka "Razor Fang" Corvin (Scrapyard)

    @Test
    void scrapyardDecodesTheActivePlayer() {
        new AgentModuleFixture("scrapyard").assertDecodesTargets(new ScrapyardAgent());
    }

    @Test
    void scrapyardOfferIsAnOwnedAgentButtonNamingTheActivePlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("scrapyard");

        List<Button> buttons = ScrapyardAgent.offerButtons(fixture.target, fixture.user);

        assertOwnedAgentButton(buttons.getFirst(), fixture.user, "exhaustAgent_scrapyardagent_pi_hacan");
    }

    @Test
    void scrapyardTellsTheActivePlayerToDeclareTheirUnits() {
        AgentModuleFixture fixture = new AgentModuleFixture("scrapyard");
        ScrapyardAgent scrapyard = new ScrapyardAgent();

        AgentOutcome outcome = scrapyard.resolve(fixture.use(scrapyard, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isEqualTo(fixture.target);
            assertThat(message.text()).contains("swap the movement and combat values");
        });
    }

    // Hazorus (Stoneborn), dormant and unleashed

    @Test
    void hazorusDecodesTheTargetAndWhichSideOfTheCardIsUsed() {
        AgentModuleFixture fixture = new AgentModuleFixture("stoneborn");
        HazorusAgent hazorus = new HazorusAgent();

        assertThat(hazorus.decode(fixture.game, fixture.user, "pi_hacan|stonebornagentunleashed"))
                .contains(new HazorusAgent.Purge(fixture.target, HazorusAgent.UNLEASHED));
        assertThat(hazorus.decode(fixture.game, fixture.user, "pi_hacan|stonebornagentdormant"))
                .contains(new HazorusAgent.Purge(fixture.target, HazorusAgent.DORMANT));
        assertThat(hazorus.decode(fixture.game, fixture.user, "pi_hacan|naluagent"))
                .isEmpty();
        assertThat(hazorus.decode(fixture.game, fixture.user, "pi_hacan")).isEmpty();
    }

    @Test
    void hazorusOnYourselfNeedsARelicFragment() {
        AgentModuleFixture fixture = new AgentModuleFixture("stoneborn");
        HazorusAgent hazorus = new HazorusAgent();

        assertThat(hazorus.unusableReason(
                        fixture.game, fixture.user, new HazorusAgent.Purge(fixture.user, HazorusAgent.DORMANT)))
                .isPresent();
        assertThat(hazorus.unusableReason(
                        fixture.game, fixture.user, new HazorusAgent.Purge(fixture.target, HazorusAgent.DORMANT)))
                .isEmpty();
    }

    @Test
    void hazorusButtonsUseTheSideOfTheCardThePlayerHolds() {
        AgentModuleFixture fixture = new AgentModuleFixture("stoneborn");
        // Flip the card: the player now holds only the unleashed side.
        fixture.user.removeLeader(HazorusAgent.DORMANT);
        fixture.user.addLeader(HazorusAgent.UNLEASHED);

        assertThat(HazorusAgent.startTurnButton(fixture.user))
                .as("no fragments to purge")
                .isNull();

        fixture.user.addFragment("crf1");
        assertOwnedAgentButton(
                HazorusAgent.startTurnButton(fixture.user),
                fixture.user,
                "exhaustAgent_stonebornagentunleashed_stoneborn|stonebornagentunleashed");

        List<Button> buttons = HazorusAgent.targetButtons(fixture.game, fixture.user, HazorusAgent.UNLEASHED);
        assertThat(buttons)
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(
                        button, fixture.user, "exhaustAgent_stonebornagentunleashed_pi_hacan|stonebornagentunleashed"));
    }

    // Golgi (Morpha)

    @Test
    void golgiDecodesTargets() {
        new AgentModuleFixture("morpha").assertDecodesTargets(new GolgiAgent());
    }

    @Test
    void golgiNeedsAnotherPlayerAndAnActionCardInTheDiscardPile() {
        AgentModuleFixture fixture = new AgentModuleFixture("morpha");
        GolgiAgent golgi = new GolgiAgent();

        assertThat(golgi.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();
        assertThat(golgi.unusableReason(fixture.game, fixture.user, fixture.target))
                .as("empty discard pile")
                .isPresent();

        fixture.game.setDiscardActionCards(java.util.Map.of("sabo1", 1));
        assertThat(golgi.unusableReason(fixture.game, fixture.user, fixture.target))
                .isEmpty();
    }

    @Test
    void golgiTargetButtonsAreOwnedAgentButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("morpha");

        assertThat(GolgiAgent.targetButtons(fixture.game, fixture.user))
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(button, fixture.user, "exhaustAgent_morphaagent_pi_hacan"));
        assertOwnedAgentButton(GolgiAgent.cardsInfoButton(fixture.user), fixture.user, "useMorphaAgent");
    }

    // The Hope of the Cosmos (Thurviali)

    @Test
    void hopeDecodesTwoPlayersIncludingAProjectPiFaction() {
        AgentModuleFixture fixture = new AgentModuleFixture("thurviali");
        HopeOfTheCosmosAgent hope = new HopeOfTheCosmosAgent();

        assertThat(hope.decode(fixture.game, fixture.user, "thurviali|pi_hacan"))
                .contains(new HopeOfTheCosmosAgent.Pair(fixture.user, fixture.target));
        assertThat(hope.decode(fixture.game, fixture.user, "pi_hacan|thurviali"))
                .contains(new HopeOfTheCosmosAgent.Pair(fixture.target, fixture.user));
        assertThat(hope.decode(fixture.game, fixture.user, "pi_hacan")).isEmpty();
    }

    @Test
    void hopeNeedsTwoDifferentPlayersWithInfantryOnNonHomePlanets() {
        AgentModuleFixture fixture = new AgentModuleFixture("thurviali");
        HopeOfTheCosmosAgent hope = new HopeOfTheCosmosAgent();
        HopeOfTheCosmosAgent.Pair pair = new HopeOfTheCosmosAgent.Pair(fixture.user, fixture.target);

        assertThat(hope.unusableReason(
                        fixture.game, fixture.user, new HopeOfTheCosmosAgent.Pair(fixture.user, fixture.user)))
                .isPresent();
        assertThat(hope.unusableReason(fixture.game, fixture.user, pair))
                .as("nobody has infantry yet")
                .isPresent();
    }

    @Test
    void hopeFinalPickIsAnOwnedAgentButtonCarryingBothMovers() {
        AgentModuleFixture fixture = new AgentModuleFixture("thurviali");
        fixture.game.setStoredValue("thurvialiHopeSelectionMode_thurviali", "owner");
        // Both players control a planet holding their own infantry in tile 26 (Lodor), which is not a home system.
        fixture.user.addOwnedUnitByID("infantry");
        fixture.target.addOwnedUnitByID("infantry");
        fixture.user.addPlanet("lodor");
        fixture.target.addPlanet("lodor");
        fixture.tile.addUnit("lodor", Units.getUnitKey(UnitType.Infantry, fixture.user.getColorID()), 1);
        fixture.tile.addUnit("lodor", Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID()), 1);

        List<Button> buttons = HopeOfTheCosmosAgent.targetButtons(fixture.game, fixture.user, null);

        assertThat(buttons)
                .singleElement()
                .satisfies(button ->
                        assertOwnedAgentButton(button, fixture.user, "exhaustAgent_thurvialiagent_thurviali|pi_hacan"));
    }
}
