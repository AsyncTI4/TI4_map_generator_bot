package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Only the paths that stop before exhausting are covered here: exhausting itself needs the Spring context
// (game events, combat replay), which unit tests do not start.
class AgentLifecycleTest extends BaseTi4Test {

    private Game game;
    private Player gledge;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("agent-lifecycle-test");
        gledge = game.addPlayer("gledgeUser", "Gledge Player");
        gledge.setFaction(game, "gledge");
        gledge.setColor("red");
    }

    @Test
    void anExhaustedAgentIsRefused() {
        Leader agent = gledge.getLeader("gledgeagent").orElseThrow();
        agent.setExhausted(true);

        assertThat(AgentLifecycle.refuseIfExhausted(null, gledge, agent)).isTrue();
        assertThat(AgentLifecycle.use(game, gledge, "gledgeagent", "red", null)).isFalse();
        assertThat(gledge.getSpentThingsThisWindow()).isEmpty();
    }

    @Test
    void aReadiedAgentIsNotRefused() {
        Leader agent = gledge.getLeader("gledgeagent").orElseThrow();

        assertThat(AgentLifecycle.refuseIfExhausted(null, gledge, agent)).isFalse();
    }

    @Test
    void anUnresolvablePayloadLeavesTheAgentReadied() {
        assertThat(AgentLifecycle.use(game, gledge, "gledgeagent", "", null)).isFalse();
        assertThat(gledge.getLeader("gledgeagent").orElseThrow().isExhausted()).isFalse();
    }

    @Test
    void inProcessUseRequiresAModule() {
        assertThatThrownBy(() -> AgentLifecycle.use(game, gledge, "hacanagent", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
