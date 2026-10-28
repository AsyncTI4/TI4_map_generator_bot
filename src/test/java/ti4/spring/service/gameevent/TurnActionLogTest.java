package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;

class TurnActionLogTest {

    @Test
    void drainWithNothingRecordedReturnsEmpty() {
        Game game = new Game();

        assertThat(TurnActionLog.drain(game)).isEmpty();
    }

    @Test
    void multipleActionsInOneTurnComeBackInOrderAndClear() {
        Game game = new Game();
        TurnActionLog.record(game, TurnActionLog.STRATEGIC);
        TurnActionLog.record(game, TurnActionLog.TACTICAL);
        TurnActionLog.recordComponent(game, "relic");

        List<String> drained = TurnActionLog.drain(game);

        assertThat(drained).containsExactly("STRATEGIC", "TACTICAL", "COMPONENT:relic");
        assertThat(TurnActionLog.drain(game)).isEmpty();
    }

    @Test
    void turnPayloadCarriesPassedFlagAndDrainsActions() {
        Game game = new Game();
        TurnActionLog.record(game, TurnActionLog.TACTICAL);

        Map<String, Object> payload = TurnActionLog.turnPayload(game, false);

        assertThat(payload).containsEntry("passed", false).containsEntry("actions", List.of("TACTICAL"));
        assertThat(TurnActionLog.turnPayload(game, true)).containsEntry("actions", List.of());
    }
}
