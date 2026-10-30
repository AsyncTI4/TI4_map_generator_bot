package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.deal.HumanAiDealStore;
import ti4.ai.trade.TradeCardRules.FollowChoice;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Hacan as the Trade facilitator and as the owner of Carth of Golden Sands. Goal 10, round 3, the action phase. Sol (the
 * second AI) holds the played Trade card; Nekro and Letnev followed it with 3 commodities each (an X-1 fee of 1);
 * Hacan (the third AI, 6 commodities, 2 trade goods) is the active player.
 */
class HacanTradeTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();
    private static final int TRADE = 5;
    private static final double EXACT = 1e-9;

    private int entries;
    private TradeTable table;
    private Player hacan;
    private Player letnev;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        hacan = table.aiSeat("hacan", "yellow");
        letnev = table.secondAiSeat("letnev", "red");
        table.sol.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.nekro.setCommoditiesBase(3);
        letnev.setCommoditiesBase(3);
        table.stock(table.nekro, 3, 0);
        table.stock(letnev, 3, 0);
        table.stock(table.sol, 0, 3);
        table.stock(hacan, 6, 5);
        followed(table.nekro);
        followed(letnev);
        followed(hacan);
        table.test.isActive(hacan, "action");
    }

    private void followed(Player follower) {
        String key = "followedSC" + TRADE + "_" + table.game.getRound();
        table.game.setStoredValue(key, table.game.getStoredValue(key) + "_" + follower.getFaction());
    }

    private AiTurnContext hacanContext() {
        return table.test.contextFor(hacan, Set.of(), NOW, TradeButtons.entry("various-" + ++entries, NOW - MINUTE));
    }

    private Optional<AiDecision> hacanTurn() {
        return OwnTurnDeals.start(hacanContext());
    }

    private Draft draft() {
        return Draft.read(table.test.memoryOf(hacan)).orElseThrow();
    }

    private void abandon() {
        OfferBuilder.abandon(hacanContext());
    }

    private static Deal deal(String sender, String receiver, String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, sender, receiver).orElseThrow())
                .toList());
    }

    // On its turn Hacan washes with each follower that still owes the holder: the follower sends its 3 commodities and
    // gets 2 back (X-1 net), so Hacan keeps the 1 that is the follower's fee. Once both accepted (they are marked paid
    // by Hacan), a single offer sends the 2 collected fees to Sol as trade goods (Hacan holds 5).
    @Test
    void washesWithEachOwingFollowerThenSettlesTheirFeesWithTheHolder() {
        assertThat(hacanTurn().map(AiTestGame::pressedId)).contains("transaction");
        assertThat(draft().partner()).isEqualTo("nekro");
        assertThat(draft().purpose()).isEqualTo(Purpose.FACILITATED_WASH);
        assertThat(draft().target().total("nekro", ItemType.COMMODITIES)).isEqualTo(3);
        assertThat(draft().target().total("hacan", ItemType.COMMODITIES)).isEqualTo(2);
        abandon();

        hacanTurn();
        assertThat(draft().partner()).isEqualTo("letnev");
        assertThat(draft().purpose()).isEqualTo(Purpose.FACILITATED_WASH);
        abandon();

        TradeCardRules.stampPaid(table.game, table.nekro, hacan);
        TradeCardRules.stampPaid(table.game, letnev, hacan);
        hacanTurn();

        assertThat(draft().partner()).isEqualTo("sol");
        assertThat(draft().purpose()).isEqualTo(Purpose.FACILITATION_SETTLE);
        assertThat(draft().target().total("hacan", ItemType.TRADE_GOODS)).isEqualTo(2);
        assertThat(draft().target().total("hacan", ItemType.SEND_DEBT)).isZero();
    }

    // Letnev paid Sol itself, so only Nekro's fee of 1 is collected. Without any trade good to send, Hacan signs 1 debt
    // to
    // Sol instead.
    @Test
    void passesTheFeesOnAsDebtWithoutTradeGoods() {
        table.stock(hacan, 6, 0);
        TradeCardRules.stampPaid(table.game, letnev, table.sol);
        hacanTurn();
        assertThat(draft().partner()).isEqualTo("nekro");
        abandon();
        TradeCardRules.stampPaid(table.game, table.nekro, hacan);

        hacanTurn();

        assertThat(draft().partner()).isEqualTo("sol");
        assertThat(draft().purpose()).isEqualTo(Purpose.FACILITATION_SETTLE);
        assertThat(draft().target().total("hacan", ItemType.TRADE_GOODS)).isZero();
        assertThat(draft().target().total("hacan", ItemType.SEND_DEBT)).isEqualTo(1);
    }

    // A follower the holder already settled with owes nothing: Hacan goes to the next one and never offers the first.
    @Test
    void skipsAFollowerAlreadySettledWithTheHolder() {
        TradeCardRules.stampPaid(table.game, table.nekro, table.sol);

        hacanTurn();

        assertThat(draft().partner()).isEqualTo("letnev");
        assertThat(draft().purpose()).isEqualTo(Purpose.FACILITATED_WASH);
        abandon();

        TradeCardRules.stampPaid(table.game, letnev, table.sol);
        hacanTurn();
        assertThat(Draft.read(table.test.memoryOf(hacan)).map(Draft::purpose).filter(Purpose.FACILITATED_WASH::equals))
                .isEmpty();
    }

    // A follower that neighbours the holder pays it directly, so Hacan skips Nekro and washes with Letnev instead.
    @Test
    void doesNotFacilitateForAFollowerThatNeighboursTheHolder() {
        table.nekroAndSolNeighbour();

        hacanTurn();

        assertThat(draft().partner()).isEqualTo("letnev");
        assertThat(draft().purpose()).isEqualTo(Purpose.FACILITATED_WASH);
    }

    @Test
    void aNeighbouringFollowerRefusesTheFacilitatedWash() {
        table.nekroAndSolNeighbour();
        AiTurnContext nekro = table.test.contextFor(table.nekro, Set.of(), NOW);
        Deal wash =
                deal("nekro", "hacan", "sendingnekro_receivinghacan_Comms_3", "sendinghacan_receivingnekro_Comms_2");

        assertThat(Facilitation.accepts(nekro, hacan, wash)).isFalse();
    }

    // A holder that leads the table is not helped, and neither is a follower Hacan would be feeding.
    @Test
    void doesNotFacilitateForARivalLeader() {
        table.points(letnev, 8);
        TradeCardRules.stampPaid(table.game, table.nekro, table.sol);

        hacanTurn();

        assertThat(Draft.read(table.test.memoryOf(hacan)).map(Draft::purpose).filter(Purpose.FACILITATED_WASH::equals))
                .isEmpty();
    }

    // Once Hacan has settled a follower's fee, the holder skips that follower instead of billing it again.
    @Test
    void theHolderDoesNotChargeAFollowerHacanSettled() {
        table.nekroAndSolNeighbour();
        TradeCardRules.recordPlay(holderContext());
        TradeCardRules.stampPaid(table.game, table.nekro, hacan);
        TradeCardRules.stampPaid(table.game, letnev, hacan);

        assertThat(TradeCardRules.settle(holderContext())).isEmpty();
    }

    @Test
    void theHolderBillsAFollowerNobodySettled() {
        table.nekroAndSolNeighbour();
        TradeCardRules.recordPlay(holderContext());
        TradeCardRules.stampPaid(table.game, letnev, hacan);

        assertThat(TradeCardRules.settle(holderContext()).map(AiTestGame::pressedId))
                .contains("transaction");
    }

    private AiTurnContext holderContext() {
        return table.test.contextFor(table.sol, Set.of(), NOW, TradeButtons.entry("holder-" + ++entries, NOW - MINUTE));
    }

    // A follower that holds its commodities for the holder still takes Hacan's washing offer, in the announced shape
    // (its commodities for at most 2 fewer back); a Hacan that is close to winning is not helped.
    @Test
    void aFollowerAcceptsTheFacilitatedWashShape() {
        AiTurnContext nekro = table.test.contextFor(table.nekro, Set.of(), NOW);
        Deal wash =
                deal("nekro", "hacan", "sendingnekro_receivinghacan_Comms_3", "sendinghacan_receivingnekro_Comms_2");

        assertThat(Facilitation.accepts(nekro, hacan, wash)).isTrue();
        assertThat(Facilitation.accepts(nekro, hacan, deal("nekro", "hacan", "sendingnekro_receivinghacan_Comms_3")))
                .isFalse();
        assertThat(Facilitation.accepts(
                        nekro,
                        hacan,
                        deal(
                                "nekro",
                                "hacan",
                                "sendingnekro_receivinghacan_Comms_3",
                                "sendinghacan_receivingnekro_TGs_2")))
                .isFalse();

        assertThat(Facilitation.accepts(
                        nekro,
                        hacan,
                        deal(
                                "nekro",
                                "hacan",
                                "sendingnekro_receivinghacan_Comms_3",
                                "sendinghacan_receivingnekro_Comms_3")))
                .isFalse();

        TradeCardRules.stampPaid(table.game, table.nekro, table.sol);
        assertThat(Facilitation.accepts(nekro, hacan, wash)).isFalse();
    }

    @Test
    void aFollowerRefusesAHacanThatIsCloseToWinning() {
        AiTurnContext nekro = table.test.contextFor(table.nekro, Set.of(), NOW);
        Deal wash =
                deal("nekro", "hacan", "sendingnekro_receivinghacan_Comms_3", "sendinghacan_receivingnekro_Comms_2");
        table.points(hacan, 8);

        assertThat(Facilitation.accepts(nekro, hacan, wash)).isFalse();
    }

    // Hacan follows an AI holder's Trade for free and used to freeze its whole stack for 10 minutes. It now reserves
    // only what the holder (2 commodities) could wash with it: 2 of 6, leaving 4 free for the followers.
    @Test
    void aMastersOfTradeFollowerReservesOnlyWhatTheHolderCanMatch() {
        table.sol.setCommodities(2);
        TradeCardRules.followPressed(table.test.contextFor(hacan, Set.of(), NOW), table.sol, FollowChoice.FREE);

        AiTurnContext soon = table.test.contextFor(hacan, Set.of(), NOW + 9 * MINUTE);
        assertThat(TradeCardRules.reservedCommodities(soon)).isEqualTo(2);
        assertThat(TradeBudget.freeCommodities(soon)).isEqualTo(4);
    }

    private void giveHacanTheAgent() {
        hacan.addLeader("hacanagent");
        table.stock(hacan, 6, 0);
        table.stock(table.nekro, 3, 0);
        table.stock(letnev, 3, 0);
        table.stock(table.sol, 0, 0);
        table.sol.setCommoditiesBase(4);
    }

    // Sol sits at 0 of 4, is not a rival, and Hacan has a commodity to give: it is the target.
    @Test
    void carthTargetsTheEmptiestNonRival() {
        giveHacanTheAgent();

        assertThat(CarthDeals.bestTarget(hacanContext())).containsSame(table.sol);
    }

    @Test
    void carthSkipsARivalLeaderAndAFullStack() {
        giveHacanTheAgent();
        table.points(table.sol, 8);

        assertThat(CarthDeals.bestTarget(hacanContext())).isEmpty();

        TradeTable calm = TradeTable.withAiSol();
        Player other = calm.aiSeat("hacan", "yellow");
        other.addLeader("hacanagent");
        calm.stock(other, 1, 0);
        calm.stock(calm.sol, 4, 0);
        calm.stock(calm.nekro, 3, 0);
        calm.sol.setCommoditiesBase(4);
        calm.test.isActive(other, "action");
        assertThat(CarthDeals.bestTarget(calm.test.contextFor(other, Set.of(), NOW)))
                .isEmpty();
    }

    @Test
    void carthNeedsSomethingToGiveBack() {
        giveHacanTheAgent();
        table.stock(hacan, 0, 0);

        assertThat(CarthDeals.bestTarget(hacanContext())).isEmpty();
    }

    // Replenished to 4, Sol is offered: all 4 of its commodities for 1 of Hacan's, so Sol gains 1 trade good.
    @Test
    void carthOffersTheReplenishedTargetAllItsCommoditiesForOne() {
        giveHacanTheAgent();
        hacan.getLeader("hacanagent").orElseThrow().setExhausted(true);
        CarthDeals.remember(hacanContext(), table.sol);
        table.stock(table.sol, 4, 0);

        assertThat(CarthDeals.followUp(hacanContext()).map(AiTestGame::pressedId))
                .contains("transaction");

        assertThat(draft().partner()).isEqualTo("sol");
        assertThat(draft().purpose()).isEqualTo(Purpose.AGENT_TRADE);
        assertThat(draft().target().total("sol", ItemType.COMMODITIES)).isEqualTo(4);
        assertThat(draft().target().total("hacan", ItemType.COMMODITIES)).isEqualTo(1);
    }

    @Test
    void carthWaitsUntilTheTargetIsReplenished() {
        giveHacanTheAgent();
        hacan.getLeader("hacanagent").orElseThrow().setExhausted(true);
        CarthDeals.remember(hacanContext(), table.sol);

        assertThat(CarthDeals.followUp(hacanContext())).isEmpty();
    }

    // A replenished AI target takes that shape from the exhausted agent's owner, and nothing richer for Hacan.
    @Test
    void anAiTargetAcceptsTheAgentDealOnly() {
        giveHacanTheAgent();
        hacan.getLeader("hacanagent").orElseThrow().setExhausted(true);
        table.stock(table.sol, 4, 0);
        AiTurnContext sol = table.test.contextFor(table.sol, Set.of(), NOW);
        Deal fourForOne =
                deal("sol", "hacan", "sendingsol_receivinghacan_Comms_4", "sendinghacan_receivingsol_Comms_1");
        Deal fourForNothing = deal("sol", "hacan", "sendingsol_receivinghacan_Comms_4");

        assertThat(CarthDeals.accepts(sol, hacan, fourForOne)).isTrue();
        assertThat(CarthDeals.accepts(sol, hacan, fourForNothing)).isFalse();

        table.stock(table.sol, 2, 0);
        assertThat(CarthDeals.accepts(sol, hacan, fourForOne)).isFalse();

        table.stock(table.sol, 4, 0);
        hacan.getLeader("hacanagent").orElseThrow().setExhausted(false);
        assertThat(CarthDeals.accepts(sol, hacan, fourForOne)).isFalse();

        hacan.getLeader("hacanagent").orElseThrow().setExhausted(true);
        table.points(hacan, 8);
        assertThat(CarthDeals.accepts(sol, hacan, fourForOne)).isFalse();
    }

    // Crossing 10 trade goods while Gila the Silvertongue is locked is worth exactly 2.0 more to Hacan than the same
    // wash that stays below 10.
    @Test
    void crossingTenWithGilaLockedIsWorthTwoMore() {
        hacan.addLeader("hacancommander");
        Deal wash =
                deal("nekro", "hacan", "sendingnekro_receivinghacan_Comms_3", "sendinghacan_receivingnekro_Comms_3");

        table.stock(hacan, 6, 0);
        double below = TradeValue.of(table.game, hacan, table.nekro, wash, 1.0).selfGain();
        table.stock(hacan, 6, 8);
        double crossing =
                TradeValue.of(table.game, hacan, table.nekro, wash, 1.0).selfGain();

        assertThat(crossing - below).isCloseTo(2.0, within(EXACT));
    }
    // Hacan's take is what the target sends less what it pays back: value 4 at 4/1 and value 5 at 5/2 give it the same
    // 3, so the target that gains less (Sol, 1 trade good) wins.
    @Test
    void carthBreaksATieByTheSmallerTargetGain() {
        giveHacanTheAgent();
        table.nekro.setCommoditiesBase(5);
        table.stock(table.nekro, 0, 0);

        CarthDeals.Plan plan = CarthDeals.bestPlan(hacanContext()).orElseThrow();

        assertThat(plan.target()).isSameAs(table.sol);
        assertThat(plan.gain()).isEqualTo(1);
        assertThat(plan.received()).isEqualTo(4);
    }

    // Value 6 asks 2 and sends 6, a take of 4, which beats value 4 at 4/1.
    @Test
    void carthPrefersTheBiggerTakeEvenAtTwoTradeGoods() {
        giveHacanTheAgent();
        table.nekro.setCommoditiesBase(6);
        table.stock(table.nekro, 0, 0);

        CarthDeals.Plan plan = CarthDeals.bestPlan(hacanContext()).orElseThrow();

        assertThat(plan.target()).isSameAs(table.nekro);
        assertThat(plan.gain()).isEqualTo(2);
        assertThat(plan.received()).isEqualTo(6);
    }

    // Nekro (value 4) is stingy toward a Hacan on 7 points and wants 2 for its 4, a take of 2; Sol (value 3, level with
    // Hacan) takes 1 for its 3, the same take of 2 for less, so Sol is chosen.
    @Test
    void carthSkipsAStingyCounterWhenAnotherTargetGivesAsMuch() {
        giveHacanTheAgent();
        table.points(hacan, 7);
        table.points(table.sol, 7);
        table.nekro.setCommoditiesBase(4);
        table.stock(table.nekro, 0, 0);
        table.sol.setCommoditiesBase(3);
        table.stock(table.sol, 0, 0);

        assertThat(CarthDeals.requiredGain(table.game, table.nekro, hacan)).isEqualTo(2);
        CarthDeals.Plan plan = CarthDeals.bestPlan(hacanContext()).orElseThrow();

        assertThat(plan.target()).isSameAs(table.sol);
        assertThat(plan.gain()).isEqualTo(1);
        assertThat(plan.received()).isEqualTo(3);
    }

    // 1 trade good is enough up to value 4, 2 are needed from value 5.
    @Test
    void anAiTargetWantsTwoTradeGoodsFromValueFive() {
        table.sol.setCommoditiesBase(4);
        assertThat(CarthDeals.acceptable(table.game, table.sol, hacan, 1)).isTrue();

        table.sol.setCommoditiesBase(5);
        assertThat(CarthDeals.acceptable(table.game, table.sol, hacan, 1)).isFalse();
        assertThat(CarthDeals.acceptable(table.game, table.sol, hacan, 2)).isTrue();
    }

    // A target facing a Hacan that is close to winning only takes an even split: 2 of 4.
    @Test
    void aStingyTargetOnlyAcceptsAnEvenSplit() {
        table.points(hacan, 8);
        table.sol.setCommoditiesBase(4);

        assertThat(CarthDeals.acceptable(table.game, table.sol, hacan, 1)).isFalse();
        assertThat(CarthDeals.acceptable(table.game, table.sol, hacan, 2)).isTrue();
    }

    // Sol, on its own turn, holds 3 commodities: the agent would replenish nothing, so Hacan first offers a 3-for-3
    // wash
    // (Hacan keeps the 1 commodity it needs to give back). Afterwards Sol is a target.
    @Test
    void carthWashesATargetsCommoditiesBeforeTheAgent() {
        giveHacanTheAgent();
        table.stock(hacan, 6, 0);
        table.stock(table.sol, 3, 0);
        table.test.isActive(table.sol, "action");

        assertThat(CarthDeals.bestPlan(hacanContext())).isEmpty();
        assertThat(CarthDeals.preWash(hacanContext()).map(AiTestGame::pressedId))
                .contains("transaction");

        assertThat(draft().partner()).isEqualTo("sol");
        assertThat(draft().purpose()).isEqualTo(Purpose.WASH);
        assertThat(draft().target().total("sol", ItemType.COMMODITIES)).isEqualTo(3);
        assertThat(draft().target().total("hacan", ItemType.COMMODITIES)).isEqualTo(3);

        table.test.isActive(hacan, "action");
        table.stock(table.sol, 0, 3);
        assertThat(CarthDeals.bestTarget(hacanContext())).containsSame(table.sol);
    }

    // A human target gets a deal with steps, not a table-talk ask: Carth replenishes them, they send all 4 commodities
    // and get 1 back. Carth is not spent until they accept.
    @Test
    void carthProposesADealToAHumanInsteadOfSpendingTheAgent() {
        giveHacanTheAgent();
        Player human = table.humanSeat("xxcha", "green");
        human.setCommoditiesBase(4);
        table.stock(human, 0, 0);

        AiDecision decision = CarthDeals.propose(hacanContext(), human).orElseThrow();

        HumanAiDeal deal = ((AiDecision.Propose) decision).deal();
        assertThat(deal.aiFaction()).isEqualTo("hacan");
        assertThat(deal.humanFaction()).isEqualTo("xxcha");
        assertThat(deal.status()).isEqualTo(HumanAiDeal.Status.PENDING);
        assertThat(deal.steps())
                .containsExactly(
                        HumanAiDeal.Step.exhaust(
                                "hacanagent", "xxcha", 4, "uses Carth of Golden Sands to replenish your commodities"),
                        HumanAiDeal.Step.transfer("xxcha", "hacan", HumanAiDeal.Item.COMMODITIES, 4),
                        HumanAiDeal.Step.transfer("hacan", "xxcha", HumanAiDeal.Item.COMMODITIES, 1));
        assertThat(hacan.hasUnexhaustedLeader("hacanagent")).isTrue();
    }

    // With no commodity to spare Hacan sends the trade good instead.
    @Test
    void carthPaysAHumanInTradeGoodsWhenItHasNoCommodity() {
        giveHacanTheAgent();
        table.stock(hacan, 0, 2);
        Player human = table.humanSeat("xxcha", "green");
        human.setCommoditiesBase(4);
        table.stock(human, 0, 0);

        HumanAiDeal deal =
                ((AiDecision.Propose) CarthDeals.propose(hacanContext(), human).orElseThrow()).deal();

        assertThat(deal.steps().getLast())
                .isEqualTo(HumanAiDeal.Step.transfer("hacan", "xxcha", HumanAiDeal.Item.TRADE_GOODS, 1));
    }

    // A denied or expired deal puts the human on the rejection cooldown and frees the AI to look at the next candidate;
    // while one is pending the AI waits.
    @Test
    void aDeniedOrExpiredDealCoolsTheHumanDown() {
        giveHacanTheAgent();
        Player human = table.humanSeat("xxcha", "green");
        human.setCommoditiesBase(4);
        table.stock(human, 0, 0);
        HumanAiDeal deal =
                ((AiDecision.Propose) CarthDeals.propose(hacanContext(), human).orElseThrow()).deal();
        HumanAiDealStore.save(table.game, deal);

        assertThat(HumanDealWatch.pending(hacanContext())).isTrue();
        HumanDealWatch.observe(hacanContext());
        assertThat(OfferRejections.coolingDown(hacanContext(), human)).isFalse();

        HumanAiDealStore.save(table.game, deal.withStatus(HumanAiDeal.Status.DENIED));
        HumanDealWatch.observe(hacanContext());
        assertThat(HumanDealWatch.pending(hacanContext())).isFalse();
        assertThat(OfferRejections.coolingDown(hacanContext(), human)).isTrue();
    }

    @Test
    void anUnansweredDealExpiresIntoARejection() {
        giveHacanTheAgent();
        Player human = table.humanSeat("xxcha", "green");
        human.setCommoditiesBase(4);
        table.stock(human, 0, 0);
        HumanAiDeal deal =
                ((AiDecision.Propose) CarthDeals.propose(hacanContext(), human).orElseThrow()).deal();
        HumanAiDealStore.save(table.game, deal);

        AiTurnContext later = table.test.contextFor(hacan, Set.of(), deal.expiresAt() + 1);
        HumanDealWatch.observe(later);

        assertThat(HumanAiDealStore.latestFor(table.game, "hacan").orElseThrow().status())
                .isEqualTo(HumanAiDeal.Status.EXPIRED);
        assertThat(OfferRejections.coolingDown(later, human)).isTrue();
    }
}
