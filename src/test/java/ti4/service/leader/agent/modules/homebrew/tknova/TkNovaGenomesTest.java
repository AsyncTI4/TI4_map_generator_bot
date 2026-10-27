package ti4.service.leader.agent.modules.homebrew.tknova;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class TkNovaGenomesTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;

    @Test
    void genomesAreOfferedOnlyToTheirHolderAndKeepTheirIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        assertThat(DeploymentGenomeAgent.startOfTurnOffer(fixture.user)).isEmpty();
        assertThat(SwarmGenomeAgent.endOfTurnOffer(fixture.user)).isEmpty();
        assertThat(SplittingGenomeAgent.combatOffer(fixture.game, fixture.user, fixture.user, ACTIVE))
                .isEmpty();
        fixture.user.addLeader("tknovadeploymentagent");
        fixture.user.addLeader("tknovaswarmagent");
        fixture.user.addLeader("tknovasplittingagent");

        assertThat(DeploymentGenomeAgent.startOfTurnOffer(fixture.user))
                .hasValueSatisfying(
                        button -> assertOwnedAgentButton(button, fixture.user, "exhaustAgent_tknovadeploymentagent"));
        assertThat(SwarmGenomeAgent.endOfTurnOffer(fixture.user))
                .hasValueSatisfying(
                        button -> assertOwnedAgentButton(button, fixture.user, "exhaustAgent_tknovaswarmagent"));
        assertThat(SplittingGenomeAgent.combatOffer(fixture.game, fixture.user, fixture.target, ACTIVE))
                .hasValueSatisfying(
                        button -> assertOwnedAgentButton(button, fixture.user, "tknovaSplittingOffer_" + ACTIVE));
    }

    @Test
    void splittingAsksItsHolderWhoseUnitWasDestroyed() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        fixture.user.addLeader("tknovasplittingagent");

        List<String> owners = SplittingGenomeAgent.ownerButtons(fixture.game, fixture.user, ACTIVE).stream()
                .map(button -> {
                    assertOwnedBy(button, fixture.user);
                    return ComponentIdEnvelope.decode(button.getCustomId()).handlerId();
                })
                .toList();

        assertThat(owners)
                .contains("exhaustAgent_tknovasplittingagent_" + ACTIVE + "_" + AgentModuleFixture.TARGET_FACTION);
    }

    @Test
    void splittingIsNotOfferedToAFogOfWarHolderWhoIsNotTheFirstCombatant() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        fixture.user.addLeader("tknovasplittingagent");
        fixture.game.setFowMode(true);

        assertThat(SplittingGenomeAgent.combatOffer(fixture.game, fixture.user, fixture.target, ACTIVE))
                .isEmpty();
        assertThat(SplittingGenomeAgent.combatOffer(fixture.game, fixture.user, fixture.user, ACTIVE))
                .isPresent();
    }

    @Test
    void deploymentLetsThePlayerPickAPlanetToMoveGroundForcesTo() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        DeploymentGenomeAgent deployment = new DeploymentGenomeAgent();
        assertThat(deployment.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();
        fixture.user.addPlanet(LODOR);

        AgentOutcome outcome = deployment.resolve(fixture.use(deployment, fixture.user));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.text()).contains("the _Deployment Genome_").doesNotContain("agent");
            assertThat(handlerIds(message)).containsExactly("transitDiodes_" + LODOR, "deleteButtons");
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, fixture.user));
        });
    }

    @Test
    void swarmLetsTheTargetPlaceTwoInfantry() {
        AgentModuleFixture fixture = new AgentModuleFixture("sardakk");
        SwarmGenomeAgent swarm = new SwarmGenomeAgent();
        assertThat(swarm.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(swarm.unusableReason(fixture.game, fixture.user, fixture.target))
                .isPresent();
        fixture.target.addPlanet(LODOR);

        AgentOutcome outcome = swarm.resolve(fixture.use(swarm, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.buttons())
                    .hasSizeGreaterThan(1)
                    .allSatisfy(button -> assertOwnedBy(button, fixture.target));
        });
    }

    @Test
    void splittingDefaultsToItsHolderAndKeepsInfantryInTwilightsFall() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        fixture.game.setTwilightsFallMode(true);
        SplittingGenomeAgent splitting = new SplittingGenomeAgent();

        assertThat(splitting.decode(fixture.game, fixture.user, ACTIVE))
                .contains(new SplittingGenomeAgent.Replacement(fixture.tile, fixture.user));
        assertThat(splitting.decode(fixture.game, fixture.user, ACTIVE + "_" + AgentModuleFixture.TARGET_FACTION))
                .contains(new SplittingGenomeAgent.Replacement(fixture.tile, fixture.target));
        assertThat(splitting.decode(fixture.game, fixture.user, "999")).isEmpty();

        AgentOutcome outcome = splitting.resolve(
                fixture.use(splitting, new SplittingGenomeAgent.Replacement(fixture.tile, fixture.user)));

        // Unlike the Yin agent's Twilight's Fall text, this genome may place infantry on a planet.
        assertThat(handlerIds(outcome.messages().getFirst()))
                .containsExactly("placeOneNDone_skipbuild_2ff_" + ACTIVE, "placeOneNDone_skipbuild_2gf_" + LODOR);
    }

    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
