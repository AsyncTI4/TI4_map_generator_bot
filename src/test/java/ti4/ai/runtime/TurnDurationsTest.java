package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class TurnDurationsTest extends BaseTi4Test {

    // Nekro's turn runs from when it becomes active until Sol does; the strategy phase before it is its own stretch,
    // and Nekro's second turn in the round is numbered 2.
    @Test
    void measuresEachSeatsTurnAndEachOtherPhase() {
        AiTestGame test = new AiTestGame();
        TurnDurations durations = new TurnDurations("g");
        test.game.setRound(2);
        test.game.setPhaseOfGame("strategy");
        durations.observe(test.game, 0);

        test.game.setPhaseOfGame("action");
        test.game.setActivePlayerID(test.nekro.getUserID());
        durations.observe(test.game, 10_000);
        durations.ticked(2_000_000_000L);
        durations.ticked(500_000_000L);
        test.game.setActivePlayerID(test.sol.getUserID());
        durations.observe(test.game, 70_000);
        test.game.setActivePlayerID(test.nekro.getUserID());
        durations.observe(test.game, 100_000);

        List<TurnDurations.Turn> turns = durations.finish(130_000);

        assertThat(turns)
                .extracting(TurnDurations.Turn::who, TurnDurations.Turn::number, TurnDurations.Turn::gameMillis)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("strategy", 1, 10_000L),
                        org.assertj.core.groups.Tuple.tuple("nekro", 1, 60_000L),
                        org.assertj.core.groups.Tuple.tuple("sol", 1, 30_000L),
                        org.assertj.core.groups.Tuple.tuple("nekro", 2, 30_000L));
        assertThat(turns.get(1).ticks()).isEqualTo(2);
        assertThat(turns.get(1).line()).isEqualTo("TURN g r2 nekro #1: game time 60s, AI thinking 2.5s over 2 ticks");
        assertThat(TurnDurations.summary(turns))
                .contains("TURNS g nekro: 2, average game time 45s, average AI thinking 1.3s, longest r2 #1 60s");
        assertThat(durations.finish(130_000)).isEmpty();
    }

    // Each turn reaches the listener as soon as the next one opens, not when the game ends, so a long game's log shows
    // its turns while it is still playing.
    @Test
    void eachTurnIsHandedOverAsSoonAsItCloses() {
        AiTestGame test = new AiTestGame();
        List<TurnDurations.Turn> heard = new java.util.ArrayList<>();
        TurnDurations durations = new TurnDurations("g", heard::add);
        test.game.setRound(3);
        test.game.setPhaseOfGame("action");
        test.game.setActivePlayerID(test.nekro.getUserID());
        durations.observe(test.game, 0);
        assertThat(heard).isEmpty();

        test.game.setActivePlayerID(test.sol.getUserID());
        durations.observe(test.game, 40_000);
        assertThat(heard).extracting(TurnDurations.Turn::who).containsExactly("nekro");

        durations.finish(90_000);
        assertThat(heard).extracting(TurnDurations.Turn::who).containsExactly("nekro", "sol");
    }
}
