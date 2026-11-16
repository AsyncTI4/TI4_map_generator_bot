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
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The user's value review (2026-11-11), Public Execution: "Players usually pay each other not to be executed, starting
// with the speaker, Yssaril, Ral Nel and the points leaders."
//
// The table: Public Execution on the first agenda. Sol (an AI) is the speaker, holds four action cards and has no
// votes; executing it costs it the hand, the speaker token and its votes for the phase. Xxcha (an AI, 6 votes from
// three planets) votes now and, unpaid, puts them all on Sol. Nekro cannot vote and Hacan has no votes, so Xxcha's
// ballot decides the election. Sol pays Xxcha not to vote for it, and pays only if Sol is not elected.
class AvoidanceBribesTest extends BaseTi4Test {

    private static final String EXECUTION = "Directive_Elect Player_7_execution";

    private AiTestGame test;
    private Player sol;
    private Player xxcha;
    private Player hacan;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        hacan = test.addSeat("7100000666666666", "hacan", "yellow");
        test.game.setVp(10);
        test.place("14", "310");
        test.place("19", "311");
        for (String planet : List.of("archonren", "archontau", "wellon")) xxcha.addPlanet(planet);
        for (String card : List.of("sabo1", "sabo2", "overrule", "bribery")) sol.setActionCard(card);
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo(EXECUTION);
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":0,\"red\":6,\"yellow\":0,\"black\":0}");
        test.isActive(xxcha, "agendaVoting");
        sol.setTg(6);
    }

    private PromiseDeals.Proposal solsOffer() {
        AgendaForecast forecast = AgendaForecast.of(test.game, sol);
        return AvoidanceBribes.offer(test.game, forecast, sol, xxcha, "sol", 1.0)
                .orElseThrow();
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

    private Promise accepted() {
        Promise offer = asPromise(solsOffer());
        Promise accepted = offer.accepted(test.game);
        PromiseBook.save(test.game, accepted);
        return accepted;
    }

    // Unpaid, Xxcha elects the speaker, so Sol dreads its own name and offers Xxcha a price to vote elsewhere.
    @Test
    void theSpeakerPaysTheVoterNotToElectIt() {
        assertThat(AgendaVoting.itsBallot(test.game, xxcha).outcome()).isEqualTo("sol");
        assertThat(AvoidanceBribes.dreaded(AgendaForecast.of(test.game, sol), sol))
                .contains("sol");

        PromiseDeals.Proposal proposal = solsOffer();

        assertThat(proposal.term()).isEqualTo(Promise.Term.NOT_FOR);
        assertThat(proposal.promiser()).isEqualTo(xxcha);
        assertThat(proposal.promisee()).isEqualTo(sol);
        assertThat(proposal.target()).isEqualTo("sol");
        assertThat(proposal.price()).isBetween(1, 6);
    }

    // Xxcha takes the money and then votes for someone else (or abstains), never for Sol.
    @Test
    void theVoterTakesTheMoneyAndVotesElsewhere() {
        Promise offer = asPromise(solsOffer());
        PromiseBook.save(test.game, offer);
        AiTurnContext context = test.contextFor(xxcha, Set.of(), NOW, offerPrompt(xxcha, offer));

        assertThat(PromiseDeals.accepts(context, offer)).isTrue();
        assertThat(PromiseDeals.paysOnResolution(test.game, offer)).isTrue();

        PromiseBook.save(test.game, offer.accepted(test.game));
        assertThat(PromisedVotes.avoided(test.game, xxcha)).contains("sol");
        assertThat(AgendaVoting.itsBallot(test.game, xxcha).outcome()).isNotEqualTo("sol");
    }

    // Kept and someone else elected: Sol owes the price.
    @Test
    void solOwesThePriceWhenSomeoneElseIsElected() {
        Promise promise = accepted();
        test.game.setCurrentAgendaVote("hacan", "red_6");

        assertThat(PromisedVotes.verdict(test.game, promise)).isEmpty();
        test.game.setStoredValue("resolvedAgendaId", promise.agenda());
        test.game.setStoredValue("resolvedAgendaOutcome", "hacan");
        assertThat(PromisedVotes.verdict(test.game, promise)).contains(Promise.Status.KEPT);
        assertThat(PromisedVotes.settlement(test.game, promise.with(Promise.Status.KEPT)))
                .contains(Promise.Status.OWED);
    }

    // Kept but Sol elected anyway (by other votes): nothing is owed.
    @Test
    void nothingIsOwedWhenSolIsElectedAnyway() {
        Promise promise = accepted();
        test.game.setStoredValue("resolvedAgendaId", promise.agenda());
        test.game.setStoredValue("resolvedAgendaOutcome", "sol");

        assertThat(PromisedVotes.settlement(test.game, promise.with(Promise.Status.KEPT)))
                .isEmpty();
    }

    @Test
    void votingForSolBreaksThePromise() {
        Promise promise = accepted();
        test.game.setCurrentAgendaVote("sol", "red_6");

        assertThat(PromisedVotes.verdict(test.game, promise)).contains(Promise.Status.BROKEN);
    }

    // In play: Sol posts the offer while Xxcha is voting.
    @Test
    void solPostsTheOfferWhileXxchaVotes() {
        AiPrompt table = AiTestGame.prompt("table", PromptSource.PUBLIC, NOW, "FFCC_xxcha_vote");

        Optional<AiDecision> posted =
                AvoidanceBribes.propose(test.contextFor(sol, Set.of(), NOW, table), List.of(xxcha, hacan));

        assertThat(posted).containsInstanceOf(AiDecision.Post.class);
        assertThat(((AiDecision.Post) posted.orElseThrow()).text())
                .contains("not vote for")
                .contains("does not win");
    }

    @Test
    void onlyOnPublicExecutionAndColonialRedistribution() {
        test.game.setCurrentAgendaInfo("Law_For/Against_7_articles_war");
        AiPrompt table = AiTestGame.prompt("table", PromptSource.PUBLIC, NOW, "FFCC_xxcha_vote");

        assertThat(AvoidanceBribes.propose(test.contextFor(sol, Set.of(), NOW, table), List.of(xxcha, hacan)))
                .isEmpty();
    }

    @Test
    void noOfferWithoutTheExperiment() {
        AiExperiments.disable(sol, Set.of(Experiment.AVOIDANCE_BRIBES));
        AiPrompt table = AiTestGame.prompt("table", PromptSource.PUBLIC, NOW, "FFCC_xxcha_vote");

        assertThat(AvoidanceBribes.propose(test.contextFor(sol, Set.of(), NOW, table), List.of(xxcha, hacan)))
                .isEmpty();
    }
}
