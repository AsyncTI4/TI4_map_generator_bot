package ti4.service.leader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ti4.service.leader.agent.modules.AgentModuleFixture.NOT_AN_AGENT_MODULE;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.ExhaustOnlyAgent;
import ti4.service.leader.agent.modules.ExhaustOnlyAgents;
import ti4.service.leader.agent.modules.homebrew.AugersAgent;
import ti4.service.leader.agent.modules.homebrew.HomebrewExhaustOnlyAgents;
import ti4.testUtils.BaseTi4Test;

class LeaderAgentsTest extends BaseTi4Test {

    @Test
    void everyModuleClaimsARealAgentLeader() {
        for (AgentModule<?> module : LeaderAgents.all()) {
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
        assertThat(LeaderAgents.findAgent("augersagent")).containsInstanceOf(AugersAgent.class);
        assertThat(LeaderAgents.findAgent("AugersAgent")).containsInstanceOf(AugersAgent.class);
        // Unmigrated agents must fall through to the legacy exhaustAgent branches.
        assertThat(LeaderAgents.findAgent(NOT_AN_AGENT_MODULE)).isEmpty();
        assertThat(LeaderAgents.findAgent(null)).isEmpty();
    }

    @Test
    void refusesTwoModulesForTheSameAgent() {
        assertThatThrownBy(() -> LeaderAgents.indexById(List.of(new AugersAgent(), new AugersAgent())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyExhaustOnlyAgentIsRegistered() {
        // The registry is the one place that lists official and homebrew agents together.
        for (ExhaustOnlyAgent agent : ExhaustOnlyAgents.ALL) {
            assertThat(LeaderAgents.findAgent(agent.agentId())).contains(agent);
        }
        for (ExhaustOnlyAgent agent : HomebrewExhaustOnlyAgents.ALL) {
            assertThat(LeaderAgents.findAgent(agent.agentId())).contains(agent);
        }
    }
}
