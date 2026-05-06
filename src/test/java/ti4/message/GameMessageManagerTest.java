package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;

class GameMessageManagerTest {

    @Test
    void gameMessageDeserializesLegacyJsonWithoutInfo() throws Exception {
        String legacyJson = """
            {
              "messageId": "123",
              "type": "ACTION_CARD",
              "factionsThatReacted": ["hacan"],
              "gameSaveTime": 42
            }
            """;

        GameMessageManager.GameMessage gameMessage =
                JsonMapperManager.basic().readValue(legacyJson, GameMessageManager.GameMessage.class);

        assertThat(gameMessage.info()).isEmpty();
        assertThat(gameMessage.factionsThatReacted()).containsExactly("hacan");
    }

    @Test
    void gameMessageRoundTripsInfo() throws Exception {
        GameMessageManager.GameMessage original = new GameMessageManager.GameMessage(
                "456", GameMessageType.TURN, new LinkedHashSet<>(), 99L, Map.of("strategyCard", "1"));

        String json = JsonMapperManager.basic().writeValueAsString(original);
        GameMessageManager.GameMessage reread =
                JsonMapperManager.basic().readValue(json, GameMessageManager.GameMessage.class);

        assertThat(reread.info()).containsEntry("strategyCard", "1");
    }

    @Test
    void gameMessageOmitsEmptyInfoFromJson() throws Exception {
        GameMessageManager.GameMessage original =
                new GameMessageManager.GameMessage("789", GameMessageType.TURN, new LinkedHashSet<>(), 99L, Map.of());

        String json = JsonMapperManager.basic().writeValueAsString(original);

        assertThat(json).doesNotContain("\"info\"");
    }
}
