package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.VaylerianAgent.DrawFor;
import ti4.service.leader.agent.modules.VaylerianAgent.PickTarget;
import ti4.testUtils.BaseTi4Test;

class CardDrawAgentsTest extends BaseTi4Test {

    @Test
    void cymiaeDrawsOneActionCardForTheTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("cymiae");
        int before = fixture.target.getAcCount();

        AgentOutcome outcome = new CymiaeAgent().resolve(fixture.use(new CymiaeAgent(), fixture.target));

        assertThat(fixture.target.getAcCount()).isEqualTo(before + 1);
        assertThat(outcome.messages()).isEmpty();
    }

    @Test
    void mentakDrawsForBothPlayers() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        int userBefore = fixture.user.getAcCount();
        int targetBefore = fixture.target.getAcCount();

        new MentakAgent().resolve(fixture.use(new MentakAgent(), fixture.target));

        assertThat(fixture.user.getAcCount()).isEqualTo(userBefore + 1);
        assertThat(fixture.target.getAcCount()).isEqualTo(targetBefore + 1);
    }

    @Test
    void hyperGenomeStealsATradeGoodAndBothDraw() {
        // Yssaril holds no Hyper Genome, so the lookup falls back to the Yssaril agent: a Clever Clever copy.
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");
        fixture.target.setTg(2);
        fixture.user.setTg(0);
        HyperAgent hyper = new HyperAgent();

        AgentOutcome outcome = hyper.resolve(fixture.use(hyper, fixture.target));

        assertThat(fixture.target.getTg()).isEqualTo(1);
        assertThat(fixture.user.getTg()).isEqualTo(1);
        assertThat(outcome.messages()).hasSize(2);
        assertThat(outcome.messages().get(0).recipient()).isSameAs(fixture.user);
        assertThat(outcome.messages().get(0).text()).contains("took 1 TG");
        assertThat(outcome.messages().get(1).recipient()).isSameAs(fixture.target);
        assertThat(outcome.messages().get(1).text()).contains("gave 1 TG");
    }

    @Test
    void hyperGenomeNoLongerPutsTheTargetsSchemingIntoTheUsersMessage() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");
        // Yssaril has Scheming by default; take it away so only the target has it.
        fixture.user.removeAbility("scheming");
        fixture.target.addAbility("scheming");
        HyperAgent hyper = new HyperAgent();

        AgentOutcome outcome = hyper.resolve(fixture.use(hyper, fixture.target));

        assertThat(outcome.messages().get(0).text())
                .startsWith(fixture.user.getRepresentation() + " drew 1 action card");
        assertThat(outcome.messages().get(1).text()).contains("drew 2 action cards (Scheming)");
    }

    @Test
    void hyperGenomeIsNamedAsAGenome() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");
        HyperAgent hyper = new HyperAgent();

        assertThat(hyper.exhaustAnnouncement(fixture.use(hyper, fixture.target)))
                .endsWith(" has exhausted the Clever Clever _Hyper Genome_.");
    }

    @Test
    void vaylerianDrawsForAChosenTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");
        int before = fixture.target.getAcCount();
        VaylerianAgent vaylerian = new VaylerianAgent();

        vaylerian.resolve(fixture.use(vaylerian, new DrawFor(fixture.target)));

        assertThat(fixture.target.getAcCount()).isEqualTo(before + 1);
    }

    @Test
    void vaylerianWithoutATargetOffersAPlayerPicker() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");
        VaylerianAgent vaylerian = new VaylerianAgent();

        AgentOutcome outcome = vaylerian.resolve(fixture.use(vaylerian, new PickTarget()));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons())
                    .isNotEmpty()
                    .allSatisfy(button -> assertThat(button.getCustomId()).startsWith("vaylerianAgent_"));
        });
    }
}
