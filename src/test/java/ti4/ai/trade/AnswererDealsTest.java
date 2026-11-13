package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The user's note on traced self-play (2026-11-13), Ixthian Artifact: Hacan (16 votes) stood to lose about 8.7 TG if
// For won and abstained, because Xxcha (32 votes, liking For) would answer an Against vote. "A seat facing a big loss
// should pay the seat that would answer its vote (e.g. Hacan pays Xxcha not to vote For on Ixthian)."
//
// The table: Ixthian Artifact on the last agenda. Sol (an AI, 6 votes, voting now) holds Mecatol Rex with four
// dreadnoughts, three cruisers and two infantry, so the blast would cost it about 27 TG. Xxcha (the speaker, 6 votes
// from three planets, nothing near the centre) votes last and likes For. Nekro cannot
// vote and Hacan has no votes, so Against wins exactly when Sol votes
// Against and Xxcha stays out (a 6-6 tie goes to the speaker, Xxcha).
class AnswererDealsTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private Player xxcha;
    private Player hacan;
    private int customPoints;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        hacan = test.addSeat("7100000666666666", "hacan", "yellow");
        test.game.setVp(10);
        test.game.setRound(3);
        Tile mecatol = test.place("18", "000");
        sol.addPlanet("mr");
        test.units(mecatol, "space", sol, UnitType.Dreadnought, 4);
        test.units(mecatol, "space", sol, UnitType.Cruiser, 3);
        test.units(mecatol, "mr", sol, UnitType.Infantry, 2);
        test.place("14", "310");
        test.place("19", "311");
        for (String planet : java.util.List.of("archonren", "archontau", "wellon")) xxcha.addPlanet(planet);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_artifact");
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":6,\"red\":6,\"yellow\":0,\"black\":0}");
        test.isActive(sol, "agendaVoting");
        sol.setTg(10);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(
                player.getUserID(), test.game.addCustomPO("Answerer points " + ++customPoints, points));
    }

    private PromiseDeals.Proposal solsOffer() {
        return AnswererDeals.offer(test.game, sol, xxcha, outcome -> true, 1.0).orElseThrow();
    }

    private Promise asPromise(PromiseDeals.Proposal proposal) {
        return Promise.offer(
                test.game,
                proposal.term(),
                proposal.promiser(),
                proposal.promisee(),
                sol,
                PromisedVotes.agendaKey(test.game),
                proposal.target(),
                0,
                proposal.price(),
                NOW,
                NOW + 60_000);
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

    @Test
    void theSeatFacingTheBlastPaysTheAnswererToStayOutOfTheVote() {
        // Sol's own Against ballot alone loses: Xxcha answers it.
        assertThat(AgendaVoting.itsBallot(test.game, sol).forecast().chance("against"))
                .isLessThan(0.5);

        PromiseDeals.Proposal proposal = solsOffer();

        assertThat(proposal.term()).isEqualTo(Promise.Term.NOT_AGAINST);
        assertThat(proposal.promiser()).isEqualTo(xxcha);
        assertThat(proposal.promisee()).isEqualTo(sol);
        assertThat(proposal.target()).isEqualTo("against");
        assertThat(proposal.price()).isBetween(1, 10);
    }

    // Xxcha weighs the trade goods against the damage the blast does to Sol: at the price Sol offers it accepts, and
    // once the deal stands Sol casts its own votes Against.
    @Test
    void theAnswererTakesTheMoneyAndThePayerVotes() {
        Promise offer = asPromise(solsOffer());
        PromiseBook.save(test.game, offer);
        AiTurnContext context = test.contextFor(xxcha, Set.of(), NOW, offerPrompt(xxcha, offer));

        assertThat(PromiseDeals.accepts(context, offer)).isTrue();
        assertThat(PromiseDeals.paysOnResolution(test.game, offer)).isTrue();

        PromiseBook.save(test.game, offer.accepted(test.game));
        assertThat(AgendaVoting.itsBallot(test.game, sol).outcome()).isEqualTo("against");
    }

    @Test
    void theAnswererTurnsDownTooLittle() {
        Promise offer = asPromise(new PromiseDeals.Proposal(Promise.Term.NOT_AGAINST, xxcha, sol, "against", 0, 1, 0));
        PromiseBook.save(test.game, offer);
        AiTurnContext context = test.contextFor(xxcha, Set.of(), NOW, offerPrompt(xxcha, offer));

        assertThat(AnswererDeals.answererView(test.game, xxcha, sol, "against")
                        .orElseThrow()
                        .answererCost())
                .isGreaterThan(1.0);
        assertThat(PromiseDeals.accepts(context, offer)).isFalse();
    }

    // Hurting the leader is worth more: Xxcha charges more to spare Sol's fleet when Sol leads than when Hacan leads,
    // because Sol's loss then helps Xxcha less.
    @Test
    void sparingTheLeaderCostsTheAnswererMore() {
        points(hacan, 7);
        double thirdLeads = AnswererDeals.answererView(test.game, xxcha, sol, "against")
                .orElseThrow()
                .answererCost();
        test.game.getScoredPublicObjectives().clear();
        points(sol, 7);
        double payerLeads = AnswererDeals.answererView(test.game, xxcha, sol, "against")
                .orElseThrow()
                .answererCost();

        assertThat(payerLeads).isGreaterThan(thirdLeads);
    }

    @Test
    void noOfferWithoutTheExperiment() {
        AiExperiments.disable(sol, Set.of(Experiment.PAY_THE_ANSWERER));

        assertThat(AnswererDeals.offer(test.game, sol, xxcha, outcome -> true, 1.0))
                .isEmpty();
    }

    // Only a seat voting after the payer can answer its vote.
    @Test
    void noOfferToASeatThatVotesBefore() {
        assertThat(AnswererDeals.offer(test.game, xxcha, sol, outcome -> true, 1.0))
                .isEmpty();
    }

    // In play: at its turn Sol posts the offer to Xxcha, then waits for the answer before it casts its votes.
    @Test
    void thePayerPostsTheOfferAndWaitsForTheAnswerBeforeVoting() {
        AiPrompt table = AiTestGame.prompt("table", PromptSource.PUBLIC, NOW, "FFCC_sol_vote");

        Optional<AiDecision> posted = TradeRules.payTheAnswerer(test.contextFor(sol, Set.of(), NOW, table));

        assertThat(posted).containsInstanceOf(AiDecision.Post.class);
        assertThat(((AiDecision.Post) posted.orElseThrow()).text()).contains("not vote against");
        assertThat(TradeRules.payTheAnswerer(test.contextFor(sol, Set.of(), NOW + 1, table)))
                .containsInstanceOf(AiDecision.Wait.class);
    }
}
