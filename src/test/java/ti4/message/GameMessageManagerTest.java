package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.json.JsonMapperManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gamemessage.GameMessageService;

class GameMessageManagerTest {

    @Test
    void databaseFailuresFallBackInsteadOfBreakingTheButton() {
        // Matches the old file store: a storage failure is logged and the caller sees "nothing tracked".
        try (MockedStatic<SpringContext> spring = Mockito.mockStatic(SpringContext.class, Mockito.CALLS_REAL_METHODS)) {
            spring.when(() -> SpringContext.getBean(GameMessageService.class))
                    .thenThrow(new IllegalStateException("database down"));

            assertThat(GameMessageManager.replace("pbd1", new GameMessage("1", GameMessageType.TURN, 1L)))
                    .isNull();
            assertThat(GameMessageManager.getOne("pbd1", GameMessageType.TURN)).isEmpty();
            assertThat(GameMessageManager.getAll("pbd1", GameMessageType.TURN)).isEmpty();
            assertThat(GameMessageManager.remove("pbd1", GameMessageType.TURN)).isEmpty();
            GameMessageManager.addReaction("pbd1", "sol", "1");
            GameMessageManager.add("pbd1", new GameMessage("1", GameMessageType.TURN, 1L));
        }
    }

    @Test
    void gameMessageDeserializesLegacyJsonWithoutKey() {
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
    void gameMessageRoundTripsKey() {
        GameMessage original = new GameMessage("456", GameMessageType.TURN, new LinkedHashSet<>(), 99L, "4::1");

        String json = JsonMapperManager.basic().writeValueAsString(original);
        GameMessage reread = JsonMapperManager.basic().readValue(json, GameMessage.class);

        assertThat(reread.key()).isEqualTo("4::1");
    }

    @Test
    void gameMessageOmitsNullKeyFromJson() {
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
    void gameMessageOmitsEmptyFactionsThatReactedFromJson() {
        GameMessage original = new GameMessage("789", GameMessageType.TURN, new LinkedHashSet<>(), 99L, null);

        String json = JsonMapperManager.basic().writeValueAsString(original);

        assertThat(json).doesNotContain("\"factionsThatReacted\"");
    }
}
