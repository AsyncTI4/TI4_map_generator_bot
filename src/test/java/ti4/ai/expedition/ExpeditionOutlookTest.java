package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The value of an expedition is in the tactical planner's currency (a victory point is 8, a trade good 1): the
// breakthrough, the Fracture roll and the Thunder's Edge race it brings, minus what the payment could otherwise buy.
class ExpeditionOutlookTest extends BaseTi4Test {

    private AiTestGame test;
    private Player mahact;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        test.nekro.addBreakthrough("nekrobt");
        mahact = test.addSeat("200000000000000009", "mahact", "green");
        mahact.addBreakthrough("mahactbt");
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private void claim(ExpeditionKind kind, Player claimer) {
        test.game.getExpeditions().getExpeditionFactions().put(kind.id(), claimer.getFaction());
    }

    // A resource is worth 0.2 of a trade good up to round 4 and 0.5 from round 8 on.
    private void lateGame() {
        test.game.setRound(8);
    }

    private void sevenResources(Player seat) {
        test.place("38", "201");
        seat.addPlanet("abyz");
        seat.addPlanet("mordaiii");
    }

    private ExpeditionOutlook.Choice option(Player seat, ExpeditionKind kind) {
        return ExpeditionOutlook.options(test.game, seat, Holdings.of(test.game, seat)).stream()
                .filter(choice -> choice.kind() == kind)
                .findFirst()
                .orElseThrow();
    }

    // A faction that gains five win-rate points from its breakthrough pays 3 trade goods for it.
    @Test
    void aHighLiftFactionPaysThreeTradeGoodsForItsBreakthrough() {
        mahact.setTg(3);

        assertThat(ExpeditionOutlook.best(test.game, mahact))
                .hasValueSatisfying(choice -> assertThat(choice.kind()).isEqualTo(ExpeditionKind.TRADE_GOODS));
    }

    // Early on five resources cost 1.0 in trade goods, which even a faction that loses win rate with its breakthrough
    // pays: it is cheap and nothing is being saved for.
    @Test
    void aLowLiftFactionTakesTheBreakthroughWhenItIsCheap() {
        sevenResources(test.nekro);

        assertThat(ExpeditionOutlook.best(test.game, test.nekro))
                .hasValueSatisfying(choice -> assertThat(choice.kind()).isEqualTo(ExpeditionKind.FIVE_RES));
    }

    // Late in the game the same five resources cost 3.5 in planets spent: the strong breakthrough pays for it and the
    // weak one does not.
    @Test
    void aLowLiftFactionDoesNotSpendSevenResourcesButAHighLiftFactionDoes() {
        lateGame();
        sevenResources(test.nekro);
        sevenResources(mahact);

        assertThat(ExpeditionOutlook.best(test.game, test.nekro)).isEmpty();
        assertThat(ExpeditionOutlook.best(test.game, mahact)).isPresent();
    }

    // The scoring reserve comes first: five trade goods are the Trade Routes objective, so 3 of them cannot go.
    @Test
    void aScoringReserveMakesTheKindUnavailable() {
        test.nekro.setTg(5);
        assertThat(ExpeditionOutlook.options(test.game, test.nekro, Holdings.of(test.game, test.nekro)))
                .extracting(ExpeditionOutlook.Choice::kind)
                .contains(ExpeditionKind.TRADE_GOODS);

        reveal("trade_routes");

        assertThat(ExpeditionOutlook.options(test.game, test.nekro, Holdings.of(test.game, test.nekro)))
                .extracting(ExpeditionOutlook.Choice::kind)
                .doesNotContain(ExpeditionKind.TRADE_GOODS);
    }

    // The reserve also holds the planets back: Erect a Monument takes 8 resources and the seat has exactly 7 plus 1
    // trade good, so five resources cannot be spent without losing the point.
    @Test
    void aScoringReserveKeepsThePlanetsToo() {
        sevenResources(test.nekro);
        test.nekro.setTg(1);
        reveal("monument");

        assertThat(ExpeditionOutlook.options(test.game, test.nekro, Holdings.of(test.game, test.nekro)))
                .extracting(ExpeditionOutlook.Choice::kind)
                .doesNotContain(ExpeditionKind.FIVE_RES);
    }

    @Test
    void anAdjustedWalletValuesTheExpeditionThatTheMoneyAboutToArriveWillPay() {
        Holdings broke = Holdings.of(test.game, mahact);
        assertThat(ExpeditionOutlook.best(test.game, mahact, broke)).isEmpty();

        Holdings afterTrade = broke.withWallet(broke.wallet().withTradeGoods(3));
        assertThat(ExpeditionOutlook.best(test.game, mahact, afterTrade))
                .hasValueSatisfying(choice -> assertThat(choice.kind()).isEqualTo(ExpeditionKind.TRADE_GOODS));
    }

    @Test
    void aTechnologySpecialtyPlanetPaysTheTechSkipKind() {
        test.place("22", "202");
        mahact.addPlanet("tarmann");

        assertThat(ExpeditionOutlook.best(test.game, mahact)).hasValueSatisfying(choice -> {
            assertThat(choice.kind()).isEqualTo(ExpeditionKind.TECH_SKIP);
            assertThat(choice.payment().forResources()).containsExactly("tarmann");
        });
    }

