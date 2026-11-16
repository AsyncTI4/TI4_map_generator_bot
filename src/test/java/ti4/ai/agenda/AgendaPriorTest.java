package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Milestone 6: judging agendas that are not on the table yet, for the kept vote and Politics placement.
class AgendaPriorTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sol = test.sol;
        test.game.setRound(3);
        test.game.setVp(10);
        test.game.resetAgendas();
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Law_For/Against_7_regulations");
        test.nekroHome();
        String lodor = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", lodor);
        test.solHomeBeside(lodor);
    }

    // The deck holds what has not been discarded, put into play or revealed now.
    @Test
    void theRemainingDeckLeavesOutWhatIsGone() {
        test.game.getDiscardAgendas().put("minister_war", 1);
        test.game.addLaw("minister_peace", "sol");

        assertThat(AgendaPrior.remainingDeck(test.game))
                .isNotEmpty()
                .doesNotContain("minister_war", "minister_peace", "regulations", "covert");
    }

    // A vote kept for the next agenda is worth a share of what is at stake there, per vote at the table. Without
    // knowing the next card it uses the deck average; knowing it, that card's stakes.
    @Test
    void aKeptVoteIsWorthTheNextAgendasStakes() {
        int tableVotes = test.game.getRealPlayers().stream()
                .mapToInt(player -> VoteCapacity.of(test.game, player))
                .sum();
        assertThat(tableVotes).isPositive();
        double deck = AgendaPrior.marginalVote(test.game, sol);
        assertThat(deck)
                .isCloseTo(
                        Math.clamp(
                                AgendaPrior.PIVOTAL_SHARE * AgendaPrior.deckSpread(test.game, sol) / tableVotes,
                                AgendaPrior.MIN_VOTE,
                                AgendaPrior.MAX_VOTE),
                        within(1e-9));

        AgendaKnowledge.placedOnTop(test.game, sol, "shard_of_the_throne");
        assertThat(AgendaPrior.marginalVote(test.game, sol))
                .isCloseTo(
                        Math.clamp(
                                AgendaPrior.PIVOTAL_SHARE
                                        * AgendaPrior.spread(test.game, sol, "shard_of_the_throne")
                                        / tableVotes,
                                AgendaPrior.MIN_VOTE,
                                AgendaPrior.MAX_VOTE),
                        within(1e-9));
        assertThat(BallotPlanner.keptVote(test.game, sol)).isEqualTo(AgendaPrior.marginalVote(test.game, sol));
    }

    // Politics: a card better for Sol than the deck average stays on top, a worse one goes to the bottom.
    @Test
    void politicsKeepsCardsBetterThanTheDeckAverage() {
        double average = AgendaPrior.deckExpected(test.game, sol);
        List<String> byValue = AgendaPrior.remainingDeck(test.game).stream()
                .sorted(Comparator.comparingDouble(agenda -> AgendaPrior.expected(test.game, sol, agenda)))
                .toList();
        String worst = byValue.getFirst();
        String best = byValue.getLast();
        assertThat(AgendaPrior.expected(test.game, sol, worst)).isLessThan(average);
        assertThat(AgendaPrior.expected(test.game, sol, best)).isGreaterThanOrEqualTo(average);

        assertThat(AgendaPeek.keepOnTop(test.game, sol, best)).isTrue();
        assertThat(AgendaPeek.keepOnTop(test.game, sol, worst)).isFalse();
    }
}
