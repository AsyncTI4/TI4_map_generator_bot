package ti4.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.testbed.TestBedService;
import ti4.testUtils.BaseTi4Test;

class AiSeatsTest extends BaseTi4Test {

    private static final String THIRD_AI_ID = "7100000555555555";
    private static final String SPECTATOR_ID = "300000000000000003";

    @Test
    void recognisesOnlySixteenDigitIdsWithTheAiPrefix() {
        assertThat(AiSeats.isAiSeatId("7100000123456789")).isTrue();
        assertThat(AiSeats.isAiSeatId("710000012345678")).isFalse();
        assertThat(AiSeats.isAiSeatId("71000001234567890")).isFalse();
        assertThat(AiSeats.isAiSeatId("7100000abcdefghi")).isFalse();
        assertThat(AiSeats.isAiSeatId("1234567890123456")).isFalse();
        assertThat(AiSeats.isAiSeatId(null)).isFalse();
    }

    // AI ids must never be mistaken for test bed virtual seats, and must stay below real Discord snowflakes
    // (17+ digits), so JDA lookups simply return nothing.
    @Test
    void aiIdsAreDistinctFromTestBedSeatsAndRealUsers() {
        String testBedSeat = TestBedService.VIRTUAL_SEAT_ID_PREFIX + "01";
        assertThat(AiSeats.isAiSeatId(testBedSeat)).isFalse();
        assertThat(AiSeats.ID_PREFIX).doesNotStartWith(TestBedService.VIRTUAL_SEAT_ID_PREFIX);
        assertThat("7100000123456789").hasSizeLessThan(17);
    }

    // Several AI seats can share a table; every one still in the game takes part.
    @Test
    void activeAiSeatsListsEveryAiSeatStillInTheGame() {
        AiTestGame test = AiTestGame.withSolAi();
        Player eliminated = test.addSeat(THIRD_AI_ID, "hacan", "yellow");
        eliminated.setEliminated(true);

        assertThat(AiSeats.activeAiSeats(test.game)).containsExactlyInAnyOrder(test.nekro, test.sol);
        assertThat(AiSeats.aiSeats(test.game)).containsExactlyInAnyOrder(test.nekro, test.sol, eliminated);
    }

    // An AI id that never picked a faction is not a seat at the table yet.
    @Test
    void activeAiSeatsSkipsAiPlayersWithoutAFaction() {
        AiTestGame test = new AiTestGame();
        test.game.addPlayer(THIRD_AI_ID, "Unseated AI");

        assertThat(AiSeats.activeAiSeats(test.game)).containsExactly(test.nekro);
    }

    @Test
    void selfPlayOnlyWhenEveryRealPlayerIsAnAiSeat() {
        assertThat(AiSeats.isSelfPlay(new AiTestGame().game)).isFalse();
        assertThat(AiSeats.isSelfPlay(AiTestGame.withSolAi().game)).isTrue();
    }

    // Spectators who joined the game but never sat down must not stop an all-AI game from refereeing itself.
    @Test
    void anUnseatedSpectatorDoesNotBreakSelfPlay() {
        AiTestGame test = AiTestGame.withSolAi();
        test.game.addPlayer(SPECTATOR_ID, "Spectator");

        assertThat(AiSeats.isSelfPlay(test.game)).isTrue();
    }

    @Test
    void aGameWithoutRealPlayersIsNotSelfPlay() {
        Game empty = new Game();
        empty.addPlayer(SPECTATOR_ID, "Spectator");

        assertThat(AiSeats.isSelfPlay(empty)).isFalse();
    }
}
