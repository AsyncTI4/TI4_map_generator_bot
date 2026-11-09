package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Six seats in table order nekro, sol, hacan, xxcha, letnev, jolnar. Pick order runs round the table from the speaker
// (LRR 80.1), so jolnar is nekro's right-hand neighbour: as speaker jolnar picks first and nekro second. Politics lets
// nekro name any player but the current speaker, itself included (LRR 66.2.i, 80.6). The sale is a deal (LRR 28): the
// trade goods move at once and are binding, while "leave nekro its card next strategy phase" is a non-binding promise
// (LRR 28.4) that the AI keeps. Age of Commerce opens transactions between all players (the real rule needs
// neighbours).
class SpeakerSaleTest extends BaseTi4Test {

    private static final String PREFIX = "FFCC_nekro_sc_3_assign_speaker_to_";

    private AiTestGame test;
    private Player hacan;
    private Player xxcha;
    private Player letnev;
    private Player jolnar;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        hacan = test.addSeat("200000000000000002", "hacan", "yellow");
        xxcha = test.addSeat("200000000000000003", "xxcha", "green");
        letnev = test.addSeat("200000000000000004", "letnev", "red");
        jolnar = test.addSeat("200000000000000005", "jolnar", "purple");
        test.game.setVp(10);
        test.game.setAgeOfCommerceMode(true);
        test.game.setSpeakerUserID(test.nekro.getUserID());
        test.aiIsActive("action");
        for (Player rival : new Player[] {hacan, xxcha, letnev, jolnar}) test.points(rival, 3);
        test.points(test.nekro, 3);
        jolnar.setTg(3);
        for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private AiDecision chooseSpeaker(long now, String... names) {
        AiPrompt card = prompt("politics", PromptSource.PUBLIC, NOW, "sc_follow_3", "sc_no_follow_3", "sc_ac_draw");
        String[] ids = java.util.Arrays.stream(names).map(name -> PREFIX + name).toArray(String[]::new);
        AiPrompt speaker = prompt("speaker", PromptSource.PUBLIC, NOW, ids);
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), 3);
        return StrategyCardRules.resolvePrimary(test.contextFor(test.nekro, Set.of(), now, card, speaker))
                .orElseThrow();
    }

    private AiDecision chooseSpeakerAsSpeaker(long now) {
        return chooseSpeaker(now, "sol", "hacan", "xxcha", "letnev", "jolnar");
    }

    private SpeakerPromise promise() {
        return SpeakerPromise.read(test.game).orElseThrow();
    }

    private void storePromise(SpeakerPromise.Status status, int saleRound, int wanted) {
        new SpeakerPromise("nekro", "jolnar", saleRound, 2, status, NOW, NOW + 60_000, wanted).save(test.game);
        changed();
    }

    // Nekro already holds the token, so it cannot keep it (LRR 80.6). If jolnar declines, nekro gives the token to
    // letnev instead, so jolnar would pick second anyway: the deal gains it no pick place, only the speaker's perks.
    // Holding Mecatol Rex makes those perks 0.5 agenda + 1.5 Imperial = 2, so nekro offers at the 2 trade good
    // baseline with the first-pick promise.
    private void jolnarWantsImperial() {
        jolnar.addPlanet("mr");
        changed();
    }

    @Test
    void offersTheTokenToTheRightNeighbourWhenItIsSpeaker() {
        jolnarWantsImperial();
        AiDecision decision = chooseSpeakerAsSpeaker(NOW);

        assertThat(decision).isInstanceOf(AiDecision.Post.class);
        AiDecision.Post post = (AiDecision.Post) decision;
        assertThat(post.buttons())
                .extracting(Button::getCustomId)
                .containsExactly("FFCC_jolnar_speakerBuy_2_nekro", "FFCC_jolnar_speakerNo_2_nekro");
        assertThat(post.text()).contains("2 trade goods").contains("gives the token to");
        assertThat(promise().status()).isEqualTo(SpeakerPromise.Status.OFFERED);
        assertThat(promise().price()).isEqualTo(2);
    }

    @Test
    void waitsForTheAnswerWhileTheOfferStands() {
        jolnarWantsImperial();
        chooseSpeakerAsSpeaker(NOW);

        assertThat(chooseSpeakerAsSpeaker(NOW + 1000)).isInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void namesTheBuyerOnceItPaid() {
        storePromise(SpeakerPromise.Status.ACCEPTED, 2, SpeakerPromise.NOT_DECLARED);

        assertThat(AiTestGame.pressedId(chooseSpeakerAsSpeaker(NOW))).isEqualTo(PREFIX + "jolnar");
    }

    // No answer in time: the offer lapses. Nekro keeps its word and does not hand jolnar the token for free; it picks
    // the next seat that lets it pick earliest, letnev, exactly the fallback jolnar priced the offer against.
    @Test
    void anUnansweredOfferLapsesAndFallsBack() {
        storePromise(SpeakerPromise.Status.OFFERED, 2, SpeakerPromise.NOT_DECLARED);

        AiDecision decision = chooseSpeakerAsSpeaker(NOW + 120_000);

        assertThat(AiTestGame.pressedId(decision)).isEqualTo(PREFIX + "letnev");
        assertThat(SpeakerSale.speakerIfDeclined(test.game, jolnar, test.nekro)).contains(letnev);
        assertThat(promise().status()).isEqualTo(SpeakerPromise.Status.EXPIRED);
    }

    // Sol holds the token, so nekro may take it. Jolnar cannot pay, so there is no sale and nekro keeps the token.
    @Test
    void takesTheTokenItselfWhenNoSaleIsOn() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        jolnar.setTg(0);
        changed();

        AiDecision decision = chooseSpeaker(NOW, "nekro", "hacan", "xxcha", "letnev", "jolnar");

        assertThat(AiTestGame.pressedId(decision)).isEqualTo(PREFIX + "nekro");
    }

    // Not the speaker, but the sale keeps nekro's first pick and adds 2 trade goods, so it beats keeping the token.
    @Test
    void sellsEvenWhenItCouldTakeTheTokenItself() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        changed();

        AiDecision decision = chooseSpeaker(NOW, "nekro", "hacan", "xxcha", "letnev", "jolnar");

        assertThat(decision).isInstanceOf(AiDecision.Post.class);
    }

    // Jolnar is ahead of nekro and someone is within 4 points of the goal: at the 2 trade good baseline, which is all
    // jolnar's pick gain is worth to it, nekro will not sell, and it also stops handing the token to jolnar for free.
    @Test
    void refusesALeaderLateInTheGame() {
        test.points(jolnar, 3);
        changed();

        AiDecision decision = chooseSpeakerAsSpeaker(NOW);

        assertThat(AiTestGame.pressedId(decision)).isEqualTo(PREFIX + "letnev");
        assertThat(SpeakerPromise.read(test.game)).isEmpty();
    }

    // Sol holds the token, so a decline leaves jolnar last. Holding Mecatol Rex makes jolnar want first pick for
    // Imperial, worth another 1.5 trade goods to it, so nekro charges the leader its full valuation, rounded down:
    // 4 positions x 0.6 + 0.5 agenda + 1.5 = 4.4, so 4.
    @Test
    void chargesALeaderMoreWhenTheTokenIsWorthMoreToIt() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.points(jolnar, 3);
        jolnar.addPlanet("mr");
        jolnar.setTg(5);
        changed();

        AiDecision decision = chooseSpeaker(NOW, "nekro", "hacan", "xxcha", "letnev", "jolnar");

        assertThat(decision).isInstanceOf(AiDecision.Post.class);
        assertThat(promise().price()).isEqualTo(4);
    }

    // Sol holds the token, so if jolnar declines nekro takes it itself and jolnar picks last. In six seats one pick
    // position is worth 1.5 / 2.5 = 0.6 trade goods; jolnar climbs from last (index 5) to the promised second choice
    // (index 1), 2.4 trade goods, plus the speaker's agenda perks (0.5: the test table counts custodians as taken), so
    // it pays 2 but not 3. In three seats it only climbs one place.
    @Test
    void aiBuyerPaysWhenThePickGainCoversThePrice() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        assertThat(SpeakerSale.speakerIfDeclined(test.game, jolnar, test.nekro)).contains(test.nekro);
        assertThat(SpeakerSale.buyerGain(test.game, jolnar, test.nekro))
                .isEqualTo(2.9, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SpeakerSale.buyerAccepts(test.game, jolnar, test.nekro, 2)).isTrue();
        assertThat(SpeakerSale.buyerAccepts(test.game, jolnar, test.nekro, 3)).isFalse();

        jolnar.setTg(1);
        assertThat(SpeakerSale.buyerAccepts(test.game, jolnar, test.nekro, 2)).isFalse();
    }

    // Nekro holds the token (LRR 80.6: it cannot keep it). Declining does not leave jolnar last: nekro would then give
    // the token to letnev, the next seat that lets it pick early, and jolnar picks second. The deal makes jolnar
    // speaker but still second to pick, so it gains no place and only the 0.5 agenda perk: not worth 2.
    @Test
    void aiBuyerPricesTheDealAgainstTheSpeakersRealFallback() {
        assertThat(SpeakerSale.speakerIfDeclined(test.game, jolnar, test.nekro)).contains(letnev);
        assertThat(SpeakerSale.buyerGain(test.game, jolnar, test.nekro))
                .isEqualTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SpeakerSale.buyerAccepts(test.game, jolnar, test.nekro, 2)).isFalse();

        // So nekro makes no offer and gives jolnar the token for free, as without the experiment's sale.
        assertThat(AiTestGame.pressedId(chooseSpeakerAsSpeaker(NOW))).isEqualTo(PREFIX + "jolnar");
        assertThat(SpeakerPromise.read(test.game)).isEmpty();
    }

    // Once jolnar has turned the offer down, nekro does not give it the token for free: the fallback matches the
    // alternative jolnar priced.
    @Test
    void aDeclinedBuyerIsNotHandedTheTokenForFree() {
        storePromise(SpeakerPromise.Status.DECLINED, 2, SpeakerPromise.NOT_DECLARED);

        assertThat(AiTestGame.pressedId(chooseSpeakerAsSpeaker(NOW))).isEqualTo(PREFIX + "letnev");
    }

    // Late in the game a buyer will not hand first pick to a seller that is ahead of it.
    @Test
    void aiBuyerRefusesASellerAheadLate() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.points(test.nekro, 3);
        changed();

        assertThat(SpeakerSale.buyerGain(test.game, jolnar, test.nekro)).isGreaterThan(2);
        assertThat(SpeakerSale.buyerAccepts(test.game, jolnar, test.nekro, 2)).isFalse();
    }

    @Test
    void aThreeSeatBuyerDeclinesAtTwo() {
        AiTestGame small = new AiTestGame();
        Player third = small.addSeat("200000000000000002", "hacan", "yellow");
        third.setTg(5);
        small.game.setSpeakerUserID(small.nekro.getUserID());

        assertThat(SpeakerSale.buyerAccepts(small.game, third, small.nekro, 2)).isFalse();
    }

    @Test
    void aiBuyerPressesBuyOrDecline() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        storePromise(SpeakerPromise.Status.OFFERED, 2, SpeakerPromise.NOT_DECLARED);
        AiPrompt offer = prompt(
                "offer", PromptSource.PUBLIC, NOW, "FFCC_jolnar_speakerBuy_2_nekro", "FFCC_jolnar_speakerNo_2_nekro");

        assertThat(AiTestGame.pressedId(SpeakerSale.answer(test.contextFor(jolnar, Set.of(), NOW, offer))
                        .orElseThrow()))
                .isEqualTo("FFCC_jolnar_speakerBuy_2_nekro");

        // A control buyer has no speaker-sale logic and turns it down.
        AiExperiments.disable(jolnar, EnumSet.of(AiExperiments.Experiment.SPEAKER_SALE));
        assertThat(AiTestGame.pressedId(SpeakerSale.answer(test.contextFor(jolnar, Set.of(), NOW, offer))
                        .orElseThrow()))
                .isEqualTo("FFCC_jolnar_speakerNo_2_nekro");
    }

    // Next round's strategy phase: jolnar, the speaker, picks first but waits for nekro to name its card, then never
    // takes it.
    @Test
    void theBuyerWaitsForTheSellersCardAndLeavesIt() {
        test.game.setRound(3);
        test.game.setSpeakerUserID(jolnar.getUserID());
        test.isActive(jolnar, "strategy");
        storePromise(SpeakerPromise.Status.ACCEPTED, 2, SpeakerPromise.NOT_DECLARED);
        List<PromptButton> picks = List.of(
                PromptButton.of(0, Button.secondary("FFCC_jolnar_scPick_1", "Leadership")),
                PromptButton.of(1, Button.secondary("FFCC_jolnar_scPick_8", "Imperial")));

        assertThat(SpeakerSale.awaitSellerPick(test.contextFor(jolnar, Set.of(), NOW)))
                .get()
                .isInstanceOf(AiDecision.Wait.class);
        assertThat(SpeakerSale.awaitSellerPick(test.contextFor(jolnar, Set.of(), NOW + 3_600_000)))
                .isEmpty();

        storePromise(SpeakerPromise.Status.ACCEPTED, 2, 8);
        assertThat(SpeakerSale.awaitSellerPick(test.contextFor(jolnar, Set.of(), NOW)))
                .isEmpty();
        assertThat(SpeakerSale.withoutPromisedCard(test.contextFor(jolnar, Set.of(), NOW), picks))
                .extracting(PromptButton::handlerId)
                .containsExactly("scPick_1");
    }

    // The promise binds only the strategy phase right after the sale.
    @Test
    void anOldPromiseNoLongerBinds() {
        test.game.setRound(4);
        test.isActive(jolnar, "strategy");
        storePromise(SpeakerPromise.Status.ACCEPTED, 2, 8);
        List<PromptButton> picks = List.of(PromptButton.of(0, Button.secondary("FFCC_jolnar_scPick_8", "Imperial")));

        assertThat(SpeakerSale.withoutPromisedCard(test.contextFor(jolnar, Set.of(), NOW), picks))
                .isEqualTo(picks);
    }

    @Test
    void theSellerNamesItsCardWhenTheStrategyPhaseStarts() {
        test.game.setRound(3);
        test.game.setSpeakerUserID(jolnar.getUserID());
        test.isActive(jolnar, "strategy");
        storePromise(SpeakerPromise.Status.ACCEPTED, 2, SpeakerPromise.NOT_DECLARED);

        SpeakerSale.declare(test.context());

        assertThat(promise().declared()).isTrue();
        assertThat(test.game.getSCList()).contains(promise().wanted());
    }

    // The control seat keeps the old rule, the seat with the fewest points, even with a buyer able to pay.
    @Test
    void controlSeatNamesTheLowestScorer() {
        test.points(hacan, 1);
        test.points(xxcha, 1);
        test.points(letnev, 1);
        test.points(jolnar, 1);
        AiExperiments.disable(test.nekro, EnumSet.of(AiExperiments.Experiment.SPEAKER_SALE));
        changed();

        assertThat(AiTestGame.pressedId(chooseSpeakerAsSpeaker(NOW))).isEqualTo(PREFIX + "sol");
        assertThat(SpeakerPromise.read(test.game)).isEmpty();
    }
}
