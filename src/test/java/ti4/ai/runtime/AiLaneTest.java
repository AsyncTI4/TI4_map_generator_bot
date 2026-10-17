package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.runtime.AiSeatState.DelegationRequest;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// One lane runs every AI seat of a game. Seats take turns acting and keep their own pressed keys and memory.
class AiLaneTest extends BaseTi4Test {

    private static final long NOW = 1_800_000_000_000L;

    private final AiLane lane = new AiLane("ai-lane-test");
    private Player first;
    private Player second;
    private Player third;

    @BeforeEach
    void setUp() {
        Game game = new Game();
        game.setName("ai-lane-test");
        first = game.addPlayer("7100000000000001", "First AI");
        second = game.addPlayer("7100000000000002", "Second AI");
        third = game.addPlayer("7100000000000003", "Third AI");
    }

    @Test
    void rotationKeepsSeatOrderBeforeAnyoneActed() {
        assertThat(lane.rotation(List.of(first, second, third))).containsExactly(first, second, third);
    }

    // The seat that acted last goes to the back of the line, so one busy seat cannot starve the others.
    @Test
    void rotationStartsAfterTheSeatThatActedLastAndWraps() {
        lane.recordAction(second.getUserID(), NOW);
        assertThat(lane.rotation(List.of(first, second, third))).containsExactly(third, first, second);

        lane.recordAction(third.getUserID(), NOW + 1);
        assertThat(lane.rotation(List.of(first, second, third))).containsExactly(first, second, third);
    }

    @Test
    void rotationIgnoresALastActorThatLeftTheGame() {
        lane.recordAction(second.getUserID(), NOW);

        assertThat(lane.rotation(List.of(first, third))).containsExactly(first, third);
    }

    @Test
    void eachSeatHasItsOwnPressedKeysAndMemory() {
        AiSeatState a = lane.seat(first.getUserID());
        a.recordPressed("message|FFCC_nekro_passForRound", NOW);
        a.memory().put("tacticalPlan|x", "plan");

        AiSeatState b = lane.seat(second.getUserID());

        assertThat(lane.seat(first.getUserID())).isSameAs(a);
        assertThat(b).isNotSameAs(a);
        assertThat(b.pressedKeys()).isEmpty();
        assertThat(b.memory().has("tacticalPlan|x")).isFalse();
        assertThat(a.pressedKeys()).containsExactly("message|FFCC_nekro_passForRound");
        assertThat(a.memory().get("tacticalPlan|x")).contains("plan");
    }

    @Test
    void delegationRequestsAreTrackedPerSeat() {
        lane.requestDelegation(first.getUserID(), true);

        assertThat(lane.seat(second.getUserID()).consumeDelegationRequest()).isEqualTo(DelegationRequest.NONE);
        assertThat(lane.seat(first.getUserID()).consumeDelegationRequest()).isEqualTo(DelegationRequest.EXPLICIT);
        assertThat(lane.seat(first.getUserID()).consumeDelegationRequest()).isEqualTo(DelegationRequest.NONE);
    }

    // A request naming the seat outranks a table-wide one sent before or after it.
    @Test
    void anExplicitDelegationRequestIsNotDowngraded() {
        lane.requestDelegation(first.getUserID(), true);
        lane.requestDelegation(first.getUserID(), false);

        assertThat(lane.seat(first.getUserID()).consumeDelegationRequest()).isEqualTo(DelegationRequest.EXPLICIT);
    }

    // An undo must not leave the AI thinking it already pressed buttons that are back on the table.
    @Test
    void anUndoClearsEverySeatsTurnMemory() {
        lane.seat(first.getUserID()).recordPressed("a|undone", NOW);
        lane.seat(first.getUserID()).memory().put("tacticalPlan|x", "plan");
        assertThat(lane.observeUndoIndex(12)).isFalse();
        assertThat(lane.observeUndoIndex(13)).isFalse();

        assertThat(lane.observeUndoIndex(11)).isTrue();
        lane.forgetAfterUndo();

        assertThat(lane.seat(first.getUserID()).pressedKeys()).isEmpty();
        assertThat(lane.seat(first.getUserID()).memory().has("tacticalPlan|x")).isFalse();
    }

