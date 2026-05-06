package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        GameMessage gameMessage = JsonMapperManager.basic().readValue(legacyJson, GameMessage.class);

        assertThat(gameMessage.info()).isEmpty();
        assertThat(gameMessage.factionsThatReacted()).containsExactly("hacan");
    }

    @Test
    void gameMessageRoundTripsInfo() throws Exception {
        GameMessage original =
                new GameMessage("456", GameMessageType.TURN, new LinkedHashSet<>(), 99L, Map.of("strategyCard", "1"));

        String json = JsonMapperManager.basic().writeValueAsString(original);
        GameMessage reread = JsonMapperManager.basic().readValue(json, GameMessage.class);

        assertThat(reread.info()).containsEntry("strategyCard", "1");
    }

    @Test
    void gameMessageOmitsEmptyInfoFromJson() throws Exception {
        GameMessage original = new GameMessage("789", GameMessageType.TURN, new LinkedHashSet<>(), 99L, Map.of());

        String json = JsonMapperManager.basic().writeValueAsString(original);

        assertThat(json).doesNotContain("\"info\"");
    }

    @Test
    void gameMessageParsesTypedInfoValues() {
        GameMessage gameMessage = new GameMessage(
                "999",
                GameMessageType.STRATEGY_CARD,
                new LinkedHashSet<>(),
                123L,
                Map.of("round", "4", "sc", "8", "playedAt", "456"));

        assertThat(gameMessage.getInfoAsLong("playedAt")).isEqualTo(456L);
        assertThat(gameMessage.getInfoAsInt("sc")).isEqualTo(8);
    }

    @Test
    void gameMessageThrowsOnMissingTypedInfoValue() {
        GameMessage gameMessage =
                new GameMessage("999", GameMessageType.STRATEGY_CARD, new LinkedHashSet<>(), 123L, Map.of());

        assertThatThrownBy(() -> gameMessage.getInfoAsInt("sc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing info value for key 'sc'");
    }
}
