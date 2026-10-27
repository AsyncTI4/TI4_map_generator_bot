package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.ExtortionPlans;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * Extortion between two AI seats: Nekro (black) threatens an attack on a system of Sol's (blue) unless Sol pays. Sol
 * judges the demand against what the attack would cost it; Nekro spares the system for the round if paid and commits
 * to the attack if refused. Goal 10, round 3, action phase.
 */
class ExtortionTest extends BaseTi4Test {

    private static final String TARGET = "305";

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        table.game.setRound(3);
        table.stock(sol, 0, 6);
        table.nekroAndSolNeighbour();
    }

    private static Deal tribute(int tradeGoods) {
        return Deal.EMPTY.adjust("sol", "nekro", ItemType.TRADE_GOODS, tradeGoods);
    }

    private void threaten(double attackValue, double loss, int pointsAtRisk) {
        Extortion.record(table.game, nekro, sol, new Extortion.Threat(TARGET, attackValue, loss, pointsAtRisk));
    }

    private boolean solPays(Deal deal) {
        return TradeValue.acceptable(table.game, sol, nekro, deal, 1.0);
    }

    // Paying 3 trade goods for nothing is refused; with an attack on the table that would cost Sol 8, it pays.
    @Test
    void theVictimPaysWhenTheAttackCostsMore() {
        assertThat(solPays(tribute(3))).isFalse();

        threaten(3, 8, 0);

        assertThat(solPays(tribute(3))).isTrue();
        assertThat(Extortion.relief(table.game, sol, nekro, tribute(3))).isCloseTo(8, within(1e-9));
    }

    // Sol holds Lodor (one cruiser in space, 2 infantry and a mech on the planet, 6 trade goods). Its loss is the
    // planet,
    // half the cruiser, and every ground unit: 2 x 0.5 + 2 for the mech.
    private Tile lodor() {
        Tile tile = table.test.place("26", TradeTable.BESIDE_NEKRO);
        sol.addPlanet("lodor");
        table.test.units(tile, "space", sol, UnitType.Cruiser, 1);
        table.test.units(tile, "lodor", sol, UnitType.Infantry, 2);
        table.test.units(tile, "lodor", sol, UnitType.Mech, 1);
        return tile;
    }

    @Test
    void theLossCountsTheGroundForcesOnItsPlanets() {
        Tile tile = lodor();
        assertThat(Extortion.groundLoss(tile, sol)).isCloseTo(2 * 0.5 + 2, within(1e-9));
        assertThat(Extortion.loss(table.game, sol, tile, 0)).isGreaterThan(Extortion.groundLoss(tile, sol));
    }

    // Attacking Lodor scores 5 and the next best move 3: extorting pays only when the demand beats the 2 it gives up.
    // When the attack is worth far more than anything else (8 over the next best), it attacks without asking.
    @Test
    void extortsOnlyWhenTheDemandBeatsJustAttacking() {
        Tile tile = lodor();
        var demand = Extortion.demandOf(table.game, nekro, sol, tile, 5 - 3);
        assertThat(demand).isPresent();
        assertThat(demand.get().deal().total("sol", ItemType.TRADE_GOODS)).isGreaterThan(2);

        assertThat(Extortion.demandOf(table.game, nekro, sol, tile, 8)).isEmpty();
    }

    // A demand is worth 1 to 5 trade goods, however much the attack would cost: Sol with 12 trade goods is asked for 5
    // at most. Without trade goods, a relic fragment does instead.
    @Test
    void asksForOneToFiveTradeGoodsOrAFragment() {
        Tile tile = lodor();
        table.stock(sol, 0, 12);
        Extortion.Demand rich =
                Extortion.demandOf(table.game, nekro, sol, tile, 0).orElseThrow();
        assertThat(rich.deal().total("sol", ItemType.TRADE_GOODS)).isBetween(1, Extortion.MAX_DEMAND);

        table.stock(sol, 0, 0);
        sol.addFragment("crf1");
        Extortion.Demand fragment =
                Extortion.demandOf(table.game, nekro, sol, tile, 0).orElseThrow();
        assertThat(fragment.deal().items().getFirst().type()).isEqualTo(ItemType.FRAGMENTS);
    }

    // Taking Sol's home stops it scoring public objectives, so Nekro asks for its Support for the Throne; a demand on a
    // system whose loss costs no point never asks for it.
    @Test
    void asksForTheThroneOnlyWhenTheAttackStopsScoring() {
        sol.addOwnedPromissoryNoteByID("blue_sftt");
        sol.setPromissoryNote("blue_sftt", 13);
        Tile tile = lodor();
        assertThat(Extortion.demandOf(table.game, nekro, sol, tile, 0)
                        .orElseThrow()
                        .deal()
                        .notes("sol"))
                .noneMatch(detail -> detail.contains("sftt"));

        table.solHome();
        Tile home = table.game.getTileByPosition(TradeTable.SOL_SPOT);
        table.test.units(home, "jord", sol, UnitType.Infantry, 2);
        readyImperialPoint();
        Extortion.Demand demand =
                Extortion.demandOf(table.game, nekro, sol, home, 0).orElseThrow();
        assertThat(demand.deal().notes("sol")).containsExactly(NotesForTrade.detailOf("blue_sftt"));
    }

    // When Nekro needs the attack to score, it does not trade the attack for goods: only Sol's Support for the Throne
    // (worth a point itself) is still worth asking for.
    @Test
    void aScoringAttackIsOnlyTradedForTheThrone() {
        Tile tile = lodor();
        assertThat(Extortion.demandOf(table.game, nekro, sol, tile, 0, true)).isEmpty();

        sol.addOwnedPromissoryNoteByID("blue_sftt");
        sol.setPromissoryNote("blue_sftt", 13);
        table.solHome();
        Tile home = table.game.getTileByPosition(TradeTable.SOL_SPOT);
        table.test.units(home, "jord", sol, UnitType.Infantry, 2);
        readyImperialPoint();
        assertThat(Extortion.demandOf(table.game, nekro, sol, home, 0, true)
                        .orElseThrow()
                        .deal()
                        .notes("sol"))
                .containsExactly(NotesForTrade.detailOf("blue_sftt"));
    }

    // Sol on 9, holding Imperial with Mecatol to take, can win this round. The only demand worth making is one that
    // lets Nekro win first. On 5 nothing Sol could pay gets Nekro there first. On 9 Sol's Support for the Throne would,
    // but no player hands over the point that wins the game, so it is never asked for either. Sol is human here.
    @Test
    void againstAPlayerWhoCanWinOnlyADemandThatWinsFirst() {
        table = TradeTable.withHumanSol();
        nekro = table.nekro;
        sol = table.sol;
        table.game.setRound(3);
        table.stock(sol, 0, 6);
        table.nekroAndSolNeighbour();
        Tile tile = lodor();
        for (int card = 1; card <= 8; card++) table.game.setScTradeGood(card, 0);
        sol.addSC(8);
        table.points(sol, 9);
        sol.addOwnedPromissoryNoteByID("blue_sftt");
        sol.setPromissoryNote("blue_sftt", 13);
        table.points(nekro, 5);
        assertThat(ti4.ai.scoring.WinRace.canWinThisRound(table.game, nekro, sol))
                .isTrue();
        assertThat(Extortion.winningDemand(table.game, nekro, sol, tile)).isEmpty();

        table.points(nekro, 4);
        assertThat(Extortion.winningDemand(table.game, nekro, sol, tile)).isEmpty();
        assertThat(Extortion.demandOf(table.game, nekro, sol, table.game.getTileByPosition(TradeTable.BESIDE_NEKRO), 0))
                .map(demand -> demand.deal().notes("sol"))
                .hasValueSatisfying(notes -> assertThat(notes).doesNotContain(NotesForTrade.detailOf("blue_sftt")));
    }

    // The same position against an AI Sol: it would never hand Nekro the winning point, so no demand is made.
    @Test
    void anAiSeatAboutToWinIsNotAskedForTheWinningPoint() {
        Tile tile = lodor();
        for (int card = 1; card <= 8; card++) table.game.setScTradeGood(card, 0);
        sol.addSC(8);
        table.points(sol, 9);
        sol.addOwnedPromissoryNoteByID("blue_sftt");
        sol.setPromissoryNote("blue_sftt", 13);
        table.points(nekro, 9);

        assertThat(Extortion.winningDemand(table.game, nekro, sol, tile)).isEmpty();
    }

    // Sol holds Imperial and Mecatol Rex: a point ready to score, so losing its home would cost it that point.
    private void readyImperialPoint() {
        for (int card = 1; card <= 8; card++) table.game.setScTradeGood(card, 0);
        sol.addSC(8);
        sol.addPlanet("mr");
    }

    // The demand is priced at what an AI victim would still pay: the most that leaves Sol better off than the attack.
    @Test
    void theDemandIsOneTheVictimPays() {
        Tile tile = lodor();
        Extortion.Demand demand =
                Extortion.demandOf(table.game, nekro, sol, tile, 0).orElseThrow();
        Extortion.record(table.game, nekro, sol, demand.threat());

        assertThat(solPays(demand.deal())).isTrue();
        int price = demand.deal().total("sol", ItemType.TRADE_GOODS);
        if (price < Extortion.MAX_DEMAND && price < sol.getTg()) {
            assertThat(solPays(tribute(price + 1))).isFalse();
        }
    }

    // Each demand Sol paid makes the next one against Sol cost 2 more to justify, across rounds: after two payments the
    // attack-versus-next-move gap of 2 needs a demand above 6, more than it may ask.
    @Test
    void eachPaidDemandMakesTheNextHarderToJustify() {
        Tile tile = lodor();
        threaten(3, 8, 0);
        Extortion.accepted(table.context(), sol);
        table.game.setRound(4);
        threaten(3, 8, 0);
        Extortion.accepted(table.context(), sol);

        assertThat(Extortion.timesPaid(nekro, sol)).isEqualTo(2);
        assertThat(Extortion.repeatPenalty(nekro, sol)).isCloseTo(4, within(1e-9));
        assertThat(Extortion.demandOf(table.game, nekro, sol, tile, 2 + Extortion.repeatPenalty(nekro, sol)))
                .isEmpty();
    }

    // The threat counts only toward the very thing demanded, and only until the demand is settled: a later request for
    // 3 trade goods from the same seat that round (a Technology fee, say) is judged on its own.
    @Test
    void reliefOnlyForTheDemandItselfWhileItIsOpen() {
        Extortion.record(
                table.game,
                nekro,
                sol,
                new Extortion.Threat("305", 2, 8, 0)
                        .asking(new DealItem("sol", "nekro", ItemType.TRADE_GOODS, "2"), sol.getTg()));
        assertThat(Extortion.relief(table.game, sol, nekro, tribute(2))).isCloseTo(8, within(1e-9));
        assertThat(Extortion.relief(table.game, sol, nekro, tribute(3))).isZero();

        Extortion.settle(table.context(), sol);
        assertThat(Extortion.relief(table.game, sol, nekro, tribute(2))).isZero();
    }

    // A note is demanded by name but the offer the builder sends names it by its hand number: the victim still
    // recognises it as the demand. (Self-play found every note demand refused before this matched.)
    @Test
    void aNoteDemandIsRecognisedByItsHandNumber() {
        sol.addOwnedPromissoryNoteByID("blue_ta");
        sol.setPromissoryNote("blue_ta", 21);
        Extortion.record(
                table.game,
                nekro,
                sol,
                new Extortion.Threat("305", 2, 8, 0)
                        .asking(
                                new DealItem("sol", "nekro", ItemType.PROMISSORY, NotesForTrade.detailOf("blue_ta")),
                                1));
        Deal sent = Deal.EMPTY.with(new DealItem("sol", "nekro", ItemType.PROMISSORY, "21"));

        assertThat(Extortion.isDemand(table.game, sol, nekro, sent)).isTrue();
        assertThat(Extortion.relief(table.game, sol, nekro, sent)).isCloseTo(8, within(1e-9));
    }

    // "Paid" is checked against the board: an emptied transaction (deleted or reset) without the trade goods actually
    // leaving Sol counts as a refusal, so nothing is spared.
    @Test
    void anEmptiedTransactionWithoutPaymentIsARefusal() {
        Extortion.record(
                table.game,
                nekro,
                sol,
                new Extortion.Threat("305", 2, 8, 0)
                        .asking(new DealItem("sol", "nekro", ItemType.TRADE_GOODS, "2"), sol.getTg()));

        Extortion.accepted(table.context(), sol);

        assertThat(ExtortionPlans.spared(table.game, nekro, "305")).isFalse();
        assertThat(ExtortionPlans.credibility(table.game, nekro, "305")).isCloseTo(2, within(1e-9));
        assertThat(Extortion.timesPaid(nekro, sol)).isZero();
    }

    // A victim's counter-offer of 1 trade good instead of 2 settles the demand: Nekro takes it and spares the system,
    // rather than pocketing it and attacking anyway.
    @Test
    void aCounterOfferSettlesTheDemand() {
        Extortion.record(
                table.game,
                nekro,
                sol,
                new Extortion.Threat("305", 2, 8, 0)
                        .asking(new DealItem("sol", "nekro", ItemType.TRADE_GOODS, "2"), sol.getTg()));

        assertThat(Extortion.isCounter(table.game, nekro, sol, tribute(1))).isTrue();
        Extortion.settle(table.context(), sol);
        assertThat(ExtortionPlans.spared(table.game, nekro, "305")).isTrue();
        assertThat(Extortion.isCounter(table.game, nekro, sol, tribute(1))).isFalse();
    }

    // With nothing ready to score, Sol loses no point when its home is attacked: it keeps its Support for the Throne.
    @Test
    void aHomeWithNothingReadyPutsNoPointAtRisk() {
        table.solHome();
        Tile home = table.game.getTileByPosition(TradeTable.SOL_SPOT);
        assertThat(Extortion.pointsAtRisk(table.game, sol, home)).isZero();
    }

    // The threat only counts toward a pure tribute, and only this round.
    @Test
    void reliefOnlyForTributeThisRound() {
        threaten(3, 8, 0);
        Deal exchange = tribute(3).adjust("nekro", "sol", ItemType.COMMODITIES, 1);
        assertThat(Extortion.relief(table.game, sol, nekro, exchange)).isZero();

        table.game.setRound(4);
        assertThat(Extortion.relief(table.game, sol, nekro, tribute(3))).isZero();
    }

    // Paid: the target is spared for the rest of the round. Refused: the attack is committed, worth the demand again.
    @Test
    void acceptedSparesTheTargetAndRefusedCommitsToIt() {
        threaten(3.5, 8, 0);
        Extortion.accepted(table.context(), sol);
        assertThat(ExtortionPlans.spared(table.game, nekro, TARGET)).isTrue();
        assertThat(ExtortionPlans.credibility(table.game, nekro, TARGET)).isZero();

        TradeTable other = TradeTable.withAiSol();
        other.game.setRound(3);
        Extortion.record(other.game, other.nekro, other.sol, new Extortion.Threat(TARGET, 3.5, 8, 0));
        Extortion.refused(other.context(), other.sol);
        assertThat(ExtortionPlans.spared(other.game, other.nekro, TARGET)).isFalse();
        assertThat(ExtortionPlans.credibility(other.game, other.nekro, TARGET)).isCloseTo(3.5, within(1e-9));

        other.game.setRound(4);
        assertThat(ExtortionPlans.credibility(other.game, other.nekro, TARGET)).isZero();
    }

    // The plan travels as a "details" line in the transaction, which is read back as a details item worth nothing.
    @Test
    void theThreatIsWrittenIntoTheTransactionDetails() {
        threaten(3, 8, 0);
        Extortion.addDetails(table.game, nekro, sol);

        Deal sent = Deal.between(nekro, sol);
        assertThat(sent.items()).hasSize(1);
        DealItem details = sent.items().getFirst();
        assertThat(details.type()).isEqualTo(ItemType.DETAILS);
        assertThat(details.detail()).contains("attack");
        assertThat(sent.hasUnsupported()).isFalse();
        assertThat(ComponentValues.receive(table.game, sol, nekro, details, 1.0))
                .isZero();
    }

    // Sol gives its Support for the Throne to stop an attack that would cost it a point, unless it gives Nekro a
    // public path to win.
    @Test
    void handsOverItsThroneToKeepAPoint() {
        sol.addOwnedPromissoryNoteByID("blue_sftt");
        sol.setPromissoryNote("blue_sftt", 13);
        Deal throne = Deal.EMPTY.with(new DealItem("sol", "nekro", ItemType.PROMISSORY, "13"));

        threaten(3, 8, 0);
        assertThat(ThroneSale.allowed(table.game, sol, nekro, throne)).isFalse();

        threaten(3, 8, 1);
        assertThat(ThroneSale.allowed(table.game, sol, nekro, throne)).isTrue();

        table.points(nekro, 9);
        assertThat(ThroneSale.allowed(table.game, sol, nekro, throne)).isFalse();
    }
}
