package ti4.service.milty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.testUtils.BaseTi4Test;

class MiltyDraftEventRecorderTest extends BaseTi4Test {

    private MockedStatic<GameEventService> events;
    private Game game;
    private MiltyDraftManager manager;

    @BeforeEach
    void setUp() {
        events = mockStatic(GameEventService.class);
        game = new Game();
        game.newGameSetup();
        game.setName("milty-draft-event-test");
        Player alice = game.addPlayer("1", "alice");
        alice.setFaction(game, "sol");
        alice.setColor("blue");
        manager = new MiltyDraftManager();
        manager.setPlayers(List.of("1", "2"));
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    @Test
    void endedRecordsEachPlayersSliceFactionAndOrder() {
        MiltyDraftSlice slice = new MiltyDraftSlice();
        slice.setName("A");
        MiltyDraftManager.PlayerDraft aliceDraft = manager.getPlayerDraft("1");
        aliceDraft.setSlice(slice);
        aliceDraft.setFaction("sol");
        aliceDraft.setPosition(1);

        MiltyDraftEventRecorder.recordEnded(game, manager);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        events.verify(
                () -> GameEventService.commit(eq(game), eq(GameEventType.DRAFT_ENDED), isNull(), payload.capture()));
        assertThat(payload.getValue()).containsEntry("orchestrator", "Milty");
        // Player "2" has no seat in the game, so they are listed by user id, and their unmade picks are empty.
        assertThat(payload.getValue().get("picks"))
                .isEqualTo(Map.of(
                        "alice", Map.of("slice", List.of("A"), "faction", List.of("sol"), "order", List.of("1")),
                        "2", Map.of("slice", List.of(), "faction", List.of(), "order", List.of())));
    }

    @Test
    void endedFiresOnceUntilTheNextDraftStarts() {
        MiltyDraftEventRecorder.recordEnded(game, manager);
        // finishDraft runs again when a milty button is pressed after the draft is over.
        MiltyDraftEventRecorder.recordEnded(game, manager);
        events.verify(
                () -> GameEventService.commit(eq(game), eq(GameEventType.DRAFT_ENDED), isNull(), any()), times(1));

        MiltyDraftEventRecorder.recordStarted(game, manager);
        MiltyDraftEventRecorder.recordEnded(game, manager);
        events.verify(
                () -> GameEventService.commit(eq(game), eq(GameEventType.DRAFT_ENDED), isNull(), any()), times(2));
    }
}
