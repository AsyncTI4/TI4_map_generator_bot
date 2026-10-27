package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.modules.L1z1xAgent.OfferPlanets;
import ti4.service.leader.agent.modules.L1z1xAgent.ReplaceInfantry;
import ti4.testUtils.BaseTi4Test;

class TilePayloadAgentsTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String NEW_ALBION = "newalbion";
    private static final String STARPOINT = "starpoint";
    private static final String VEFUTII = "vefutii";
    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;
    // Ring 1 neighbours are adjacent; 104 sits across Mecatol Rex from 101.
    private static final String ADJACENT = "102";
    private static final String FAR_AWAY = "104";

    @Test
    void naaluDecodesItsTargetLikeTheOtherTargetedAgents() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");

        fixture.assertDecodesTargets(new NaaluAgent());
    }

    @Test
    void naaluLetsTheChosenPlayerActivateASystemWithoutACommandToken() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        NaaluAgent naalu = new NaaluAgent();

        AgentOutcome outcome = naalu.resolve(fixture.use(naalu, fixture.target));

        assertThat(fixture.game.isNaaluAgent()).isTrue();
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.namesRecipient()).isTrue();
            assertThat(message.buttons()).isNotEmpty();
            assertThat(message.text()).contains("Z'eu, the Naalu agent").contains("Mecatol Rex");
        });
    }

    @Test
    void naaluInFogOfWarHidesItsNameAndTheMapLayout() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        fixture.game.setFowMode(true);
        NaaluAgent naalu = new NaaluAgent();

        AgentOutcome outcome = naalu.resolve(fixture.use(naalu, fixture.target));

        assertThat(outcome.messages().getFirst().text())
                .contains("an agent")
                .doesNotContain("Naalu")
                .doesNotContain("Mecatol Rex");
    }

    @Test
    void yinReadsTheSystemThenAProjectPiOwner() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        YinAgent yin = new YinAgent();

        assertThat(yin.decode(fixture.game, fixture.user, ACTIVE + "_" + AgentModuleFixture.TARGET_FACTION))
                .contains(new YinAgent.Replacement(fixture.tile, fixture.target));
        assertThat(yin.decode(fixture.game, fixture.user, ACTIVE + "_" + AgentModuleFixture.TARGET_COLOR))
                .contains(new YinAgent.Replacement(fixture.tile, fixture.target));
        assertThat(yin.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(yin.decode(fixture.game, fixture.user, ACTIVE)).isEmpty();
        assertThat(yin.decode(fixture.game, fixture.user, "999_" + AgentModuleFixture.TARGET_FACTION))
                .isEmpty();
    }

    @Test
    void yinLetsTheOwnerOfTheDestroyedUnitPlaceFightersOrInfantry() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        YinAgent yin = new YinAgent();

        AgentOutcome outcome = yin.resolve(fixture.use(yin, new YinAgent.Replacement(fixture.tile, fixture.target)));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.text()).contains("Brother Milor, the Yin agent");
            assertThat(handlerIds(message))
                    .containsExactly("placeOneNDone_skipbuild_2ff_" + ACTIVE, "placeOneNDone_skipbuild_2gf_" + LODOR);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, fixture.target));
        });
    }

    @Test
    void yinInFogOfWarDoesNotNameItselfToTheOwner() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        fixture.game.setFowMode(true);
        YinAgent yin = new YinAgent();

        AgentOutcome outcome = yin.resolve(fixture.use(yin, new YinAgent.Replacement(fixture.tile, fixture.target)));

        assertThat(outcome.messages().getFirst().text()).contains("an agent").doesNotContain("Yin");
    }

    @Test
    void yinInTwilightsFallOnlyPlacesFightersAndUsesTheGenomeName() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        fixture.game.setTwilightsFallMode(true);

        // The Twilight's Fall card text only allows fighters.
        assertThat(YinAgent.placementButtons(fixture.game, fixture.target, fixture.tile))
                .singleElement()
                .satisfies(button -> assertThat(button.getLabel()).isEqualTo("Place 2 Fighters"));
        assertThat(YinAgent.offerButton(fixture.user, ACTIVE).getLabel()).isEqualTo("Use Splitting Genome");
    }

    @Test
    void yinOffersOnlyItsHolderTheChoiceOfWhoseUnitWasDestroyed() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");

        assertOwnedAgentButton(YinAgent.offerButton(fixture.user, ACTIVE), fixture.user, "yinagent_" + ACTIVE);
        List<Button> owners = YinAgent.ownerButtons(fixture.game, fixture.user, ACTIVE);

        assertThat(owners).hasSize(2).allSatisfy(button -> assertOwnedBy(button, fixture.user));
        // Each choice is a full agent press: the payload names the system and the owner.
        assertThat(owners)
                .extracting(button ->
                        ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .contains("exhaustAgent_yinagent_" + ACTIVE + "_" + AgentModuleFixture.TARGET_FACTION);
    }

    @Test
    void argentButtonIsOwnedAndCarriesTheProductionSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("argent");
        ArgentAgent argent = new ArgentAgent();

        assertThat(argent.decode(fixture.game, fixture.user, ACTIVE)).contains(fixture.tile);
        assertThat(argent.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(argent.decode(fixture.game, fixture.user, "999")).isEmpty();
        assertOwnedAgentButton(
                ArgentAgent.button(fixture.user, fixture.tile), fixture.user, "exhaustAgent_argentagent_" + ACTIVE);
    }

    @Test
    void argentPlacesOnlyOnControlledPlanetsInAndAroundTheSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("argent");
        ArgentAgent argent = new ArgentAgent();
        Tile adjacent = new Tile("27", ADJACENT);
        Tile farAway = new Tile("20", FAR_AWAY);
        fixture.game.setTile(adjacent);
        fixture.game.setTile(farAway);
        assertThat(FoWHelper.getAdjacentTiles(fixture.game, ACTIVE, fixture.user, false))
                .contains(ADJACENT)
                .doesNotContain(FAR_AWAY);
        fixture.user.addPlanet(LODOR);
        fixture.user.addPlanet(NEW_ALBION);
        fixture.user.addPlanet(VEFUTII);

        AgentOutcome outcome = argent.resolve(fixture.use(argent, fixture.tile));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(handlerIds(message))
                    .containsExactlyInAnyOrder(
                            "place_infantry_" + LODOR,
                            "place_mech_" + LODOR,
                            "place_infantry_" + NEW_ALBION,
                            "place_mech_" + NEW_ALBION,
                            "deleteButtons_spitItOut")
                    .noneMatch(id -> id.endsWith(STARPOINT) || id.endsWith(VEFUTII));
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, fixture.user));
        });
    }

    @Test
    void mercerOffersEachControlledPlanetInTheActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        MercerAgent mercer = new MercerAgent();
        fixture.user.addPlanet(LODOR);

        assertThat(mercer.decode(fixture.game, fixture.user, ACTIVE + "_" + LODOR))
                .contains(LODOR);
        assertThat(mercer.decode(fixture.game, fixture.user, ACTIVE + "_nowhere"))
                .isEmpty();
        assertThat(mercer.decode(fixture.game, fixture.user, "999_" + LODOR)).isEmpty();
        assertThat(mercer.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(MercerAgent.buttons(fixture.game, fixture.user))
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(
                        button, fixture.user, "exhaustAgent_nomadagentmercer_" + ACTIVE + "_" + LODOR));
    }

    @Test
    void mercerWithoutAnActiveSystemOffersNothing() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        fixture.user.addPlanet(LODOR);
        fixture.game.setActiveSystem("");

        assertThat(MercerAgent.buttons(fixture.game, fixture.user)).isEmpty();
    }

    @Test
    void mercerOffersOwnedMovesForTheUsersGroundForces() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        MercerAgent mercer = new MercerAgent();
        Tile elsewhere = new Tile("27", ADJACENT);
        fixture.game.setTile(elsewhere);
        elsewhere.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Infantry, fixture.user.getColorID()), 1);

        assertThat(mercer.unusableReason(fixture.game, fixture.user, LODOR)).isEmpty();
        AgentOutcome outcome = mercer.resolve(fixture.use(mercer, LODOR));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.text()).contains("up to 2 ground forces");
            assertThat(handlerIds(message))
                    .containsExactly(
                            "mercerMove_" + LODOR + "_" + ADJACENT + "_" + Constants.SPACE + "_infantry",
                            "deleteButtons");
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, fixture.user));
        });
    }

    @Test
    void mercerIsRefusedBeforeExhaustingWhenNoGroundForcesCanMove() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");

        assertThat(new MercerAgent().unusableReason(fixture.game, fixture.user, LODOR))
                .hasValueSatisfying(reason -> assertThat(reason).contains("no ground forces"));
    }

    @Test
    void l1z1xTellsTargetsFromPositionsAndPlanets() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        L1z1xAgent l1z1x = new L1z1xAgent();

        assertThat(l1z1x.decode(fixture.game, fixture.user, "")).contains(new OfferPlanets(fixture.user));
        // The old split("_") parsing read "pi_hacan" as position "pi", planet "hacan".
        assertThat(l1z1x.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(new OfferPlanets(fixture.target));
        assertThat(l1z1x.decode(fixture.game, fixture.user, ACTIVE + "_" + LODOR))
                .contains(new ReplaceInfantry(fixture.tile, LODOR));
        assertThat(l1z1x.decode(fixture.game, fixture.user, "999_" + LODOR)).isEmpty();
    }

    @Test
    void l1z1xOffersControlledPlanetsWithInfantryInTheGivenSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        fixture.user.addPlanet(LODOR);
        assertThat(L1z1xAgent.buttons(fixture.game, fixture.tile, fixture.user)).isEmpty();
        fixture.tile.addUnit(LODOR, Units.getUnitKey(UnitType.Infantry, fixture.user.getColorID()), 1);

        assertThat(L1z1xAgent.buttons(fixture.game, fixture.tile, fixture.user))
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(
                        button, fixture.user, "exhaustAgent_l1z1xagent_" + ACTIVE + "_" + LODOR));
        assertThat(L1z1xAgent.buttons(fixture.game, null, fixture.user)).isEmpty();
    }

    @Test
    void l1z1xReplacesOneOfTheUsersInfantryWithAMech() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        L1z1xAgent l1z1x = new L1z1xAgent();
        ReplaceInfantry replace = new ReplaceInfantry(fixture.tile, LODOR);
        assertThat(l1z1x.unusableReason(fixture.game, fixture.user, replace)).isPresent();
        fixture.tile.addUnit(LODOR, Units.getUnitKey(UnitType.Infantry, fixture.user.getColorID()), 2);
        assertThat(l1z1x.unusableReason(fixture.game, fixture.user, replace)).isEmpty();

        AgentOutcome outcome = l1z1x.resolve(fixture.use(l1z1x, replace));

        UnitHolder lodor = fixture.tile.getUnitHolders().get(LODOR);
        assertThat(lodor.getUnitCount(UnitType.Infantry, fixture.user.getColor()))
                .isEqualTo(1);
        assertThat(lodor.getUnitCount(UnitType.Mech, fixture.user.getColor())).isEqualTo(1);
        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(fixture.user));
    }

    @Test
    void l1z1xOffersAnotherPlayerOnlyInfantryInTheActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        fixture.game.setFowMode(true);
        L1z1xAgent l1z1x = new L1z1xAgent();
        Tile elsewhere = new Tile("27", ADJACENT);
        fixture.game.setTile(elsewhere);
        Units.UnitKey infantry = Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID());
        elsewhere.addUnit(Constants.SPACE, infantry, 1);
        OfferPlanets offer = new OfferPlanets(fixture.target);

        // Infantry outside the active system does not count, so the agent is refused before it exhausts.
        assertThat(l1z1x.unusableReason(fixture.game, fixture.user, offer)).isPresent();
        fixture.tile.addUnit(LODOR, infantry, 1);
        assertThat(l1z1x.unusableReason(fixture.game, fixture.user, offer)).isEmpty();

        AgentOutcome outcome = l1z1x.resolve(fixture.use(l1z1x, offer));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.text()).contains("an agent").doesNotContain("L1Z1X");
            assertThat(handlerIds(message)).containsExactly("mitoMechPlacement_" + ACTIVE + "_" + LODOR + "_l1z1x");
        });
    }

    @Test
    void l1z1xWithoutAnActiveSystemOffersInfantryAnywhere() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        L1z1xAgent l1z1x = new L1z1xAgent();
        fixture.game.setActiveSystem("");
        Tile elsewhere = new Tile("27", ADJACENT);
        fixture.game.setTile(elsewhere);
        elsewhere.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID()), 1);

        AgentOutcome outcome = l1z1x.resolve(fixture.use(l1z1x, new OfferPlanets(fixture.target)));

        assertThat(handlerIds(outcome.messages().getFirst()))
                .containsExactly("mitoMechPlacement_" + ADJACENT + "_" + Constants.SPACE + "_l1z1x");
    }

    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