    // Bio-Stims readies the planet again at the end of the turn, so the tech skip costs nothing.
    @Test
    void bioStimsMakesTheTechSkipFree() {
        test.place("22", "202");
        mahact.addPlanet("tarmann");
        mahact.addTech("bs");

        assertThat(option(mahact, ExpeditionKind.TECH_SKIP).cost()).isZero();
    }

    @Test
    void theSecretKindCostsTheLeastPromisingSecretAndNeedsOne() {
        assertThat(ExpeditionOutlook.options(test.game, mahact, Holdings.of(test.game, mahact)))
                .extracting(ExpeditionOutlook.Choice::kind)
                .doesNotContain(ExpeditionKind.SECRET);

        mahact.setSecret("dts");
        mahact.setSecret("sb");

        assertThat(option(mahact, ExpeditionKind.SECRET).cost()).isBetween(0.0, 4.0);
    }

    @Test
    void theActionCardKindNeedsTwoCardsAndCostsTheTwoWorst() {
        assertThat(ExpeditionOutlook.options(test.game, mahact, Holdings.of(test.game, mahact)))
                .extracting(ExpeditionOutlook.Choice::kind)
                .doesNotContain(ExpeditionKind.ACTION_CARDS);

        Holdings drawing = Holdings.of(test.game, mahact).withActionCards(2);
        assertThat(ExpeditionOutlook.options(test.game, mahact, drawing))
                .filteredOn(choice -> choice.kind() == ExpeditionKind.ACTION_CARDS)
                .singleElement()
                .satisfies(choice -> assertThat(choice.cost()).isEqualTo(2 * 1.2));
    }

    // Once the seat has its breakthrough only the Thunder's Edge race is left to pay for another slice.
    @Test
    void aSeatWithItsBreakthroughGainsNothingFromItAgain() {
        mahact.setBreakthroughUnlocked("mahactbt", true);
        mahact.setTg(3);

        assertThat(option(mahact, ExpeditionKind.TRADE_GOODS).breakthrough()).isZero();
    }

    // The last slice is worth a point when it makes the seat the Thunder's Edge holder: tied for the most slices,
    // the seat that claims the last one chooses who places the infantry.
    @Test
    void theLastSliceIsRushedWhenItWinsThundersEdge() {
        test.nekro.setBreakthroughUnlocked("nekrobt", true);
        test.nekro.setTg(3);
        claim(ExpeditionKind.TECH_SKIP, test.nekro);
        claim(ExpeditionKind.FIVE_RES, test.nekro);
        claim(ExpeditionKind.FIVE_INF, test.sol);
        claim(ExpeditionKind.SECRET, test.sol);
        claim(ExpeditionKind.ACTION_CARDS, test.sol);

        assertThat(ExpeditionOutlook.best(test.game, test.nekro)).hasValueSatisfying(choice -> {
            assertThat(choice.kind()).isEqualTo(ExpeditionKind.TRADE_GOODS);
            assertThat(choice.thundersEdge()).isGreaterThan(4);
        });
    }

    // Finishing the expedition while a rival holds more slices hands that rival the infantry: not worth 3 trade goods.
    @Test
    void theLastSliceIsLeftAloneWhenItHandsARivalThundersEdge() {
        test.nekro.setBreakthroughUnlocked("nekrobt", true);
        test.nekro.setTg(3);
        claim(ExpeditionKind.TECH_SKIP, test.nekro);
        claim(ExpeditionKind.FIVE_RES, test.sol);
        claim(ExpeditionKind.FIVE_INF, test.sol);
        claim(ExpeditionKind.SECRET, test.sol);
        claim(ExpeditionKind.ACTION_CARDS, test.sol);

        assertThat(ExpeditionOutlook.best(test.game, test.nekro)).isEmpty();
        assertThat(ThundersEdgeRace.gainFromClaiming(test.game, test.nekro)).isNegative();
    }

    // Other seats that still lack a breakthrough and could pay make the cheap kinds more urgent.
    @Test
    void aRivalThatCouldTakeTheKindAddsUrgency() {
        mahact.setTg(3);
        test.sol.addBreakthrough("solbt");
        test.sol.setTg(3);

        assertThat(option(mahact, ExpeditionKind.TRADE_GOODS).urgency()).isEqualTo(RaceUrgency.PER_RIVAL);

        test.sol.setTg(0);
        assertThat(option(mahact, ExpeditionKind.TRADE_GOODS).urgency()).isZero();
    }

    @Test
    void nothingIsOfferedOnceEverySliceIsClaimed() {
        mahact.setTg(3);
        assertThat(ExpeditionOutlook.isOpen(test.game, mahact)).isTrue();

        for (ExpeditionKind kind : ExpeditionKind.values()) claim(kind, test.sol);

        assertThat(ExpeditionOutlook.isOpen(test.game, mahact)).isFalse();
        assertThat(ExpeditionOutlook.best(test.game, mahact)).isEmpty();
    }
}
