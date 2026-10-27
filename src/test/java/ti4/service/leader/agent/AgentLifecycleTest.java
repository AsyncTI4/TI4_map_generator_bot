package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgentLifecycleTest extends BaseTi4Test {

    private Game game;
    private Player mentak;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("agent-lifecycle-test");
        mentak = game.addPlayer("mentakUser", "Mentak Player");
        mentak.setFaction(game, "mentak");
        mentak.setColor("red");
    }

    @Test
    void anExhaustedAgentIsRefused() {
        Leader agent = mentak.getLeader("mentakagent").orElseThrow();
        agent.setExhausted(true);
        int actionCards = mentak.getAcCount();

        assertThat(AgentLifecycle.refuseIfExhausted(null, mentak, agent)).isTrue();
        assertThat(AgentLifecycle.use(game, mentak, "mentakagent", "red", null)).isFalse();
        assertThat(mentak.getAcCount()).isEqualTo(actionCards);
    }

    @Test
    void aReadiedAgentIsNotRefused() {
        Leader agent = mentak.getLeader("mentakagent").orElseThrow();

        assertThat(AgentLifecycle.refuseIfExhausted(null, mentak, agent)).isFalse();
    }

    @Test
    void anUnresolvablePayloadLeavesTheAgentReadied() {
        assertThat(AgentLifecycle.use(game, mentak, "mentakagent", "", null)).isFalse();
        assertThat(mentak.getLeader("mentakagent").orElseThrow().isExhausted()).isFalse();
    }

    @Test
    void inProcessUseRequiresAModule() {
        assertThatThrownBy(() -> AgentLifecycle.use(game, mentak, "hacanagent", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesTheRealCardByItsDisplayName() {
        assertThat(AgentLifecycle.agentName("Clodho, the Ilyxum", false)).isEqualTo("Clodho, the Ilyxum agent");
    }

    @Test
    void namesAYssarilCopyAsCleverClever() {
        assertThat(AgentLifecycle.agentName("Clodho, the Ilyxum", true))
                .isEqualTo("Clever Clever Clodho, the Ilyxum/Yssaril agent");
    }

    @Test
    void theYssarilAgentStandingInForAnotherAgentIsACopy() {
        assertThat(AgentLifecycle.isYssarilCopy("yssarilagent", "augersagent")).isTrue();
    }

    @Test
    void theYssarilAgentUsedAsItselfIsNotACopy() {
        assertThat(AgentLifecycle.isYssarilCopy("yssarilagent", "yssarilagent")).isFalse();
        assertThat(AgentLifecycle.isYssarilCopy("augersagent", "augersagent")).isFalse();
    }
}
