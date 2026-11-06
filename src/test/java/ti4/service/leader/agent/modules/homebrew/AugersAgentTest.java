package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.testUtils.BaseTi4Test;

class AugersAgentTest extends BaseTi4Test {

    private final AugersAgent module = new AugersAgent();
    private Game game;
    private Player augers;
    private Player explorer;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("augers-agent-test");
        augers = game.addPlayer("augersUser", "Augers Player");
        augers.setFaction(game, "augers");
        augers.setColor("red");
        explorer = game.addPlayer("explorerUser", "Explorer Player");
        // A Project Pi faction id contains the "_" separator, which the old split("_")[1] parsing truncated.
        explorer.setFaction(game, "pi_hacan");
        explorer.setColor("blue");
    }

    @Test
    void decodesAFactionContainingTheSeparator() {
        assertThat(module.decode(game, augers, "pi_hacan")).contains(explorer);
    }

    @Test
    void decodesAColourForFogOfWarTargets() {
        assertThat(module.decode(game, augers, "blue")).contains(explorer);
    }

    @Test
    void cannotResolveWithoutATarget() {
        assertThat(module.decode(game, augers, "")).isEmpty();
        assertThat(module.decode(game, augers, "nobody")).isEmpty();
    }

    @Test
    void givesTheExplorerTwoTradeGoods() {
        explorer.setTg(3);

        AgentOutcome outcome = module.resolve(use());

        assertThat(explorer.getTg()).isEqualTo(5);
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(explorer);
            assertThat(message.text())
                    .contains("gained 2 trade goods from Clodho, the Ilyxum agent")
                    .contains("(3->5)");
        });
    }

    private AgentUse<Player> use() {
        return new AgentUse<>(game, augers, explorer, "Clodho, the Ilyxum agent", false, null);
    }
}
