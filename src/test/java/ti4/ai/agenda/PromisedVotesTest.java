package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Vote promises: "I will cast my votes for outcome X on this agenda". A seat that promised casts all its votes for X
// even against its own interest; another seat's vote for a different outcome breaks the promise. Such a deal is
// non-binding under the rules (LRR 28.4, note II.δ), so the AI's only hold on a human is its trust.
class PromisedVotesTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";
    private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";

    private AiTestGame test;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.place("01", "304");
        sol.addPlanet("jord");
        test.place("19", "310");
        xxcha.addPlanet("wellon");
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"red\":2,\"black\":0}");
        test.isActive(sol, "agendawaiting");
    }

    private Promise votesFor(Player promiser, Player promisee, String outcome) {
        return Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                promiser,
                promisee,
                promisee,
                PromisedVotes.agendaKey(test.game),
                outcome,
                4,
                2,
                1,
                60_000);
    }

    @Test
    void aSeatThatPromisedCastsAllItsVotesForTheOutcome() {
        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("sol");

        PromiseBook.save(test.game, votesFor(sol, test.nekro, "nekro").accepted(test.game));

        AgendaVoting.Ballot ballot = AgendaVoting.plan(test.game, sol).orElseThrow();
        assertThat(ballot.outcome()).isEqualTo("nekro");
        assertThat(ballot.votes()).isEqualTo(AgendaVoting.castableVotes(test.game, sol));
    }

    // Sol planned its ballot (for itself) before the buyer accepted its offer. The planned ballot is rebuilt for the
    // promised outcome before any vote is cast, so the promise is kept.
    @Test
    void aBallotPlannedBeforeThePromiseIsRebuilt() {
        AiTurnContext context = test.contextFor(sol, Set.of(), AiTestGame.NOW);
        String key = "agendaBallot|test";
        AgendaVoting.Ballot planned = AgendaVoting.plan(test.game, sol).orElseThrow();
        context.memory().put(key, planned.encode());
        assertThat(AgendaVoting.keepingPromise(context, key, planned)).isEqualTo(planned);

        PromiseBook.save(test.game, votesFor(sol, test.nekro, "nekro").accepted(test.game));

        AgendaVoting.Ballot kept = AgendaVoting.keepingPromise(context, key, planned);
        assertThat(kept.outcome()).isEqualTo("nekro");
        assertThat(context.memory().get(key).flatMap(AgendaVoting.Ballot::decode))
                .contains(kept);
    }

    // A seat that sold its votes, or offered them for sale, plays no rider on this agenda, since a rider would stop it
    // voting; a seat that was only asked to sell is free.
    @Test
    void aVotePromiseForbidsRiders() {
        Promise asked = votesFor(sol, test.nekro, "nekro");
        PromiseBook.save(test.game, asked);
        assertThat(PromisedVotes.forbidsRider(test.game, sol)).isFalse();

        PromiseBook.save(test.game, asked.accepted(test.game));
        assertThat(PromisedVotes.forbidsRider(test.game, sol)).isTrue();
        assertThat(PromisedVotes.forbidsRider(test.game, test.nekro)).isFalse();

        AiExperiments.disable(sol, Set.of(Experiment.PROMISE_DEALS));
        assertThat(PromisedVotes.forbidsRider(test.game, sol)).isFalse();
    }

    // An offer that is not yet accepted does not bind a vote: the seat votes as it likes if the buyer is silent.
    @Test
    void anUnansweredOfferDoesNotBindTheVote() {
        PromiseBook.save(test.game, votesFor(sol, test.nekro, "nekro"));

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("sol");
    }

    @Test
    void controlSeatVotesAsItLikes() {
        PromiseBook.save(test.game, votesFor(sol, test.nekro, "nekro").accepted(test.game));
        AiExperiments.disable(sol, Set.of(Experiment.PROMISE_DEALS));

        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("sol");
    }

    @Test
    void votingForAnotherOutcomeBreaksThePromise() {
        Promise promise = votesFor(xxcha, test.nekro, "nekro").accepted(test.game);
        assertThat(PromisedVotes.verdict(test.game, promise)).isEmpty();

        test.game.setCurrentAgendaVote("xxcha", "red_2");

        assertThat(PromisedVotes.verdict(test.game, promise)).contains(Promise.Status.BROKEN);
    }

    @Test
    void votingForThePromisedOutcomeKeepsIt() {
        Promise promise = votesFor(xxcha, test.nekro, "nekro").accepted(test.game);

        test.game.setCurrentAgendaVote("nekro", "red_2");

        assertThat(PromisedVotes.verdict(test.game, promise)).contains(Promise.Status.KEPT);
    }

    @Test
    void aPromiseForAnEarlierAgendaLapses() {
        Promise promise = votesFor(xxcha, test.nekro, "nekro").accepted(test.game);

        test.game.setCurrentAgendaInfo("Law_Elect Player_8_minister_of_policy");

        assertThat(PromisedVotes.verdict(test.game, promise)).contains(Promise.Status.EXPIRED);
    }

    // Shard of the Throne gives the elected player a point. With Xxcha's bloc the larger one, Xxcha's votes for Sol
    // are worth something to Sol (its chance of the point rises) and cost Xxcha (it gives up its own chance and helps a
    // rival to a point).
    @Test
    void votesAreWorthWhatTheyShiftTheOutcome() {
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"red\":5,\"black\":0}");
        VoteDeals.Terms terms =
                VoteDeals.valued(test.game, test.nekro, xxcha, sol, "sol").orElseThrow();

        assertThat(terms.votes()).isBetween(1, AgendaVoting.castableVotes(test.game, xxcha));
        assertThat(terms.buyerGain()).isPositive();
        assertThat(terms.sellerCost()).isPositive();
    }

    // The deal names the fewest votes that still win the buyer the outcome: one of Xxcha's two votes already puts Sol
    // ahead once Xxcha no longer votes for itself. Without the expert value table the deal buys the whole stack.
    @Test
    void aVoteDealBuysOnlyTheVotesItNeeds() {
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"red\":5,\"black\":0}");
        int castable = AgendaVoting.castableVotes(test.game, xxcha);
        assertThat(castable).isGreaterThan(1);

        assertThat(VoteDeals.valued(test.game, test.nekro, xxcha, sol, "sol")
                        .orElseThrow()
                        .votes())
                .isLessThan(castable);

        AiExperiments.disable(sol, Set.of(Experiment.AGENDA_VALUE_TABLE));
        assertThat(VoteDeals.valued(test.game, test.nekro, xxcha, sol, "sol")
                        .orElseThrow()
                        .votes())
                .isEqualTo(castable);
    }

    // A seller that would feed Nekro a technology by voting for its prediction counts that in its cost of the deal.
    // The buyer runs without the expert value table so both deals name the same (whole) stack of votes.
    @Test
    void aVoteSaleOnNekrosPredictionCostsTheSellerTheGalacticThreat() {
        AiExperiments.disable(sol, Set.of(Experiment.AGENDA_VALUE_TABLE));
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"red\":5,\"black\":0}");
        test.game.setCurrentAgendaVote("sol", "black_" + GalacticThreatCost.RIDER);
        VoteDeals.Terms withThreat =
                VoteDeals.valued(test.game, test.nekro, xxcha, sol, "sol").orElseThrow();
        double threat = GalacticThreatCost.ofBallot(test.game, xxcha, "sol", withThreat.votes());
        assertThat(threat).isPositive();

        AiExperiments.disable(xxcha, Set.of(Experiment.GALACTIC_THREAT_COST));
        VoteDeals.Terms without =
                VoteDeals.valued(test.game, test.nekro, xxcha, sol, "sol").orElseThrow();

        assertThat(withThreat.sellerCost() - without.sellerCost()).isCloseTo(threat, within(1e-9));
    }

    // Votes sold on a first agenda are votes the seller no longer has for the second, priced like its own ballot
    // (0.1 TG a vote); on the last agenda they are free. Without the expert value table the seller does not price them.
    @Test
    void votesSoldBeforeTheLastAgendaCostTheSellerItsNextAgenda() {
        assertThat(VoteDeals.spentVotes(test.game, xxcha, 5)).isZero();

        test.game.setStoredValue("agendaCount", "1");
        assertThat(VoteDeals.spentVotes(test.game, xxcha, 5)).isCloseTo(0.5, within(1e-9));

        AiExperiments.disable(xxcha, Set.of(Experiment.AGENDA_VALUE_TABLE));
        assertThat(VoteDeals.spentVotes(test.game, xxcha, 5)).isZero();
    }

    // A seller whose votes are not needed to win sells the fewest that still lock the outcome: on a first agenda Sol
    // already wins, so Xxcha's deal names fewer votes than its whole stack. Xxcha holds its home planets too, since on
    // a first agenda it keeps its smallest planet back and Wellon alone would leave nothing to sell.
    @Test
    void anUncontestedVoteSaleOnAFirstAgendaNamesFewerVotesThanTheStack() {
        test.place("14", "101");
        xxcha.addPlanet("archonren");
        xxcha.addPlanet("archontau");
        test.game.setStoredValue("agendaCount", "1");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":5,\"black\":0}");
        int castable = AgendaVoting.castableVotes(test.game, xxcha);
        assertThat(castable).isGreaterThan(1);

        VoteDeals.Terms terms =
                VoteDeals.valued(test.game, test.nekro, xxcha, sol, "sol").orElseThrow();

        assertThat(terms.votes()).isLessThan(castable);
    }

    // A binding promise casts the votes agreed, not the promiser's whole stack.
    @Test
    void aPromiseForFewerVotesCastsOnlyThose() {
        Promise promise = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                sol,
                test.nekro,
                test.nekro,
                PromisedVotes.agendaKey(test.game),
                "nekro",
                1,
                2,
                1,
                60_000);
        PromiseBook.save(test.game, promise.accepted(test.game));

        AgendaVoting.Ballot ballot = AgendaVoting.plan(test.game, sol).orElseThrow();
        assertThat(ballot.outcome()).isEqualTo("nekro");
        assertThat(ballot.votes()).isEqualTo(1);
        assertThat(PromisedVotes.promisedVotes(promise, 4)).isEqualTo(1);
        assertThat(PromisedVotes.promisedVotes(votesFor(sol, test.nekro, "nekro"), 3))
                .isEqualTo(3);
    }

    // Once a vote promise is accepted, everyone's forecast puts the promiser's votes on the promised outcome.
    @Test
    void acceptedVotesCountInTheForecast() {
        double before = AgendaForecast.of(test.game, test.nekro).chance("xxcha");

        PromiseBook.save(test.game, votesFor(sol, xxcha, "xxcha").accepted(test.game));

        assertThat(AgendaForecast.of(test.game, test.nekro).chance("xxcha")).isGreaterThan(before);
        assertThat(AgendaForecast.of(test.game, test.nekro).votesLeft(sol)).isZero();
        assertThat(VoteDeals.valued(test.game, test.nekro, sol, xxcha, "xxcha")).isEmpty();
    }
}
