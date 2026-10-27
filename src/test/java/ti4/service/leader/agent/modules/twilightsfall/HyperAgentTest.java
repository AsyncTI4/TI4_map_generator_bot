package ti4.service.leader.agent.modules.twilightsfall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class HyperAgentTest extends BaseTi4Test {

    @Test
    void decodesFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        HyperAgent hyper = new HyperAgent();

        assertThat(hyper.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
        assertThat(hyper.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_COLOR))
                .contains(fixture.target);
        assertThat(hyper.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(hyper.decode(fixture.game, fixture.user, "nobody")).isEmpty();
    }

    @Test
    void hyperGenomeStealsATradeGoodAndBothDraw() {
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
    void hyperGenomeIsAnnouncedAsTheGenome() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        HyperAgent hyper = new HyperAgent();

        assertThat(hyper.exhaustAnnouncement(fixture.use(hyper, fixture.target)))
                .endsWith(" has exhausted the _Hyper Genome_ agent.");
    }
}
