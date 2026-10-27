package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.testUtils.BaseTi4Test;

class PromptAgentsTest extends BaseTi4Test {

    @Test
    void naazWithoutATargetLetsTheUserExplore() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");
        NaazAgent naaz = new NaazAgent();

        assertThat(naaz.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(naaz.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
        AgentOutcome outcome = naaz.resolve(fixture.use(naaz, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.text()).contains("explore");
        });
    }

    @Test
    void jolNarSendsTheTargetTheInfantryRemovalPrompt() {
        AgentModuleFixture fixture = new AgentModuleFixture("jolnar");
        JolNarAgent jolNar = new JolNarAgent();

        AgentOutcome outcome = jolNar.resolve(fixture.use(jolNar, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.text()).contains("remove infantry");
            // Even without infantry the "Done" button is offered.
            assertThat(message.buttons()).isNotEmpty();
        });
    }

    @Test
    void empyreanRemembersTheTargetsTokensBeforeTheyGainMore() {
        AgentModuleFixture fixture = new AgentModuleFixture("empyrean");
        EmpyreanAgent empyrean = new EmpyreanAgent();

        AgentOutcome outcome = empyrean.resolve(fixture.use(empyrean, fixture.target));

        assertThat(fixture.game.getStoredValue("originalCCsFor" + AgentModuleFixture.TARGET_FACTION))
                .isEqualTo(fixture.target.getCCRepresentation());
        assertThat(outcome.messages().getFirst().buttons()).isNotEmpty();
    }

    @Test
    void bastionNeedsATargetAndAnActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("bastion");
        BastionAgent bastion = new BastionAgent();

        fixture.assertDecodesTargets(bastion);
        assertThat(bastion.unusableReason(fixture.game, fixture.user, fixture.target))
                .isEmpty();
        fixture.game.setActiveSystem("");
        assertThat(bastion.unusableReason(fixture.game, fixture.user, fixture.target))
                .isPresent();
    }

    @Test
    void ralNelDrawsTwoCardsAndKeepsTheChoicePrivate() {
        AgentModuleFixture fixture = new AgentModuleFixture("ralnel");
        RalNelAgent ralNel = new RalNelAgent();
        int actionCards = fixture.user.getAcCount();

        AgentOutcome outcome = ralNel.resolve(fixture.use(ralNel, fixture.user));

        assertThat(fixture.user.getAcCount()).isEqualTo(actionCards + 2);
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.secret()).isTrue();
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons()).hasSize(2);
        });
    }

    @Test
    void promptAgentButtonsAreOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");
        var holder = fixture.user;

        assertOwnedAgentButton(NaazAgent.button(holder), holder, "exhaustAgent_naazagent_naaz");
        assertOwnedAgentButton(JolNarAgent.button(holder), holder, "exhaustAgent_jolnaragent");
        assertOwnedAgentButton(EmpyreanAgent.button(holder), holder, "exhaustAgent_empyreanagent");
    }
}
