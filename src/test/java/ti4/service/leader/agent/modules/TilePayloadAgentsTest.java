package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.modules.L1z1xAgent.OfferPlanets;
import ti4.service.leader.agent.modules.L1z1xAgent.ReplaceInfantry;
import ti4.testUtils.BaseTi4Test;

class TilePayloadAgentsTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;

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
            assertThat(handlerIds(message))
                    .containsExactly("placeOneNDone_skipbuild_2ff_" + ACTIVE, "placeOneNDone_skipbuild_2gf_" + LODOR);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, fixture.target));
        });
    }

    @Test
    void yinOffersOnlyItsHolderTheChoiceOfWhoseUnitWasDestroyed() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        YinAgent yin = new YinAgent();

        assertOwnedAgentButton(YinAgent.offerButton(fixture.user, ACTIVE), fixture.user, "yinagent_" + ACTIVE);
        List<Button> owners = YinAgent.ownerButtons(fixture.game, fixture.user, ACTIVE);

        assertThat(owners).hasSize(2).allSatisfy(button -> assertOwnedBy(button, fixture.user));
        // Each choice is a full agent press: the payload names the system and the owner.
        assertThat(owners)
                .extracting(button ->
                        ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .contains("exhaustAgent_yinagent_" + ACTIVE + "_" + AgentModuleFixture.TARGET_FACTION);
        assertThat(yin.decode(fixture.game, fixture.user, ACTIVE + "_" + AgentModuleFixture.TARGET_FACTION))
                .isPresent();
    }

    @Test
    void argentPlacesGroundForcesOnPlanetsTheProducerControlsInAndAroundTheSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("argent");
        ArgentAgent argent = new ArgentAgent();
        fixture.user.addPlanet(LODOR);

        assertThat(argent.decode(fixture.game, fixture.user, ACTIVE)).contains(fixture.tile);
        assertThat(argent.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(argent.decode(fixture.game, fixture.user, "999")).isEmpty();
        assertOwnedAgentButton(
                ArgentAgent.button(fixture.user, fixture.tile), fixture.user, "exhaustAgent_argentagent_" + ACTIVE);

        AgentOutcome outcome = argent.resolve(fixture.use(argent, fixture.tile));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(handlerIds(message)).containsExactly("place_infantry_lodor", "place_mech_lodor", null);
        });
    }

    @Test
    void mercerReadsThePlanetAfterThePosition() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        MercerAgent mercer = new MercerAgent();

        assertThat(mercer.decode(fixture.game, fixture.user, ACTIVE + "_" + LODOR))
                .contains(LODOR);
        assertThat(mercer.decode(fixture.game, fixture.user, ACTIVE + "_nowhere"))
                .isEmpty();
        assertThat(mercer.decode(fixture.game, fixture.user, "")).isEmpty();
        assertOwnedAgentButton(
                MercerAgent.button(fixture.user, ACTIVE, LODOR, "Lodor"),
                fixture.user,
                "exhaustAgent_nomadagentmercer_" + ACTIVE + "_" + LODOR);
    }

    @Test
    void mercerOffersTheGroundForcesThatCanMoveToThePlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        MercerAgent mercer = new MercerAgent();
        Tile elsewhere = new Tile("27", "102");
        fixture.game.setTile(elsewhere);
        elsewhere.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Infantry, fixture.user.getColorID()), 1);

        AgentOutcome outcome = mercer.resolve(fixture.use(mercer, LODOR));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons()).isNotEmpty();
        });
    }

    @Test
    void mercerWithNoGroundForcesTellsTheUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        MercerAgent mercer = new MercerAgent();

        AgentOutcome outcome = mercer.resolve(fixture.use(mercer, LODOR));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons()).isEmpty();
        });
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
        assertOwnedAgentButton(
                L1z1xAgent.button(fixture.user, ACTIVE, LODOR, "Lodor"),
                fixture.user,
                "exhaustAgent_l1z1xagent_" + ACTIVE + "_" + LODOR);
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

        var lodor = fixture.tile.getUnitHolders().get(LODOR);
        assertThat(lodor.getUnitCount(UnitType.Infantry, fixture.user.getColor()))
                .isEqualTo(1);
        assertThat(lodor.getUnitCount(UnitType.Mech, fixture.user.getColor())).isEqualTo(1);
        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(fixture.user));
    }

    @Test
    void l1z1xOffersOnlyInfantryInTheActiveSystemWhenThereIsAny() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        L1z1xAgent l1z1x = new L1z1xAgent();
        Tile elsewhere = new Tile("27", "102");
        fixture.game.setTile(elsewhere);
        Units.UnitKey infantry = Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID());
        elsewhere.addUnit(Constants.SPACE, infantry, 1);
        fixture.tile.addUnit(LODOR, infantry, 1);

        AgentOutcome outcome = l1z1x.resolve(fixture.use(l1z1x, new OfferPlanets(fixture.target)));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(handlerIds(message)).containsExactly("mitoMechPlacement_" + ACTIVE + "_" + LODOR + "_l1z1x");
        });
    }

    @Test
    void l1z1xWithNoInfantryToReplaceTellsTheUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        L1z1xAgent l1z1x = new L1z1xAgent();

        AgentOutcome outcome = l1z1x.resolve(fixture.use(l1z1x, new OfferPlanets(fixture.target)));

        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(fixture.user));
    }

    private static void assertOwnedBy(Button button, Player owner) {
        assertThat(ComponentIdEnvelope.decode(button.getCustomId()).ownerFaction())
                .isEqualTo(owner.getFaction());
    }

    // Unowned buttons (like "Done") have no handler in the owner envelope, so they map to null.
    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()))
                .map(envelope -> envelope.ownerFaction() == null ? null : envelope.handlerId())
                .toList();
    }
}
