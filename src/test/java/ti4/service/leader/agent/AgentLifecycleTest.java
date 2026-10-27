package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentLifecycleTest {

    @Test
    void namesTheRealCardByItsDisplayName() {
        assertThat(AgentLifecycle.agentName("Clodho, the Ilyxum", "augersagent", "augersagent"))
                .isEqualTo("Clodho, the Ilyxum agent");
    }

    @Test
    void namesAYssarilAgentStandingInForAnotherAgentAsCleverClever() {
        assertThat(AgentLifecycle.agentName("Clodho, the Ilyxum", "yssarilagent", "augersagent"))
                .isEqualTo("Clever Clever Clodho, the Ilyxum/Yssaril agent");
    }

    @Test
    void theYssarilAgentUsedAsItselfIsNotACopy() {
        assertThat(AgentLifecycle.agentName("Ssruu, the Yssaril", "yssarilagent", "yssarilagent"))
                .isEqualTo("Ssruu, the Yssaril agent");
    }
}
