package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.TickMemo;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Win odds are kept for the rest of the tick, but only for the same deciding seat (and the same imagined seat): the
// answer can depend on the decider's hand and experiment flags, so other seats work it out for themselves.
class WinOddsTickMemoTest extends BaseTi4Test {

    private final AiTestGame test = new AiTestGame();
    private final Player rival = test.addSeat("200000000000000002", "sardakk", "red");
    private final Player other = test.addSeat("300000000000000003", "hacan", "yellow");

    {
        test.game.setVp(10);
        test.game.setRound(3);
    }

    private WinOdds.Odds askedBy(Player decider) {
        return DecidingSeat.of(decider, () -> WinOdds.of(test.game, test.nekro, rival));
    }

    private int kept() {
        Map<?, ?> memo = TickMemo.mapFor(test.game, "win.odds");
        return memo.size();
    }

    @Test
    void theSameSeatReusesItsAnswerForTheRestOfTheTick() {
        try (TickMemo.Open tick = TickMemo.open(test.game)) {
            WinOdds.Odds first = askedBy(test.nekro);
            WinOdds.Odds again = askedBy(test.nekro);

            assertThat(again).isSameAs(first);
            assertThat(kept()).isEqualTo(1);
        }
    }

    @Test
    void anotherDecidingSeatOrAnImaginedTurnWorksItOutAgain() {
        try (TickMemo.Open tick = TickMemo.open(test.game)) {
            WinOdds.Odds mine = askedBy(test.nekro);
            WinOdds.Odds theirs = askedBy(other);
            WinOdds.Odds imagined = ImaginedTurn.of(other, () -> askedBy(other));

            assertThat(theirs).isNotSameAs(mine);
            assertThat(imagined).isNotSameAs(theirs);
            assertThat(kept()).isEqualTo(3);
        }
    }

    @Test
    void nothingCarriesIntoTheNextTick() {
        WinOdds.Odds first;
        try (TickMemo.Open tick = TickMemo.open(test.game)) {
            first = askedBy(test.nekro);
        }
        try (TickMemo.Open tick = TickMemo.open(test.game)) {
            assertThat(askedBy(test.nekro)).isNotSameAs(first);
        }
    }
}
