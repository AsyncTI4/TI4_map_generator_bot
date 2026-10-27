package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promissory.NoteGiving;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * Military Support is Sol's promissory note: at the start of Sol's turn its holder puts 2 infantry on a planet it
 * controls, and Sol loses a strategy token if it has one. Sol sells it for 1 trade good, but only with no strategy
 * tokens; the buyer is a seat with trade goods that wants the infantry. Nekro (black, the AI) has a carrier at home
 * and no infantry to load it, so it wants infantry; Sol (blue, an AI) holds its own note as hand number 7.
 */
class MilitarySupportTradeTest extends BaseTi4Test {

    private static final int NOTE_ID = 7;

    private TradeTable table;
    private Player nekro;
    private Player sol;
    private Tile nekroHome;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        nekroHome = table.test.nekroHome();
        table.test.units(nekroHome, "space", nekro, UnitType.Carrier, 1);
        table.nekroAndSolNeighbour();
        sol.addOwnedPromissoryNoteByID("ms");
        sol.setPromissoryNote("ms", NOTE_ID);
        sol.setStrategicCC(0);
        sol.setTacticalCC(3);
        nekro.addSC(1);
        sol.addSC(2);
        table.stock(nekro, 0, 3);
        table.stock(sol, 0, 0);
    }

    // No carrier to move infantry, and its 4 spare infantry already at home: Nekro has no use for more.
    private void withoutCarrier() {
        nekroHome.removeUnit("space", Units.getUnitKey(UnitType.Carrier, nekro.getColor()), 1);
        table.test.units(nekroHome, "mordaiii", nekro, UnitType.Infantry, 6);
    }

    private AiTurnContext contextFor(Player seat) {
        return table.test.contextFor(
                seat, Set.of(), AiTestGame.NOW, TradeButtons.entry("various-" + seat.getFaction(), AiTestGame.NOW));
    }

    private static Deal sale(String note) {
        return Deal.EMPTY
                .with(new DealItem("sol", "nekro", ItemType.PROMISSORY, note))
                .adjust("nekro", "sol", ItemType.TRADE_GOODS, 1);
    }

    private Deal started(Player seat) {
        return Draft.read(table.test.memoryOf(seat)).orElseThrow().target();
    }

    // ---- what the note is worth ----

    @Test
    void isWorthMoreThanAResourceToASeatThatNeedsInfantry() {
        assertThat(MilitarySupport.receivedBy(table.game, nekro)).isGreaterThan(MilitarySupport.PRICE + 0.5);
    }

    @Test
    void isWorthLessThanTheTradeGoodToASeatWithNoUseForInfantry() {
        withoutCarrier();

        assertThat(MilitarySupport.receivedBy(table.game, nekro)).isLessThan(MilitarySupport.PRICE);
    }

    // ---- Sol's side ----

    @Test
    void solNeverLendsItWhileItHoldsAStrategyToken() {
        sol.setStrategicCC(1);

        assertThat(NotesForTrade.giveCost(table.game, sol, nekro, "ms")).isInfinite();
        assertThat(TradeValue.acceptable(table.game, sol, nekro, sale(String.valueOf(NOTE_ID)), 1.0))
                .isFalse();
    }

    @Test
    void solLendsItForNothingWhenItHasNoStrategyTokens() {
        assertThat(NotesForTrade.giveCost(table.game, sol, nekro, "ms")).isLessThan(0.5);
        assertThat(TradeValue.acceptable(table.game, sol, nekro, sale(String.valueOf(NOTE_ID)), 1.0))
                .isTrue();
    }

    @Test
    void solDoesNotHandItOverAsAnyNoteWhileItHoldsATokenEither() {
        sol.setStrategicCC(2);
        sol.addOwnedPromissoryNoteByID("blue_ta");
        sol.setPromissoryNote("blue_ta", 8);

        assertThat(NoteGiving.leastHarmful(table.game, sol, nekro, true)).contains("blue_ta");
        assertThat(NotesForTrade.leastHarmful(table.game, sol, nekro)).contains("blue_ta");
    }

    @Test
    void solSellsItForOneTradeGoodAtTheStartOfItsOwnTurnWhenItHasNoTokens() {
        table.test.isActive(sol, "action");

        Optional<AiDecision> start = MilitarySupportDeals.sell(contextFor(sol), List.of(nekro));

        assertThat(start.map(AiTestGame::pressedId)).contains("transaction");
        assertThat(started(sol).notes("sol")).containsExactly(String.valueOf(NOTE_ID));
        assertThat(started(sol).total("nekro", ItemType.TRADE_GOODS)).isEqualTo(1);
    }

    @Test
    void solKeepsItWhileItHoldsAStrategyToken() {
        table.test.isActive(sol, "action");
        sol.setStrategicCC(1);

        assertThat(MilitarySupportDeals.sell(contextFor(sol), List.of(nekro))).isEmpty();
    }

    // Sol may sell it on a turn spent on a component or strategic action, or on the turn it passes: having no tactic
    // token left for another action does not stop the sale.
    @Test
    void solSellsItEvenWithNoTokenLeftForAnotherAction() {
        table.test.isActive(sol, "action");
        sol.setTacticalCC(0);

        assertThat(MilitarySupportDeals.sell(contextFor(sol), List.of(nekro)).map(AiTestGame::pressedId))
                .contains("transaction");
    }

    // A buyer ahead of the table pays for its lead: half a trade good per point ahead of the others' average, on top of
    // the usual rivalry discount. Three points ahead the note costs Sol more than the trade good it earns.
    @Test
    void solIsWarierOfABuyerWithALead() {
        table.test.isActive(sol, "action");
        double level = MilitarySupport.givenCost(table.game, sol, nekro);

        table.points(nekro, 1);
        assertThat(MilitarySupport.givenCost(table.game, sol, nekro)).isGreaterThan(level);

        TradeTable leading = TradeTable.withAiSol();
        leading.points(leading.nekro, 3);
        assertThat(MilitarySupport.givenCost(leading.game, leading.sol, leading.nekro))
                .isGreaterThan(MilitarySupport.PRICE);
    }

    // Nekro on 9 with Imperial and Mecatol Rex has a public path to 10: Sol never sells it Military Support.
    @Test
    void solNeverSellsToABuyerWithAPublicPathToWin() {
        table.test.isActive(sol, "action");
        for (int card = 1; card <= 8; card++) table.game.setScTradeGood(card, 0);
        nekro.addSC(8);
        nekro.addPlanet("mr");
        table.points(nekro, 9);

        assertThat(MilitarySupport.publicPathToWin(table.game, nekro)).isTrue();
        assertThat(MilitarySupport.givenCost(table.game, sol, nekro)).isInfinite();
        assertThat(MilitarySupportDeals.sell(contextFor(sol), List.of(nekro))).isEmpty();
    }

    @Test
    void solOffersItOnlyToASeatThatWantsInfantryAndCanPay() {
        table.test.isActive(sol, "action");
        table.stock(nekro, 0, 0);
        assertThat(MilitarySupportDeals.sell(contextFor(sol), List.of(nekro))).isEmpty();

        table.stock(nekro, 0, 3);
        withoutCarrier();
        assertThat(MilitarySupportDeals.sell(contextFor(sol), List.of(nekro))).isEmpty();
    }

    // ---- a buyer's side ----

    @Test
    void aBuyerWhoseTurnPrecedesSolsAsksToBuyItForOneTradeGood() {
        Optional<AiDecision> start = MilitarySupportDeals.buy(contextFor(nekro), List.of(sol));

        assertThat(start.map(AiTestGame::pressedId)).contains("transaction");
        assertThat(started(nekro).notes("sol")).containsExactly("ms");
        assertThat(started(nekro).total("nekro", ItemType.TRADE_GOODS)).isEqualTo(1);
    }

    @Test
    void theBuyerIsReachedFromItsOwnTurnStart() {
        Optional<AiDecision> start = OwnTurnDeals.start(contextFor(nekro));

        assertThat(start.map(AiTestGame::pressedId)).contains("transaction");
        assertThat(started(nekro).notes("sol")).containsExactly("ms");
    }

    @Test
    void aBuyerWaitsUntilSolIsNextToAct() {
        Player hacan = table.aiSeat("hacan", "red");
        nekro.clearSCs();
        sol.clearSCs();
        nekro.addSC(1);
        hacan.addSC(2);
        sol.addSC(3);

        assertThat(MilitarySupportDeals.buy(contextFor(nekro), List.of(sol))).isEmpty();
    }

    @Test
    void aBuyerNeverBuysWhileSolStillHoldsAStrategyToken() {
        sol.setStrategicCC(1);

        assertThat(MilitarySupportDeals.buy(contextFor(nekro), List.of(sol))).isEmpty();
    }

    @Test
    void aBuyerWithoutTradeGoodsToSpareDoesNotBuy() {
        table.stock(nekro, 0, 0);

        assertThat(MilitarySupportDeals.buy(contextFor(nekro), List.of(sol))).isEmpty();
    }

    @Test
    void aBuyerWithNoUseForInfantryDoesNotBuy() {
        withoutCarrier();

        assertThat(MilitarySupportDeals.buy(contextFor(nekro), List.of(sol))).isEmpty();
    }

    @Test
    void aBuyerAcceptsSolsOfferOfItForOneTradeGood() {
        assertThat(TradeValue.acceptable(table.game, nekro, sol, sale("ms"), 1.0))
                .isTrue();
    }

    @Test
    void aBuyerWithNoUseForInfantryRefusesTheSameOffer() {
        withoutCarrier();

        assertThat(TradeValue.acceptable(table.game, nekro, sol, sale("ms"), 1.0))
                .isFalse();
    }
}
