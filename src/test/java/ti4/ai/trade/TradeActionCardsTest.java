package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.testUtils.BaseTi4Test;

/**
 * Arbiters: action cards may be part of a transaction when either side is Hacan (or holds Guild Ships the
 * technology). Nekro (black, the AI) and Sol (blue, a human with Arbiters standing in for Hacan) are neighbours in
 * the action phase of round 3. On this board Nekro values Insubordination at 2.25 and Parley at 0 (no agenda soon,
 * no votes to protect), while the table prices Parley at about 1.57.
 */
class TradeActionCardsTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final long NOW = AiTestGame.NOW;

    private TradeTable table;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.sol.addAbility("arbiters");
        table.stock(table.nekro, 0, 2);
        table.stock(table.sol, 0, 3);
    }

    private static DealItem item(String raw) {
        return DealItem.parse(raw, "nekro", "sol").orElseThrow();
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws).map(TradeActionCardsTest::item).toList());
    }

    private double keep(String alias) {
        return ActionCardValue.keepValue(table.game, table.nekro, alias);
    }

    @Test
    void cardsChangeHandsOnlyWithArbitersOrGuildShips() {
        TradeTable plain = TradeTable.withHumanSol();
        assertThat(TradeActionCards.tradable(plain.nekro, plain.sol)).isFalse();

        plain.nekro.addTech("tf-guildships");
        assertThat(TradeActionCards.tradable(plain.nekro, plain.sol)).isTrue();
        assertThat(TradeActionCards.tradable(table.nekro, table.sol)).isTrue();
    }

    // A card Sol offers is worth what Nekro would keep it for; predicting Sol's side uses only the card's table price.
    @Test
    void aReceivedCardIsWorthItsKeepValue() {
        table.sol.setActionCard("insub", 7);
        DealItem insub = item("sendingsol_receivingnekro_ACs_7");

        assertThat(ComponentValues.receive(table.game, table.nekro, table.sol, insub, 0.8))
                .isCloseTo(keep("insub"), within(EXACT));
        assertThat(ComponentValues.receive(table.game, table.nekro, table.sol, insub, 0.8, true))
                .isCloseTo(ActionCardPrior.keepPrior("insub"), within(EXACT));
        assertThat(ComponentValues.receive(
                        table.game, table.nekro, table.sol, item("sendingsol_receivingnekro_ACs_generic2"), 0.8))
                .isCloseTo(2 * TradeActionCards.UNKNOWN_CARD, within(EXACT));
    }

    // With a full hand of seven, taking a card means discarding the worst one, so only the improvement counts.
    @Test
    void aCardIntoAFullHandOnlyCountsWhatItImproves() {
        String[] hand = {"ghost_ship", "lucky", "plague", "cripple", "mining_initiative", "economic_initiative", "sabo1"
        };
        for (int index = 0; index < hand.length; index++) table.nekro.setActionCard(hand[index], index + 1);
        table.sol.setActionCard("insub", 17);
        String worst = ActionCardValue.discardOrder(table.game, table.nekro).getFirst();

        assertThat(ComponentValues.receive(
                        table.game, table.nekro, table.sol, item("sendingsol_receivingnekro_ACs_17"), 0.8))
                .isCloseTo(keep("insub") - keep(worst), within(EXACT));
    }

    // Nekro gives a card at what it would keep it for, never a Sabotage (it could cancel Nekro's own cards), and
    // never "any card" (it would have to pick one later, through buttons it does not answer).
    @Test
    void givingACardCostsItsKeepValueExceptSabotageOrAnUnnamedCard() {
        table.nekro.setActionCard("parley", 3);
        table.nekro.setActionCard("sabo1", 4);

        assertThat(ComponentValues.give(
                        table.game, table.nekro, table.sol, item("sendingnekro_receivingsol_ACs_3"), 0.8))
                .isCloseTo(keep("parley"), within(EXACT));
        assertThat(ComponentValues.give(
                        table.game, table.nekro, table.sol, item("sendingnekro_receivingsol_ACs_4"), 0.8))
                .isInfinite();
        assertThat(ComponentValues.give(
                        table.game, table.nekro, table.sol, item("sendingnekro_receivingsol_ACs_generic1"), 0.8))
                .isInfinite();
    }

    // Without Arbiters on either side the card is not a legal part of the deal.
    @Test
    void cardsWithoutArbitersAreVetoed() {
        TradeTable plain = TradeTable.withHumanSol();
        plain.nekroAndSolNeighbour();
        Deal deal = deal("sendingsol_receivingnekro_ACs_7", "sendingnekro_receivingsol_TGs_1");

        assertThat(Stinginess.vetoes(plain.game, plain.nekro, plain.sol, deal)).contains(Stinginess.Veto.UNSUPPORTED);
        assertThat(Stinginess.vetoes(table.game, table.nekro, table.sol, deal))
                .doesNotContain(Stinginess.Veto.UNSUPPORTED);
    }

    // Buying: Insubordination (2.25 to Nekro) for 1 trade good clears the 0.5 margin against a human; for 2 it does
    // not, so Nekro does not accept.
    @Test
    void buysACardWorthMoreThanItsPrice() {
        table.sol.setActionCard("insub", 7);

        assertThat(answer(offer(1, "sendingsol_receivingnekro_ACs_7", "sendingnekro_receivingsol_TGs_1")))
                .isEqualTo("acceptOffer_blue_1");
        assertThat(answer(offer(2, "sendingsol_receivingnekro_ACs_7", "sendingnekro_receivingsol_TGs_2")))
                .isNotEqualTo("acceptOffer_blue_2");
    }

    // Selling: Parley is spare (worth nothing to Nekro now), so on its own turn Nekro offers it to Sol for 1 trade
    // good, which also leaves Sol about 0.57 better off; 2 would not be a deal Sol should want.
    @Test
    void sellsItsSpareCardForTradeGoods() {
        table.nekro.setActionCard("parley", 3);

        OwnTurnDeals.start(ownTurn());

        Draft draft = Draft.read(table.test.memory).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.SELL);
        assertThat(draft.partner()).isEqualTo("sol");
        assertThat(draft.target().sameAs(deal("sendingnekro_receivingsol_ACs_3", "sendingsol_receivingnekro_TGs_1")))
                .isTrue();
    }

    // A card Nekro still wants (Insubordination) or a Sabotage is not for sale, and nothing is sold where Arbiters
    // does not apply.
    @Test
    void keepsCardsItWantsAndSellsNothingWithoutArbiters() {
        table.nekro.setActionCard("insub", 3);
        OwnTurnDeals.start(ownTurn());
        assertThat(Draft.read(table.test.memory)).isEmpty();

        TradeTable plain = TradeTable.withHumanSol();
        plain.nekroAndSolNeighbour();
        plain.stock(plain.sol, 0, 3);
        plain.nekro.setActionCard("parley", 3);
        assertThat(CardSales.candidates(plain.game, plain.nekro, plain.sol)).isEmpty();
    }

    // Building the sale: Sol's trade good is asked for first, then the card is picked by its number in Nekro's hand.
    @Test
    void picksItsCardByItsHandNumber() {
        table.nekro.setActionCard("parley", 3);
        table.nekro.setActionCard("insub", 5);
        Set<String> pressed = new HashSet<>();
        Deal sale = deal("sendingnekro_receivingsol_ACs_3", "sendingsol_receivingnekro_TGs_1");
        OfferBuilder.start(
                contextAt(NOW, pressed, TradeButtons.entry("various", NOW - 60_000L)), table.sol, sale, Purpose.SELL);
        table.nekro.addTransactionItem("sendingsol_receivingnekro_TGs_1");

        AiPrompt offering = TradeButtons.builder("builder", NOW + 500, table.nekro, table.nekro, table.sol);
        assertThat(pressedId(OfferBuilder.continueDraft(contextAt(NOW + 1000, pressed, offering))))
                .isEqualTo("newTransact_ACs_black_blue");

        AiPrompt cards = TradeButtons.picker("cards", NOW + 1500, "ACs", table.nekro, table.sol, "3", "5");
        assertThat(pressedId(OfferBuilder.continueDraft(contextAt(NOW + 2000, pressed, cards))))
                .isEqualTo("offerToTransact_ACs_black_blue_3");
    }

    private AiPrompt offer(int number, String... items) {
        table.sol.getTransactionItems().clear();
        for (String raw : items) table.sol.addTransactionItem(raw);
        table.game.setStoredValue("offerFromsolTonekro", String.valueOf(number));
        return TradeButtons.incoming("offer-" + number, NOW - 1_000L, table.sol, number);
    }

    private String answer(AiPrompt offer) {
        OfferBuilder.abandon(table.context());
        return pressedId(OfferResponder.answer(table.test.contextFor(table.nekro, Set.of(), NOW, offer)));
    }

    private AiTurnContext ownTurn() {
        return table.test.contextFor(table.nekro, Set.of(), NOW, TradeButtons.entry("various-nekro", NOW - 60_000L));
    }

    private AiTurnContext contextAt(long now, Set<String> pressed, AiPrompt... prompts) {
        return table.test.contextFor(table.nekro, Set.copyOf(pressed), now, prompts);
    }

    private static String pressedId(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }
}
