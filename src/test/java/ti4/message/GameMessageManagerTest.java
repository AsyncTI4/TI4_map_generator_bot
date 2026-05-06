package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;

class GameMessageManagerTest {

    @Test
    void gameMessageDeserializesLegacyJsonWithoutSecondaryKey() throws Exception {
        String legacyJson = """
            {
              "messageId": "123",
              "type": "ACTION_CARD",
              "factionsThatReacted": ["hacan"],
              "gameSaveTime": 42
            }
            """;

        GameMessage gameMessage = JsonMapperManager.basic().readValue(legacyJson, GameMessage.class);

        assertThat(gameMessage.secondaryKey()).isNull();
        assertThat(gameMessage.factionsThatReacted()).containsExactly("hacan");
    }

    @Test
    void gameMessageRoundTripsSecondaryKey() throws Exception {
        GameMessage original = new GameMessage("456", GameMessageType.TURN, new LinkedHashSet<>(), 99L, "4::1");

        String json = JsonMapperManager.basic().writeValueAsString(original);
        GameMessage reread = JsonMapperManager.basic().readValue(json, GameMessage.class);

        assertThat(reread.secondaryKey()).isEqualTo("4::1");
    }

    @Test
    void gameMessageOmitsNullSecondaryKeyFromJson() throws Exception {
        GameMessage original = new GameMessage("789", GameMessageType.TURN, new LinkedHashSet<>(), 99L, null);

        String json = JsonMapperManager.basic().writeValueAsString(original);

        assertThat(json).doesNotContain("\"secondaryKey\"");
    }

    @Test
    void gameMessageDeserializesLegacyStrategyCardInfoAsSecondaryKey() throws Exception {
        String legacyJson = """
            {
              "messageId": "999",
              "type": "STRATEGY_CARD",
              "factionsThatReacted": [],
              "gameSaveTime": 123,
              "info": {
                "round": "4",
                "sc": "8",
                "playedAt": "456"
              }
            }
            """;

        GameMessage gameMessage = JsonMapperManager.basic().readValue(legacyJson, GameMessage.class);

        assertThat(gameMessage.secondaryKey()).isEqualTo("4::8");
    }

    @Test
    void gameMessageKeepsExplicitSecondaryKey() {
        GameMessage gameMessage = new GameMessage(
                "999", GameMessageType.STRATEGY_CARD, new LinkedHashSet<>(), 123L, "4::8");

        assertThat(gameMessage.secondaryKey()).isEqualTo("4::8");
        assertThat(gameMessage.gameSaveTime()).isEqualTo(123L);
    }
}
