package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// EnemyIntent keeps a rival's predicted plans for every seat that asks on the same save, so the imagined turn must
// not depend on who asked. It is played as the rival deciding: the rival's own experiment flags apply, nobody's hand
// is known, and the rival's secrets, action cards and promissory notes stay hidden.
class EnemyIntentTest extends BaseTi4Test {

    private final AiTestGame test = new AiTestGame();
    private final Player rival = test.addSeat("200000000000000002", "sardakk", "red");
    private final Player other = test.addSeat("300000000000000003", "hacan", "yellow");

    private record Seen(
            Player decider, boolean readyPoints, boolean knowsAskersHand, boolean knowsRivalsHand, int rivalNotes) {}

    private Seen askedBy(Player asker) {
        return DecidingSeat.of(
                asker,
                () -> EnemyIntent.imagined(rival, () -> {
                    Player decider = DecidingSeat.orSelf(test.game, rival);
                    return new Seen(
                            decider,
                            AiExperiments.enabled(decider, Experiment.READY_PUBLIC_POINTS),
                            DecidingSeat.knowsHandOf(asker),
                            DecidingSeat.knowsHandOf(rival),
                            ImaginedTurn.promissoryNotes(rival).size());
                }));
    }

    @Test
    void theImaginedTurnIsTheSameWhicheverSeatAsks() {
        // The two askers sit in different A/B arms; the rival's own flag is on.
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.READY_PUBLIC_POINTS));
        rival.setPromissoryNote("ceasefire_yellow");

        Seen byControl = askedBy(test.nekro);
        Seen byTreated = askedBy(other);

        assertThat(byControl).isEqualTo(byTreated);
        assertThat(byControl.decider()).isEqualTo(rival);
        assertThat(byControl.readyPoints()).isTrue();
        assertThat(byControl.knowsAskersHand()).isFalse();
        assertThat(byControl.knowsRivalsHand()).isFalse();
        assertThat(byControl.rivalNotes()).isZero();
    }

    @Test
    void theAskerDecidesAgainOnceTheImaginedTurnEnds() {
        rival.setPromissoryNote("ceasefire_yellow");

        Player after = DecidingSeat.of(test.nekro, () -> {
            EnemyIntent.imagined(rival, () -> null);
            return DecidingSeat.orSelf(test.game, rival);
        });

        assertThat(after).isEqualTo(test.nekro);
        assertThat(ImaginedTurn.promissoryNotes(rival)).isNotEmpty();
    }
}
