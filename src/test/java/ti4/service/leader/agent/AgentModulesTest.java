package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;
import ti4.service.leader.agent.modules.AugersAgent;
import ti4.testUtils.BaseTi4Test;

class AgentModulesTest extends BaseTi4Test {

    @Test
    void everyModuleClaimsARealAgentLeader() {
        for (AgentModule<?> module : AgentModules.ALL) {
            LeaderModel leader = Mapper.getLeader(module.agentId());

            assertThat(leader).as(module.agentId()).isNotNull();
            assertThat(leader.getType()).as(module.agentId()).isEqualTo(Constants.AGENT);
            for (String alias : module.aliasIds()) {
                assertThat(Mapper.getLeader(alias)).as(alias).isNotNull();
            }
        }
    }

    @Test
    void findsModulesByAgentIdIgnoringCase() {
        assertThat(AgentModules.find("augersagent")).containsInstanceOf(AugersAgent.class);
        assertThat(AgentModules.find("AugersAgent")).containsInstanceOf(AugersAgent.class);
        // Unmigrated agents must fall through to the legacy exhaustAgent branches.
        assertThat(AgentModules.find("hacanagent")).isEmpty();
        assertThat(AgentModules.find(null)).isEmpty();
    }

    @Test
    void refusesTwoModulesForTheSameAgent() {
        assertThatThrownBy(() -> AgentModules.indexById(List.of(new AugersAgent(), new AugersAgent())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void namesTheAgentOnceForBothTheRealCardAndAYssarilCopy() {
        assertThat(AgentNames.cardName("Clodho, the Ilyxum", false)).isEqualTo("Clodho, the Ilyxum agent");
        assertThat(AgentNames.cardName("Clodho, the Ilyxum", true))
                .isEqualTo("Clever Clever Clodho, the Ilyxum/Yssaril agent");
    }

    @Test
    void onlyAnExhaustedYssarilAgentStandingInForAnotherAgentIsACopy() {
        assertThat(AgentNames.isYssarilCopy("yssarilagent", "augersagent")).isTrue();
        assertThat(AgentNames.isYssarilCopy("augersagent", "augersagent")).isFalse();
        assertThat(AgentNames.isYssarilCopy("yssarilagent", "yssarilagent")).isFalse();
    }
}
