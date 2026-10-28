package ti4.service.tech;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.testUtils.BaseTi4Test;

class TemporalCommandSuiteServiceTest extends BaseTi4Test {

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("tcs-test");
        Player mentak = game.addPlayer("mentakUser", "Mentak Player");
        mentak.setFaction(game, "mentak");
        mentak.setColor("red");
    }

    @Test
    void theLabelNamesTheCommandTokenInTwilightsFallOnly() {
        assertThat(TemporalCommandSuiteService.label(game, "Suffi An")).isEqualTo("TCS: Ready Suffi An");

        game.setTwilightsFallMode(true);

        assertThat(TemporalCommandSuiteService.label(game, "Suffi An")).isEqualTo("Token: Ready Suffi An");
    }

    @Test
    void labelsFitInAButtonEvenForCleverCleverCopies() {
        for (boolean twilightsFall : List.of(false, true)) {
            game.setTwilightsFallMode(twilightsFall);
            for (AgentModule<?> module : LeaderAgents.all()) {
                for (String leaderId : leaderIds(module)) {
                    String label =
                            TemporalCommandSuiteService.label(game, AgentLifecycle.agentName(game, leaderId, true));
                    assertThat(label).as(leaderId).hasSizeLessThanOrEqualTo(80);
                }
            }
        }
    }

    private static List<String> leaderIds(AgentModule<?> module) {
        List<String> ids = new ArrayList<>(module.aliasIds());
        ids.add(module.agentId());
        return ids;
    }
}
