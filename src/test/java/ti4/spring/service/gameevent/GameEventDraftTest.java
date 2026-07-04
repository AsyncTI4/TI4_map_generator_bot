package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.json.JsonMapperManager;

class GameEventDraftTest {

    @Test
    void stageWhenClosedReturnsFalseAndStoresNothing() {
        Game game = new Game();

        boolean staged = GameEventDraft.stage(game, new GameSubEvent.ControlEstablished("18"));

        assertThat(staged).isFalse();
        assertThat(GameEventDraft.isOpen(game)).isFalse();
        assertThat(game.getPendingSubEventsJson()).isEmpty();
    }

    @Test
    void openThenStageThenDrainReturnsTypedOrderedListAndClears() {
        Game game = new Game();
        GameEventDraft.open(game);

        assertThat(GameEventDraft.isOpen(game)).isTrue();
        assertThat(GameEventDraft.stage(game, new GameSubEvent.ControlEstablished("18")))
                .isTrue();
        assertThat(GameEventDraft.stage(game, new GameSubEvent.Combat("space", "18", null, "hacan")))
                .isTrue();
        assertThat(GameEventDraft.stage(game, new GameSubEvent.TechExhausted("hacan", "gs")))
                .isTrue();

        List<GameSubEvent> drained = GameEventDraft.drain(game);

        assertThat(drained)
                .containsExactly(
                        new GameSubEvent.ControlEstablished("18"),
                        new GameSubEvent.Combat("space", "18", null, "hacan"),
                        new GameSubEvent.TechExhausted("hacan", "gs"));
        assertThat(GameEventDraft.isOpen(game)).isFalse();
        assertThat(game.getPendingSubEventsJson()).isEmpty();
    }

    @Test
    void drainWhenClosedReturnsEmpty() {
        Game game = new Game();

        assertThat(GameEventDraft.drain(game)).isEmpty();
    }

    @Test
    void serializedDraftContainsTypeDiscriminatorsAndNoNewlines() {
        Game game = new Game();
        GameEventDraft.open(game);
        GameEventDraft.stage(game, new GameSubEvent.ControlEstablished("18"));
        GameEventDraft.stage(game, new GameSubEvent.LeaderPlayed("hacan", "agent", "hacanagent"));

        String json = game.getPendingSubEventsJson();

        assertThat(json).doesNotContain("\n").doesNotContain("\r");
        assertThat(json).contains("\"type\":\"CONTROL_ESTABLISHED\"");
        assertThat(json).contains("\"type\":\"LEADER_PLAYED\"");
    }

    @Test
    void drainedListReserializesWithTypeIntact() {
        Game game = new Game();
        GameEventDraft.open(game);
        GameEventDraft.stage(game, new GameSubEvent.ActionCardPlayed("hacan", "ac1", "Sabotage"));

        List<GameSubEvent> drained = GameEventDraft.drain(game);

        // This is what lands in the event payload; the typed serialize keeps the polymorphic discriminator, whereas a
        // raw writeValueAsString(list) through an Object reference would drop it.
        String payloadJson = GameEventDraft.serialize(drained);
        assertThat(payloadJson).contains("\"type\":\"ACTION_CARD_PLAYED\"");

        // And it survives being embedded (as a JsonNode) into the Object-valued payload map the committer serializes.
        Map<String, Object> payload = new HashMap<>();
        payload.put("subEvents", JsonMapperManager.basic().readTree(payloadJson));
        assertThat(JsonMapperManager.basic().writeValueAsString(payload)).contains("\"type\":\"ACTION_CARD_PLAYED\"");
    }
}
