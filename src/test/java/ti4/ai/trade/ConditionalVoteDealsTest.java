package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Politics notes after the traced self-play (2026-11-12): a vote deal is made when the seller is about to vote, and
// it is conditional: "I pay you X TG to put your votes on O, if it resolves O". Nothing changes hands up front; the
// buyer pays after the agenda resolves, and only when O won.
//
// The table: Shard of the Throne on the last agenda, Sol (an AI) the speaker and so voting last. Xxcha holds 6 votes
// and 4 points; Hacan and Letnev hold 3 each, Sol 2. Seats join in the order Nekro, Sol, Xxcha, Hacan, Letnev, so the
// voting order is Xxcha, Hacan, Letnev, Nekro (who cannot vote), Sol.
class ConditionalVoteDealsTest extends BaseTi4Test {

    private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";

    private AiTestGame test;
    private Player sol;
    private Player xxcha;
    private Player hacan;
    private Player letnev;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        hacan = test.addSeat("7100000666666666", "hacan", "yellow");
        letnev = test.addSeat("7100000777777777", "letnev", "purple");
        test.game.setVp(10);
        test.place("01", "304");
        sol.addPlanet("jord");
        test.place("14", "310");
        test.place("19", "311");
        xxcha.addPlanet("archonren");
        xxcha.addPlanet("archontau");
        xxcha.addPlanet("wellon");
        test.place("28", "312");
        hacan.addPlanet("torkan");
        test.place("27", "313");
        test.place("26", "314");
        letnev.addPlanet("newalbion");
        letnev.addPlanet("starpoint");
        letnev.addPlanet("lodor");
        test.points(xxcha, 4);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setStoredValue(
                "agendaStartVoteCounts", "{\"blue\":2,\"red\":6,\"yellow\":3,\"purple\":3,\"black\":0}");
        test.isActive(xxcha, "agendaVoting");
        sol.setTg(10);
    }

    private AiTurnContext context(Player seat, AiPrompt... prompts) {
        return test.contextFor(seat, Set.of(), NOW, prompts);
    }

    private static AiPrompt table() {
        return AiTestGame.prompt("turn", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_done");
    }

    private Promise sold(Player seller, String outcome, int price) {
        return Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                seller,
                sol,
                sol,
                PromisedVotes.agendaKey(test.game),
                outcome,
                3,
                price,
                NOW,
                NOW + 60_000);
    }

    // Hacan votes next (Xxcha is voting), so Sol may approach it; Letnev votes later, so Sol waits for its turn.
    @Test
    void aBuyerApproachesOnlyTheSeatAboutToVote() {
        assertThat(VoteDeals.aboutToVote(test.game, xxcha)).isTrue();
        assertThat(VoteDeals.aboutToVote(test.game, hacan)).isTrue();
        assertThat(VoteDeals.aboutToVote(test.game, letnev)).isFalse();

        PromiseDeals.proposeVotes(context(sol, table()), List.of(letnev));
        assertThat(PromiseBook.proposedBy(test.game, sol)).isEmpty();
    }

    // Neither Hacan's 3 votes nor Letnev's 3 alone lift Sol (2) past Xxcha's 6, but both together do (8). Sol offers
    // Hacan, the seat about to vote, its part of that bloc; Letnev is approached at its own turn.
    @Test
    void aBuyerBuildsABlocWhenNoSingleSellerIsDecisive() {
        VoteDeals.Outlook outlook = VoteDeals.outlook(test.game, sol);
        double alone = VoteDeals.bestPurchase(outlook, sol, hacan, outcome -> true)
                .map(VoteDeals.Terms::buyerGain)
                .orElse(0.0);
        List<VoteDeals.Bloc> blocs = VoteDeals.blocs(outlook, sol, hacan, List.of(letnev), "sol"::equals);
        assertThat(blocs).hasSize(1);
        assertThat(blocs.getFirst().members()).hasSize(2);
        assertThat(blocs.getFirst().buyerGain()).isGreaterThan(alone + 1);

        AiDecision.Post post = (AiDecision.Post)
                PromiseDeals.proposeVotes(context(sol, table()), List.of(hacan)).orElseThrow();

        Promise offer = PromiseBook.proposedBy(test.game, sol).getFirst();
        assertThat(offer.isPromiser(hacan)).isTrue();
        assertThat(offer.target()).isEqualTo("sol");
        assertThat(post.text()).contains("only if it resolves");
    }

    // The seller is paid only with the chance the outcome wins, so it asks more than for cash up front, and the buyer
    // weighs its price by the same chance. Below a 20% chance no conditional deal is struck.
    @Test
    void conditionalPricesScaleWithTheChanceOfPayment() {
        assertThat(PromiseDeals.sellerPrice(4.0, 0.5, sol, 10, 0, 1)).contains(3);
        assertThat(PromiseDeals.sellerPrice(4.0, 0.5, sol, 10, 0, 0.5)).contains(7);
        assertThat(PromiseDeals.buyerPrice(3.0, 1.0, hacan, 10, 0, 0, 0.5)).contains(3);
        assertThat(PromiseDeals.buyerPrice(3.0, 1.0, hacan, 10, 0, 0, 0.1)).isEmpty();
        assertThat(PromiseDeals.voteFloor(1.0, 0.5)).isEqualTo(3);
    }

    // Hacan kept its promise. Once the agenda resolves for Sol, Sol owes the price; if it resolves another way, nothing
    // is owed. A deal from a seat without the experiment was paid up front and is left alone.
    @Test
    void aKeptPromiseIsOwedOnlyWhenItsOutcomeWins() {
        Promise kept = sold(hacan, "sol", 2).accepted(test.game).with(Promise.Status.KEPT);
        PromiseBook.save(test.game, kept);
        test.game.setStoredValue("resolvedAgendaId", "shard_of_the_throne");

        test.game.setStoredValue("resolvedAgendaOutcome", "xxcha");
        assertThat(PromiseDeals.settled(context(sol), kept)).isEmpty();

        test.game.setStoredValue("resolvedAgendaOutcome", "sol");
        assertThat(PromiseDeals.settled(context(sol), kept)).contains(Promise.Status.OWED);

        AiExperiments.disable(sol, Set.of(Experiment.CONDITIONAL_VOTE_DEALS));
        assertThat(PromiseDeals.settled(context(sol), kept)).isEmpty();
    }

    // An AI that owes posts the bill with a pay button it owns, then presses it; it posts the bill only once.
    @Test
    void anAiBuyerPostsItsBillThenPays() {
        Promise owed = sold(hacan, "sol", 2).accepted(test.game).with(Promise.Status.OWED);
        PromiseBook.save(test.game, owed);
        AiTurnContext first = context(sol, table());

        AiDecision.Post bill = (AiDecision.Post) PromiseDeals.answer(first).orElseThrow();
        assertThat(bill.text()).contains("owes").contains("2 trade goods");
        assertThat(bill.buttons()).hasSize(1);
        assertThat(bill.buttons().getFirst().getCustomId()).endsWith(PromiseDeals.PAY_PREFIX + owed.id());
        assertThat(PromiseDeals.answer(context(sol, table()))).isEmpty();

        AiPrompt billPrompt =
                AiTestGame.prompt("bill", PromptSource.PUBLIC, NOW, "FFCC_sol_" + PromiseDeals.PAY_PREFIX + owed.id());
        Optional<AiDecision> pay = PromiseDeals.answer(context(sol, billPrompt));
        assertThat(pay.map(AiTestGame::pressedId)).contains("FFCC_sol_" + PromiseDeals.PAY_PREFIX + owed.id());
    }

    // The AI seller of a conditional deal values it at the chance it gets paid (Sol is unlikely to win on Hacan's
    // votes alone), so the least it takes is higher than for cash up front.
    @Test
    void aSellerWeighsAConditionalOfferByItsChanceOfPayment() {
        int conditional = leastAccepted();

        AiExperiments.disable(sol, Set.of(Experiment.CONDITIONAL_VOTE_DEALS));
        int upFront = leastAccepted();

        assertThat(upFront).isLessThan(Integer.MAX_VALUE);
        assertThat(conditional).isGreaterThan(upFront);
    }

    private int leastAccepted() {
        for (int price = 1; price <= sol.getTg(); price++) {
            Promise offer = sold(hacan, "sol", price);
            PromiseBook.save(test.game, offer);
            if (PromiseDeals.accepts(context(hacan), offer)) return price;
        }
        return Integer.MAX_VALUE;
    }
}
