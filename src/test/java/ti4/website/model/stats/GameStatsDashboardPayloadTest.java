package ti4.website.model.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;
import ti4.map.Game;
import ti4.map.persistence.GameTestHelper;
import ti4.testUtils.BaseTi4Test;

class GameStatsDashboardPayloadTest extends BaseTi4Test {

    @Test
    void getSetupTime() {
        var game = createGame();
        game.setCreationDate("2024.10.30");

        var setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();

        assertThat(setupTimestamp).isEqualTo(1730247890L);
    }

    @Test
    void getSetupTimeHandlesException() {
        var game = createGame();
        game.setCreationDate("2024-10-30");

        var setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();

        assertThat(String.valueOf(setupTimestamp)).endsWith("90");
    }

    @Test
    void getLawsIgnoresUnknownIds() {
        var game = createGame();
        game.setLaws(Map.of("unknown_law", 1));
        game.setDiscardAgendas(Map.of("another_unknown", 2));

        var laws = new GameStatsDashboardPayload(game).getLaws();

        assertThat(laws).isEmpty();
    }

    @Test
    void getLawsReturnsKnownNames() {
        var game = createGame();
        game.setLaws(Map.of("revolution", 1));

        var laws = new GameStatsDashboardPayload(game).getLaws();

        assertThat(laws).containsExactly("Anti-Intellectual Revolution");
    }

    @Test
    void serializesDisplacedUnitsGameAsDashboardPayload() {
        var game = GameTestHelper.loadGame("game-with-displaced-units");

        var payloadJson = JsonMapperManager.basic().valueToTree(new GameStatsDashboardPayload(game));

        assertThat(payloadJson.path("asyncGameID").asText()).isEqualTo(game.getID());
        assertThat(payloadJson.path("asyncFunGameName").asText()).isEqualTo(game.getCustomName());
        assertThat(payloadJson.path("isPoK").isBoolean()).isTrue();
        assertThat(payloadJson.path("tiglGame").asBoolean()).isTrue();
    }

    private Game createGame() {
        var game = new Game();
        game.setName("pbd123");
        game.setCustomName("pbd123-a-test-for-you-and-me");
        return game;
    }
}
