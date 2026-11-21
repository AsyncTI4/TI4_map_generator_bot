package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.RiderPayoff;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The user's note on traces-4b (Executive Sanctions): a seat whose Against vote could stop a rider from paying may
// "extort Xxcha to give some of the Trade Rider TGs not to vote Against". The extorter offers, at its turn to vote,
// "pay me X TG and I will not vote against your outcome (I abstain or vote for it)", paid only if that outcome
// resolves; the price is capped at half the rider's payoff (and Nekro's 2 TG prediction cap).
//
// The table: Executive Sanctions on the last agenda, nobody holding action cards (so neither outcome costs anyone a
// card), Xxcha (an AI, the speaker) placed a Trade Rider on For. Sol (an AI) is voting with Jord's votes; Hacan and
// Letnev have none. With Sol abstaining nobody votes and Xxcha breaks the tie For; with Sol voting Against, Against
// wins.
class RiderExtortionTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        test.addSeat("7100000666666666", "hacan", "yellow");
        test.addSeat("7100000777777777", "letnev", "purple");
        test.game.setVp(10);
        test.place("01", "304");
        sol.addPlanet("jord");
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo("Law_For/Against_7_sanctions");
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setStoredValue(
                "agendaStartVoteCounts", "{\"blue\":2,\"red\":0,\"yellow\":0,\"purple\":0,\"black\":0}");
        test.game.setCurrentAgendaVote("for", "red_" + RiderPayoff.TRADE);
        test.isActive(sol, "agendaVoting");
        xxcha.setTg(5);
    }

    private static AiPrompt offerPrompt(Player partner, Promise promise) {
        String owner = "FFCC_" + partner.getFaction() + "_";
        return AiTestGame.prompt(
                "offer",
                PromptSource.PUBLIC,
                NOW,
                owner + PromiseDeals.ACCEPT_PREFIX + promise.id(),
                owner + PromiseDeals.DECLINE_PREFIX + promise.id());
    }

    private Promise extortion(int price) {
        return Promise.offer(
                test.game,
                Promise.Term.NOT_AGAINST,
                sol,
                xxcha,
                sol,
                PromisedVotes.agendaKey(test.game),
                "for",
                0,
                price,
                NOW,
                NOW + 60_000);
    }

    @Test
    void theSeatAboutToVoteAgainstAsksTheRiderHolderToPayItToStayOut() {
        PromiseDeals.Proposal proposal =
                RiderExtortion.offer(test.game, sol, xxcha, outcome -> true).orElseThrow();

        assertThat(proposal.term()).isEqualTo(Promise.Term.NOT_AGAINST);
        assertThat(proposal.promiser()).isEqualTo(sol);
        assertThat(proposal.promisee()).isEqualTo(xxcha);
        assertThat(proposal.target()).isEqualTo("for");
        assertThat(proposal.price()).isBetween(1, 2);
    }

    @Test
    void thePriceIsAtMostHalfTheRider() {
        assertThat(RiderExtortion.cap(test.game, xxcha, new ti4.ai.agenda.AgendaForecast.PlacedRider("for", 5.0)))
                .isEqualTo(2);
    }

    @Test
    void noOfferWithoutTheExperiment() {
        AiExperiments.disable(sol, Set.of(Experiment.RIDER_EXTORTION));

        assertThat(RiderExtortion.offer(test.game, sol, xxcha, outcome -> true)).isEmpty();
    }

    // A seat that already backs another outcome (its ballot is in progress, or it promised its votes) cannot offer to
    // stay out of the vote against For.
    @Test
    void noOfferWhenItsBallotAlreadyBacksAnotherOutcome() {
        assertThat(RiderExtortion.offer(test.game, sol, xxcha, "against"::equals))
                .isEmpty();
    }

    // The rider holder pays when the threat costs it more than the price: Sol's votes would turn For into Against
    // and lose Xxcha its 5 TG.
    @Test
    void theRiderHolderPaysToKeepItsRider() {
        Promise offer = extortion(2);
        PromiseBook.save(test.game, offer);
        AiTurnContext context = test.contextFor(xxcha, Set.of(), NOW, offerPrompt(xxcha, offer));

        assertThat(PromiseDeals.accepts(context, offer)).isTrue();
        assertThat(PromiseDeals.paysOnResolution(test.game, offer)).isTrue();
        assertThat(PromiseDeals.describe(test.game, offer, true)).contains("not vote against");
    }

    @Test
    void theRiderHolderTurnsDownMoreThanItsRiderIsWorth() {
        xxcha.setTg(10);
        Promise offer = extortion(6);
        PromiseBook.save(test.game, offer);
        AiTurnContext context = test.contextFor(xxcha, Set.of(), NOW, offerPrompt(xxcha, offer));

        assertThat(PromiseDeals.accepts(context, offer)).isFalse();
    }
}
