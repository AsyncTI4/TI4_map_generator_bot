package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.tactical.StyxTake;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.service.map.FractureService;
import ti4.testUtils.BaseTi4Test;

/**
 * When Nekro (black, the AI) may part with its own Support for the Throne. Never when it gives Sol (blue) a path to win
 * on public information; usually not when hidden information (secrets, Styx, Imperial awaiting Mecatol) could, unless
 * Nekro finishes first. Otherwise only in a Throne swap, for a point Nekro could not score any other way, or for at
 * least 10 trade goods of value with no debt. Goal 10, round 4, Letnev is a far-away third seat.
 */
class ThroneSaleTest extends BaseTi4Test {

    private static final int NEKRO_THRONE = 11;
    private static final int SOL_THRONE = 13;
    private static final int IMPERIAL = 8;

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        table.humanSeat("letnev", "red");
        table.game.setRound(4);
        for (int card = 1; card <= IMPERIAL; card++) table.game.setScTradeGood(card, 0);
        nekro.addOwnedPromissoryNoteByID("black_sftt");
        nekro.setPromissoryNote("black_sftt", NEKRO_THRONE);
        sol.addOwnedPromissoryNoteByID("blue_sftt");
        sol.setPromissoryNote("blue_sftt", SOL_THRONE);
        table.stock(sol, 0, 12);
    }

    private static DealItem nekroThrone() {
        return new DealItem("nekro", "sol", ItemType.PROMISSORY, String.valueOf(NEKRO_THRONE));
    }

    private static Deal sale(int tradeGoods) {
        return Deal.EMPTY.with(nekroThrone()).adjust("sol", "nekro", ItemType.TRADE_GOODS, tradeGoods);
    }

    private static Deal swap() {
        return Deal.EMPTY
                .with(nekroThrone())
                .with(new DealItem("sol", "nekro", ItemType.PROMISSORY, NotesForTrade.detailOf("blue_sftt")));
    }

    private boolean allowed(Deal deal) {
        return ThroneSale.allowed(table.game, nekro, sol, deal);
    }

    // A few trade goods do not buy it; 10 trade goods do, but not with any debt in the deal.
    @Test
    void sellsOnlyForTenTradeGoodsAndNeverForDebt() {
        assertThat(allowed(sale(3))).isFalse();
        assertThat(allowed(sale(9))).isFalse();
        assertThat(allowed(sale(10))).isTrue();
        assertThat(allowed(sale(10).adjust("sol", "nekro", ItemType.CLEAR_DEBT, 1)))
                .isFalse();
        assertThat(Stinginess.vetoes(table.game, nekro, sol, sale(3))).contains(Stinginess.Veto.THRONE);
    }

    // Level peers swap. A swap that takes Sol to 10 on public information is never made, not even when Nekro would
    // reach 10 with it too.
    @Test
    void neverGivesAPublicPathToWin() {
        table.points(nekro, 3);
        table.points(sol, 3);
        assertThat(allowed(swap())).isTrue();

        table.points(sol, 6);
        table.points(nekro, 6);
        assertThat(allowed(swap())).isFalse();
        assertThat(allowed(sale(12))).isFalse();
    }

    // The user's case: Sol on 8 holds Imperial without Mecatol. With the Throne it is on 9, takes Mecatol and scores
    // Imperial in the action phase, before Nekro (8, then 9) can score Negotiate Trade Routes at status: no swap.
    // Nekro on 9 reaches 10 the moment the swap resolves, before anyone, and swaps.
    // This is the old win model, where Imperial without Mecatol always reads as a point. WIN_THREAT_FEASIBLE counts it
    // only when Sol can land on Mecatol Rex this round, which it cannot here (see the flag-on test below).
    @Test
    void usuallyKeepsItFromAHiddenPathToWinUnlessItFinishesFirst() {
        AiExperiments.disable(nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));
        sol.addSC(IMPERIAL);
        table.points(sol, 8);
        table.points(nekro, 8);
        table.test.nekroHome();
        table.reveal("trade_routes");
        table.stock(nekro, 0, 5);
        table.stock(sol, 0, 0);
        assertThat(ThroneSale.publicPathToWin(table.game, sol)).isFalse();
        assertThat(ThroneSale.hiddenPathToWin(table.game, nekro, sol)).isTrue();
        assertThat(allowed(swap())).isFalse();

        table.points(nekro, 1);
        assertThat(allowed(swap())).isTrue();
    }

    // WIN_THREAT_FEASIBLE: the same Sol, with Imperial but no ships to reach Mecatol Rex, has no feasible way to the
    // goal
    // even holding Nekro's Throne, so the hidden path is gone and Nekro may swap.
    @Test
    void imperialWithNoWayOntoMecatolIsNoHiddenPath() {
        sol.addSC(IMPERIAL);
        table.points(sol, 8);
        table.points(nekro, 8);
        table.test.nekroHome();
        table.reveal("trade_routes");
        table.stock(nekro, 0, 5);
        table.stock(sol, 0, 0);

        assertThat(ThroneSale.hiddenPathToWin(table.game, nekro, sol)).isFalse();
        assertThat(allowed(swap())).isTrue();
    }

    // Styx is a legendary planet; its card A Song Like Marrow is worth 1 point while held
    // (docs/rules/thunders-edge.md).
    // Sol on 8 has a space dock on an ingress system but no fleet that can take Styx. The old model read the dock alone
    // as a Styx point, so
    // 8 + the Throne + Styx was a hidden path to 10 that Nekro could not beat, and it refused even 12 trade goods.
    private void solOnEightWithADockButNoFleet() {
        table.points(sol, 8);
        FractureService.spawnFracture(null, table.game);
        Tile dock = table.test.place("26", TradeTable.FAR_SPOT);
        dock.addToken(Constants.TOKEN_INGRESS, "space");
        sol.addPlanet("lodor");
        table.test.units(dock, "lodor", sol, UnitType.Spacedock, 1);
        table.changed();
        assertThat(FractureCampaign.dock(table.game, sol)).isPresent();
    }

    @Test
    void withTheExperimentDisabledADockAloneIsAStyxPointThatBlocksTheSale() {
        solOnEightWithADockButNoFleet();
        AiExperiments.disable(nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));

        assertThat(ThroneSale.hiddenPathToWin(table.game, nekro, sol)).isTrue();
        assertThat(allowed(sale(12))).isFalse();
    }

    // With WIN_THREAT_FEASIBLE Styx counts only at the chance Sol can actually take it this round: none, so Sol has no
    // path to 10 and the 12 trade goods buy the Throne.
    @Test
    void aDockWithoutAFleetIsNoStyxPoint() {
        solOnEightWithADockButNoFleet();

        assertThat(StyxTake.thisRound(table.game, sol)).isZero();
        assertThat(ThroneSale.hiddenPathToWin(table.game, nekro, sol)).isFalse();
        assertThat(allowed(sale(12))).isTrue();
    }

    // Two short of Negotiate Trade Routes, its only public, with no commodities: Nekro sells for the 2 trade goods it
    // is missing, but not to a Sol whom the point would take to 10.
    @Test
    void sellsForAPointItCouldNotOtherwiseScore() {
        table.test.nekroHome();
        table.reveal("trade_routes");
        table.stock(nekro, 0, 3);
        assertThat(allowed(sale(2))).isTrue();

        table.points(sol, 9);
        assertThat(allowed(sale(2))).isFalse();
    }

    // Giving another note is not a sale of its Support for the Throne.
    @Test
    void otherNotesAreNotGated() {
        Deal otherNote = Deal.EMPTY.with(new DealItem("nekro", "sol", ItemType.PROMISSORY, "5"));
        assertThat(allowed(otherNote)).isTrue();
    }
}
