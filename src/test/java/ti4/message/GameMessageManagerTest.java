package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;

class GameMessageManagerTest {

    @Test
    void gameMessageDeserializesLegacyJsonWithoutKey() throws Exception {
        String legacyJson = """
            {
              "messageId": "123",
              "type": "ACTION_CARD",
              "factionsThatReacted": ["hacan"],
              "gameSaveTime": 42
            }
            """;

        GameMessage gameMessage = JsonMapperManager.basic().readValue(legacyJson, GameMessage.class);

        assertThat(gameMessage.key()).isNull();
        assertThat(gameMessage.factionsThatReacted()).containsExactly("hacan");
    }

    @Test
    void gameMessageRoundTripsKey() throws Exception {
        GameMessage original = new GameMessage("456", GameMessageType.TURN, new LinkedHashSet<>(), 99L, "4::1");

        String json = JsonMapperManager.basic().writeValueAsString(original);
        GameMessage reread = JsonMapperManager.basic().readValue(json, GameMessage.class);

        assertThat(reread.key()).isEqualTo("4::1");
    }

    @Test
    void gameMessageOmitsNullKeyFromJson() throws Exception {
        GameMessage original = new GameMessage("789", GameMessageType.TURN, new LinkedHashSet<>(), 99L, null);

        String json = JsonMapperManager.basic().writeValueAsString(original);

        assertThat(json).doesNotContain("\"key\"");
    }

    @Test
    void gameMessageKeepsExplicitKey() {
        GameMessage gameMessage =
                new GameMessage("999", GameMessageType.STRATEGY_CARD, new LinkedHashSet<>(), 123L, "4::8");

        assertThat(gameMessage.key()).isEqualTo("4::8");
        assertThat(gameMessage.gameSaveTime()).isEqualTo(123L);
    }

    @Test
    void gameMessageOmitsEmptyFactionsThatReactedFromJson() throws Exception {
        GameMessage original = new GameMessage("789", GameMessageType.TURN, new LinkedHashSet<>(), 99L, null);

        String json = JsonMapperManager.basic().writeValueAsString(original);

        assertThat(json).doesNotContain("\"factionsThatReacted\"");
    }
}