    // A referee press the engine refused is retried only once the game state has changed.
    @Test
    void refereeRetriesOnlyAfterTheStateChanges() {
        assertThat(lane.refereeMayPress("m|startOfGameObjReveal", 7)).isTrue();
        lane.recordRefereePress("m|startOfGameObjReveal", 7);

        assertThat(lane.refereeMayPress("m|startOfGameObjReveal", 7)).isFalse();
        assertThat(lane.refereeMayPress("m|startOfGameObjReveal", 8)).isTrue();
    }

    @Test
    void retainSeatsDropsSeatsThatLeft() {
        lane.seat(first.getUserID()).recordPressed("a|kept", NOW);
        lane.seat(second.getUserID()).recordPressed("b|dropped", NOW);

        lane.retainSeats(List.of(first, third));

        assertThat(lane.seat(first.getUserID()).pressedKeys()).containsExactly("a|kept");
        assertThat(lane.seat(second.getUserID()).pressedKeys()).isEmpty();
    }

    // The per-game delegation ceiling caps how often the whole table is asked to choose for the AIs each round.
    @Test
    void gameDelegationCeilingResetsOnANewRound() {
        assertThat(lane.recordGameDelegation(1, 2)).isTrue();
        assertThat(lane.recordGameDelegation(1, 2)).isTrue();
        assertThat(lane.recordGameDelegation(1, 2)).isFalse();

        assertThat(lane.recordGameDelegation(2, 2)).isTrue();
        assertThat(lane.recordGameDelegation(2, 2)).isTrue();
        assertThat(lane.recordGameDelegation(2, 2)).isFalse();
    }

    // A fallback that keeps coming back with the same reason in the same turn, with nothing the seat pressed itself in
    // between, is a loop: after the limit it stops once with a notice and then stays stopped.
    @Test
    void repeatedFallbacksWithTheSameReasonStopOnceTheLimitIsPassed() {
        AiSeatState seat = lane.seat(first.getUserID());
        for (int i = 0; i < 5; i++) {
            assertThat(seat.recordFallback("lost@turn1", 5)).isEqualTo(AiSeatState.FallbackVerdict.CHOOSE);
        }

        assertThat(seat.recordFallback("lost@turn1", 5)).isEqualTo(AiSeatState.FallbackVerdict.STOP_AND_ANNOUNCE);
        assertThat(seat.recordFallback("lost@turn1", 5)).isEqualTo(AiSeatState.FallbackVerdict.STOPPED);
        assertThat(seat.recordFallback("lost@turn1", 5)).isEqualTo(AiSeatState.FallbackVerdict.STOPPED);
    }

    // Another reason, another turn, a press the seat chose itself, or an undo all start the count again.
    @Test
    void theFallbackCountStartsAgainWhenTheSituationChanges() {
        AiSeatState seat = lane.seat(first.getUserID());
        for (int i = 0; i < 5; i++) seat.recordFallback("lost@turn1", 5);
        assertThat(seat.recordFallback("other@turn1", 5)).isEqualTo(AiSeatState.FallbackVerdict.CHOOSE);

        for (int i = 0; i < 5; i++) seat.recordFallback("lost@turn1", 5);
        assertThat(seat.recordFallback("lost@turn2", 5)).isEqualTo(AiSeatState.FallbackVerdict.CHOOSE);

        for (int i = 0; i < 5; i++) seat.recordFallback("lost@turn2", 5);
        seat.clearFallbacks();
        assertThat(seat.recordFallback("lost@turn2", 5)).isEqualTo(AiSeatState.FallbackVerdict.CHOOSE);

        for (int i = 0; i < 5; i++) seat.recordFallback("lost@turn2", 5);
        seat.forgetAfterUndo();
        assertThat(seat.recordFallback("lost@turn2", 5)).isEqualTo(AiSeatState.FallbackVerdict.CHOOSE);
    }

    @Test
    void seatDelegationCeilingIsIndependentOfOtherSeats() {
        AiSeatState a = lane.seat(first.getUserID());
        AiSeatState b = lane.seat(second.getUserID());

        assertThat(a.canDelegate(1, 1)).isTrue();
        a.recordDelegation("p1");
        assertThat(a.canDelegate(1, 1)).isFalse();
        assertThat(b.canDelegate(1, 1)).isTrue();
        b.recordDelegation("p3");
        assertThat(a.canDelegate(2, 1)).isTrue();
        assertThat(a.wasDelegated("p1")).isTrue();
        assertThat(b.wasDelegated("p1")).isFalse();
    }
}
