package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Destroy Heretical Works (purge 2 relic fragments in the status phase) makes other players' fragments worth buying.
 * Nekro (black) is the AI seat holding the secret and Sol (blue) its neighbour, a human unless a test says otherwise.
 */
class FragmentSecretsTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final String SOL_CULTURAL = "sendingsol_receivingnekro_Frags_CRF1";
    private static final String SOL_INDUSTRIAL = "sendingsol_receivingnekro_Frags_IRF1";

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        nekro = table.nekro;
        sol = table.sol;
        table.nekroAndSolNeighbour();
        table.stock(nekro, 3, 8);
        table.stock(sol, 2, 2);
        table.points(nekro, 2);
        table.points(sol, 2);
        nekro.setSecret(FragmentSecrets.HERETICAL_WORKS);
    }

    private static DealItem item(String raw) {
        return DealItem.parse(raw, "nekro", "sol").orElseThrow();
    }

    private static void dropSecret(Player player) {
        player.removeSecret(player.getSecretsUnscored().get(FragmentSecrets.HERETICAL_WORKS));
    }

    private List<FragmentSecretDeals.Option> options() {
        return FragmentSecretDeals.best(table.context(), List.of(sol));
    }

    private static int tradeGoodsOffered(Deal deal) {
        return deal.total("nekro", ItemType.TRADE_GOODS);
    }

    private double received(String raw) {
        return ComponentValues.receive(table.game, nekro, sol, item(raw), 0.8);
    }

    @Test
    void chasesFragmentsOnlyWhileShortOfTwoWithTheSecret() {
        assertThat(FragmentSecrets.missing(nekro)).isEqualTo(2);

        nekro.setCrf(1);
        assertThat(FragmentSecrets.missing(nekro)).isEqualTo(1);

        nekro.setUrf(1);
        assertThat(FragmentSecrets.chases(nekro)).isFalse();

        nekro.setCrf(0);
        nekro.setUrf(0);
        dropSecret(nekro);
        assertThat(FragmentSecrets.chases(nekro)).isFalse();
    }

    // Two fragments are one point, so each is worth half of it (3 at the base); the one that completes the pair is
    // worth most of the whole point. The worth of a point rises with the scores.
    @Test
    void theFragmentThatCompletesThePairIsWorthMostOfAPoint() {
        assertThat(received(SOL_CULTURAL)).isCloseTo(3.0, within(EXACT));

        nekro.setCrf(1);
        double completing = received(SOL_INDUSTRIAL);
        assertThat(completing).isCloseTo(0.9 * 6, within(EXACT));

        table.points(nekro, 5);
        assertThat(received(SOL_INDUSTRIAL)).isGreaterThan(completing);
    }

    @Test
    void valuesNothingExtraOnceTwoAreHeld() {
        nekro.setCrf(2);

        assertThat(received(SOL_INDUSTRIAL)).isCloseTo(1.0, within(EXACT));
    }

    // It never sells the fragments the secret needs, however much it is offered.
    @Test
    void refusesToSellFragmentsItNeeds() {
        nekro.setCrf(2);
        Deal sale = new Deal(
                List.of(item("sendingnekro_receivingsol_Frags_CRF1"), item("sendingsol_receivingnekro_TGs_2")));

        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8))
                .isInfinite();
        assertThat(TradeValue.acceptable(table.game, nekro, sol, sale, 0.8)).isFalse();
    }

    @Test
    void offersTradeGoodsForAFragmentTheOtherPlayerHolds() {
        sol.setCrf(1);

        List<FragmentSecretDeals.Option> options = options();

        assertThat(options).isNotEmpty();
        Deal best = options.getFirst().deal();
        assertThat(best.fragments("sol", "CRF")).isEqualTo(1);
        assertThat(tradeGoodsOffered(best)).isEqualTo(FragmentSecrets.FIRST_ASK);
    }

    @Test
    void asksForBothWhenTheyHoldTwoAndItNeedsTwo() {
        sol.setCrf(2);

        assertThat(options()).anyMatch(option -> option.deal().fragments("sol", "CRF") == 2);
    }

    @Test
    void asksForNoMoreThanItIsShort() {
        nekro.setIrf(1);
        sol.setCrf(2);

        assertThat(options()).noneMatch(option -> option.deal().fragments("sol", "CRF") == 2);
        assertThat(options()).anyMatch(option -> option.deal().fragments("sol", "CRF") == 1);
    }

    @Test
    void doesNotBuyWithoutTheSecretOrOnceItHasTwo() {
        sol.setCrf(1);
        nekro.setCrf(2);
        assertThat(FragmentSecretDeals.propose(table.context(), List.of(sol))).isEmpty();

        nekro.setCrf(0);
        dropSecret(nekro);
        assertThat(FragmentSecretDeals.propose(table.context(), List.of(sol))).isEmpty();
    }

    @Test
    void cannotBuyFromAPlayerWithNoFragments() {
        assertThat(options()).isEmpty();
    }

    // An AI seat never sells a fragment, so there is nothing to put to it.
    @Test
    void doesNotApproachAnAiSeatForFragments() {
        TradeTable aiTable = TradeTable.withAiSol();
        aiTable.nekroAndSolNeighbour();
        aiTable.stock(aiTable.nekro, 3, 8);
        aiTable.nekro.setSecret(FragmentSecrets.HERETICAL_WORKS);
        aiTable.sol.setCrf(2);

        assertThat(FragmentSecretDeals.best(aiTable.context(), List.of(aiTable.sol)))
                .isEmpty();
    }

    // Every ask that did not land raises the next one: by 1 early, 2 in the middle rounds and 3 near the end.
    @Test
    void eachUnansweredAskRaisesTheNextByMoreLaterInTheGame() {
        AiMemory memory = new AiMemory();
        table.game.setRound(2);

        assertThat(FragmentSecrets.askFloor(memory, table.game, sol)).isEqualTo(FragmentSecrets.FIRST_ASK);
        FragmentSecrets.asked(memory, sol);
        assertThat(FragmentSecrets.askFloor(memory, table.game, sol)).isEqualTo(FragmentSecrets.FIRST_ASK + 1);

        table.game.setRound(3);
        FragmentSecrets.asked(memory, sol);
        assertThat(FragmentSecrets.askFloor(memory, table.game, sol)).isEqualTo(FragmentSecrets.FIRST_ASK + 4);

        for (int ask = 0; ask < 10; ask++) FragmentSecrets.asked(memory, sol);
        assertThat(FragmentSecrets.askFloor(memory, table.game, sol)).isEqualTo(FragmentSecrets.MAX_PRICE);
    }

    @Test
    void theNextOfferPaysMoreAfterAnAskThatDidNotLand() {
        nekro.setIrf(1);
        sol.setCrf(1);
        table.game.setRound(2);
        int first = tradeGoodsOffered(options().getFirst().deal());

        AiTurnContext context = table.context();
        FragmentSecrets.asked(context.memory(), sol);
        FragmentSecrets.asked(context.memory(), sol);

        assertThat(tradeGoodsOffered(FragmentSecretDeals.best(context, List.of(sol))
                        .getFirst()
                        .deal()))
                .isEqualTo(first + 2);
    }

    @Test
    void startsAnOfferAndRemembersTheAsk() {
        sol.setCrf(1);
        AiPrompt entry = TradeButtons.entry("various", AiTestGame.NOW - 60_000L);
        AiTurnContext context = table.test.contextFor(nekro, Set.of(), AiTestGame.NOW, entry);

        Optional<AiDecision> start = FragmentSecretDeals.propose(context, List.of(sol));

        assertThat(start).isPresent();
        assertThat(Draft.read(table.test.memory).orElseThrow().purpose()).isEqualTo(Purpose.FRAGMENT_DEAL);
        assertThat(FragmentSecrets.askFloor(context.memory(), table.game, sol))
                .isGreaterThan(FragmentSecrets.FIRST_ASK);
    }

    // After a rejection it asks the human what they want for the fragment instead of offering again.
    @Test
    void asksAHumanWhatTheyWantForTheFragmentAfterARejection() {
        assertThat(OfferRejections.question(nekro, sol, OfferRejections.FRAGMENTS_ASKED))
                .contains("what would you want for your relic fragment");
    }

    // The player's own answer to "what do you want": with none held a fragment is worth half a point (3), and the one
    // that completes the pair most of a point, so it pays 4 only for that one.
    @Test
    void takesTheirPriceOnlyWhileTheFragmentIsWorthIt() {
        sol.setCrf(1);

        assertThat(TradeValue.acceptable(table.game, nekro, sol, asking(2), 0.8))
                .isTrue();
        assertThat(TradeValue.acceptable(table.game, nekro, sol, asking(4), 0.8))
                .isFalse();

        nekro.setIrf(1);
        assertThat(TradeValue.acceptable(table.game, nekro, sol, asking(4), 0.8))
                .isTrue();
    }

    private static Deal asking(int tradeGoods) {
        return new Deal(List.of(item(SOL_CULTURAL), item("sendingnekro_receivingsol_TGs_" + tradeGoods)));
    }
}
