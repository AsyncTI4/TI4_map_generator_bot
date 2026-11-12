package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.AgendaCardRules;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.DiscardBounties;
import ti4.ai.agenda.DiscardBounties.Bounty;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Politics notes after the traced self-play (2026-11-12), "pay to kill a doomed loss": a seat that loses trade goods to
// an agenda whatever it votes offers up to that much for a Veto (any holder) or a Quash (Xxcha), within the user's
// bounty caps (Veto 1-3 TG, Quash 3-5 TG, pooled across payers). The holder counts the bounty when it decides to play,
// and the payer pays once the card was played.
//
// The table: Economic Equality ("For": everyone returns their trade goods and gains 5). Sol (an AI) holds 12 trade
// goods
// and 2 votes; Xxcha (an AI) holds 6 votes and none of its own trade goods, so it carries For and Sol loses 7.
class DiscardBountiesTest extends BaseTi4Test {

    private static final String ECONOMIC_EQUALITY = "Directive_For/Against_7_economic_equality";
    private static final String NEXT_AGENDA = "Law_Elect Player_8_minister_war";

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        nekro = test.nekro;
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        test.game.setVp(10);
        test.place("01", "304");
        sol.addPlanet("jord");
        test.place("14", "310");
        test.place("19", "311");
        xxcha.addPlanet("archonren");
        xxcha.addPlanet("archontau");
        xxcha.addPlanet("wellon");
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo(ECONOMIC_EQUALITY);
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":2,\"red\":6,\"black\":0}");
        test.isActive(xxcha, "agendawaiting");
        sol.setTg(12);
        xxcha.setTg(0);
        nekro.setTg(0);
    }

    private AiTurnContext context(Player seat, AiPrompt... prompts) {
        return test.contextFor(seat, Set.of(), NOW, prompts);
    }

    private static AiPrompt table() {
        return AiTestGame.prompt("turn", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_done");
    }

    private Bounty bounty(Player payer, int veto, int quash) {
        return new Bounty(
                test.game.getRound(),
                PromisedVotes.agendaKey(test.game),
                payer.getFaction(),
                veto,
                quash,
                test.game.getGameStats().getActionCardPlays().size(),
                xxcha.getFaction(),
                xxcha.getStrategicCC(),
                DiscardBounties.Status.OPEN);
    }

    // Sol loses 7 whatever it votes, so it offers the most each card's cap allows: 3 for a Veto and 5 to Xxcha for a
    // Quash, paid only once the card is played. It offers once per agenda.
    @Test
    void aSeatThatLosesWhateverItVotesOffersABounty() {
        nekro.setTg(5);
        xxcha.setTg(5);
        assertThat(AgendaPolicy.expectedLoss(test.game, sol).orElseThrow()).isGreaterThan(DiscardBounties.MIN_LOSS);

        AiDecision.Post post = (AiDecision.Post)
                DiscardBountyDeals.offer(context(sol, table())).orElseThrow();

        Bounty posted = DiscardBounties.postedOnThisAgenda(test.game, sol).orElseThrow();
        assertThat(posted.veto()).isEqualTo(DiscardBounties.VETO_CAP);
        assertThat(posted.quash()).isEqualTo(DiscardBounties.QUASH_CAP);
        assertThat(post.text()).contains("_Veto_").contains("**Quash**").contains("Nothing is paid unless");
        assertThat(DiscardBountyDeals.offer(context(sol, table()))).isEmpty();
    }

    // Several losers pool their offers, but each card's pooled bounty stays within its cap: a second payer offers only
    // what the cap leaves, and the holder counts the pool up to the cap and never its own offer.
    @Test
    void bountiesPoolUpToEachCardsCap() {
        DiscardBounties.save(test.game, bounty(xxcha, 2, 4));
        DiscardBounties.save(test.game, bounty(nekro, 2, 0));

        assertThat(DiscardBounties.vetoBounty(test.game, sol)).isEqualTo(DiscardBounties.VETO_CAP);
        assertThat(DiscardBounties.vetoBounty(test.game, nekro)).isEqualTo(2);
        assertThat(DiscardBounties.quashBounty(test.game, sol)).isEqualTo(4);

        nekro.setTg(5);
        DiscardBountyDeals.offer(context(sol, table()));
        Bounty posted = DiscardBounties.postedOnThisAgenda(test.game, sol).orElseThrow();
        assertThat(posted.veto()).isZero();
        assertThat(posted.quash()).isEqualTo(1);
    }

    // With 7 trade goods of its own, Nekro loses only 0.5 on Economic Equality, too little to spend its Veto; a 3 trade
    // good bounty from Sol makes it worth playing. Without the experiment the bounty is ignored.
    @Test
    void aBountyTipsTheHolderIntoPlayingVeto() {
        nekro.setTg(7);
        nekro.setActionCard("veto", 9);
        assertThat(AgendaCardRules.worthVetoing(test.game, nekro)).isFalse();

        DiscardBounties.save(test.game, bounty(sol, 2, 0));
        assertThat(AgendaCardRules.worthVetoing(test.game, nekro)).isFalse();

        DiscardBounties.save(test.game, bounty(sol, 3, 0));
        assertThat(AgendaCardRules.worthVetoing(test.game, nekro)).isTrue();

        AiExperiments.disable(nekro, Set.of(Experiment.DISCARD_BOUNTIES));
        assertThat(AgendaCardRules.worthVetoing(test.game, nekro)).isFalse();
    }

    // Xxcha (7 trade goods) is near indifferent, so a Quash is not worth its strategy token alone; 3 trade goods from
    // Sol make it worth it.
    @Test
    void aBountyTipsXxchaIntoQuashing() {
        xxcha.setTg(7);
        assertThat(ti4.ai.xxcha.QuashRules.worthQuashing(test.game, xxcha)).isFalse();

        DiscardBounties.save(test.game, bounty(sol, 0, 3));

        assertThat(ti4.ai.xxcha.QuashRules.worthQuashing(test.game, xxcha)).isTrue();
    }

    // Nekro played Veto after Sol posted its bounty, and the agenda was replaced. Sol now owes Nekro the 3 trade goods
    // it offered, settled through the usual bill and pay button.
    @Test
    void aPlayedVetoIsPaid() {
        DiscardBounties.save(test.game, bounty(sol, 3, 5));
        test.game.getGameStats().recordAcPlay(DiscardBounties.VETO, nekro);
        test.game.setCurrentAgendaInfo(NEXT_AGENDA);

        DiscardBountyDeals.observe(context(sol));

        Promise owed = PromiseBook.all(test.game).getFirst();
        assertThat(owed.term()).isEqualTo(Promise.Term.DISCARD_AGENDA);
        assertThat(owed.status()).isEqualTo(Promise.Status.OWED);
        assertThat(owed.isPromiser(nekro)).isTrue();
        assertThat(owed.isPromisee(sol)).isTrue();
        assertThat(owed.price()).isEqualTo(3);
        assertThat(DiscardBounties.all(test.game).getFirst().status()).isEqualTo(DiscardBounties.Status.CLAIMED);

        AiDecision.Post bill =
                (AiDecision.Post) PromiseDeals.answer(context(sol, table())).orElseThrow();
        assertThat(bill.text()).contains("Veto");
    }

    // Xxcha spent a strategy token to Quash the agenda: it collects the Quash bounty.
    @Test
    void aQuashIsPaidToXxcha() {
        DiscardBounties.save(test.game, bounty(sol, 3, 5));
        xxcha.setStrategicCC(xxcha.getStrategicCC() - 1);
        test.game.setCurrentAgendaInfo(NEXT_AGENDA);

        DiscardBountyDeals.observe(context(sol));

        Promise owed = PromiseBook.all(test.game).getFirst();
        assertThat(owed.isPromiser(xxcha)).isTrue();
        assertThat(owed.price()).isEqualTo(5);
    }

    // Once voting starts on the same agenda nobody discarded it, so the bounty lapses and nothing is owed.
    @Test
    void anUnclaimedBountyLapsesWhenVotingStarts() {
        DiscardBounties.save(test.game, bounty(sol, 3, 5));
        test.game.setPhaseOfGame("agendaVoting");

        DiscardBountyDeals.observe(context(sol));

        assertThat(DiscardBounties.all(test.game).getFirst().status()).isEqualTo(DiscardBounties.Status.LAPSED);
        assertThat(PromiseBook.all(test.game)).isEmpty();
    }

    // A Veto holder waits a little before passing on its "when" while an AI seat in pain may still post a bounty, and
    // stops waiting once it has, or once the wait runs out.
    @Test
    void aHolderWaitsForAPayerInPain() {
        nekro.setTg(7);
        AiTurnContext holder = context(nekro);
        assertThat(DiscardBounties.awaitOffers(holder)).containsInstanceOf(AiDecision.Wait.class);

        DiscardBounties.save(test.game, bounty(sol, 3, 0));
        assertThat(DiscardBounties.awaitOffers(holder)).isEmpty();

        assertThat(DiscardBounties.awaitOffers(test.contextFor(nekro, Set.of(), NOW + 120_000L)))
                .isEmpty();
    }

    @Test
    void aBountyRecordSurvivesASaveRoundTrip() {
        Bounty bounty = bounty(sol, 2, 4);
        DiscardBounties.save(test.game, bounty);

        assertThat(DiscardBounties.all(test.game)).containsExactly(bounty);
        assertThat(List.of(DiscardBounties.Status.values())).contains(bounty.status());
    }
}
