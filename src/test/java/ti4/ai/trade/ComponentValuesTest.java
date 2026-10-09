package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ComponentValuesTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final int TRADE = 5;

    private TradeTable table;
    private Player nekro;
    private Player sol;
    private Player letnev;

    // Nekro (black, 3 commodities) values what it gets from and gives to Sol (blue, 4 commodities); Letnev (red,
    // 2 commodities) is the third player. Each seat owns its five colour notes; Sol also owns Military Support.
    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        nekro = table.nekro;
        sol = table.sol;
        letnev = table.humanSeat("letnev", "red");
        for (String suffix : new String[] {"_sftt", "_ta", "_cf", "_ps", "_an"}) {
            nekro.addOwnedPromissoryNoteByID("black" + suffix);
            sol.addOwnedPromissoryNoteByID("blue" + suffix);
            letnev.addOwnedPromissoryNoteByID("red" + suffix);
        }
        nekro.addOwnedPromissoryNoteByID("antivirus");
        sol.addOwnedPromissoryNoteByID("ms");
        letnev.addOwnedPromissoryNoteByID("war_funding");
        table.presence(nekro, TradeTable.NEKRO_SPOT);
        table.presence(sol, TradeTable.SOL_SPOT);
        table.presence(letnev, TradeTable.FAR_SPOT);
    }

    private static DealItem item(String raw) {
        return DealItem.parse(raw, "nekro", "sol").orElseThrow();
    }

    private double receiveValue(String alias) {
        return NotesForTrade.receiveValue(table.game, nekro, alias);
    }

    private double giveCost(String alias, boolean desperate) {
        return NotesForTrade.giveCost(table.game, nekro, sol, alias, desperate);
    }

    // Trade goods are worth 1 either way, and a commodity received becomes a trade good.
    @Test
    void tradeGoodsAndReceivedCommoditiesAreWorthOne() {
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_TGs_2"), 0.8))
                .isCloseTo(2, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Comms_3"), 0.8))
                .isCloseTo(3, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_TGs_2"), 0.8, false))
                .isCloseTo(2, within(EXACT));
    }

    // An own commodity is only worth something traded: 0.6 while another player it may transact with has
    // something to wash with, 0.3 otherwise. The partner of the deal being judged does not count as that outlet.
    @Test
    void ownCommodityDependsOnAnotherOutlet() {
        table.presence(sol, TradeTable.BESIDE_NEKRO);
        table.stock(sol, 4, 0);
        table.stock(letnev, 2, 0);
        assertThat(ComponentValues.ownCommodity(table.game, nekro, sol)).isCloseTo(0.3, within(EXACT));

        table.presence(letnev, TradeTable.BESIDE_NEKRO);
        assertThat(ComponentValues.ownCommodity(table.game, nekro, sol)).isCloseTo(0.6, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Comms_3"), 0.8, false))
                .isCloseTo(1.8, within(EXACT));
        assertThat(ComponentValues.ownCommodity(table.game, nekro, letnev)).isCloseTo(0.6, within(EXACT));

        table.stock(sol, 0, 0);
        assertThat(ComponentValues.ownCommodity(table.game, nekro, letnev)).isCloseTo(0.3, within(EXACT));
        table.stock(letnev, 0, 0);
        assertThat(ComponentValues.ownCommodity(table.game, nekro, null)).isCloseTo(0.3, within(EXACT));
    }

    // The outlet is judged as in the action phase, even in the agenda phase when everyone may transact.
    @Test
    void outletIsJudgedAsInTheActionPhase() {
        table.stock(letnev, 2, 0);
        table.game.setPhaseOfGame("agenda");

        assertThat(ComponentValues.ownCommodity(table.game, nekro, sol)).isCloseTo(0.3, within(EXACT));
    }

    // Debt owed to the AI is worth its face value times trust, times how soon it can be collected (0.9 if they can
    // transact now or it is the agenda phase, 0.75 once agenda phases are coming, 0.5 otherwise), halved near the end.
    @Test
    void debtReceivableIsDiscounted() {
        assertThat(ComponentValues.debtReceivable(table.game, nekro, sol, 2, 0.8))
                .isCloseTo(0.8, within(EXACT));

        table.custodiansTaken(letnev);
        assertThat(ComponentValues.debtReceivable(table.game, nekro, sol, 2, 0.8))
                .isCloseTo(1.2, within(EXACT));

        table.presence(sol, TradeTable.BESIDE_NEKRO);
        assertThat(ComponentValues.debtReceivable(table.game, nekro, sol, 2, 0.8))
                .isCloseTo(1.44, within(EXACT));
        assertThat(ComponentValues.debtReceivable(table.game, nekro, sol, 2, 0.5))
                .isCloseTo(0.9, within(EXACT));

        table.points(letnev, 8);
        assertThat(ComponentValues.debtReceivable(table.game, nekro, sol, 2, 0.8))
                .isCloseTo(0.72, within(EXACT));
    }

    @Test
    void agendaPhaseReachesEveryDebtor() {
        table.game.setPhaseOfGame("agenda");

        assertThat(ComponentValues.debtReceivable(table.game, nekro, sol, 1, 1.0))
                .isCloseTo(0.9, within(EXACT));
    }

    // Moving debt: Sol owing more is a receivable; the AI owing more is a liability of 0.8 each; clearing the AI's
    // debt is worth the same 0.8; clearing Sol's debt gives up the receivable.
    @Test
    void debtItems() {
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_SendDebt_2"), 0.8))
                .isCloseTo(0.8, within(EXACT));
        assertThat(ComponentValues.give(
                        table.game, nekro, sol, item("sendingnekro_receivingsol_SendDebt_2"), 0.8, false))
                .isCloseTo(1.6, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_ClearDebt_2"), 0.8))
                .isCloseTo(1.6, within(EXACT));
        assertThat(ComponentValues.give(
                        table.game, nekro, sol, item("sendingnekro_receivingsol_ClearDebt_2"), 0.8, false))
                .isCloseTo(0.8, within(EXACT));
    }

    // A relic fragment is worth 1, and 2 for the one that makes three of a kind (unknown fragments count as any
    // kind).
    @Test
    void theFragmentThatCompletesASetIsWorthMore() {
        nekro.setCrf(1);
        nekro.setUrf(1);
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_CRF1"), 0.8))
                .isCloseTo(2, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_IRF1"), 0.8))
                .isCloseTo(1, within(EXACT));

        nekro.setCrf(0);
        nekro.setUrf(0);
        nekro.setHrf(2);
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_URF1"), 0.8))
                .isCloseTo(2, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_CRF3"), 0.8))
                .isCloseTo(4, within(EXACT));
    }

    // Destroy Heretical Works scores for purging 2 fragments: while the AI holds it with fewer than 2, each is worth 3.
    @Test
    void destroyHereticalWorksMakesTheFirstTwoFragmentsValuable() {
        nekro.setSecret("dhw");

        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_IRF3"), 0.8))
                .isCloseTo(3 + 3 + 2, within(EXACT));

        nekro.setCrf(2);
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_IRF1"), 0.8))
                .isCloseTo(1, within(EXACT));
    }

    @Test
    void neverGivesFragments() {
        nekro.setCrf(3);

        assertThat(ComponentValues.give(
                        table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8, true))
                .isInfinite();
    }

    // Someone else's Support for the Throne is a point (0.8 of one, since it can be lost); the AI's own back is worth
    // what it denies the holder.
    @Test
    void supportForTheThrone() {
        assertThat(receiveValue("blue_sftt")).isCloseTo(0.8 * 6, within(EXACT));
        assertThat(receiveValue("black_sftt")).isCloseTo(1.0, within(EXACT));

        // Sol holds it face up, which is one of its 4 points; the two are level, so Sol is a peer (rivalry 0.1).
        sol.getPromissoryNotesInPlayArea().add("black_sftt");
        table.points(nekro, 4);
        table.points(sol, 3);
        assertThat(receiveValue("black_sftt")).isCloseTo(1.0 + 0.1 * 6, within(EXACT));
    }

    // The AI only gives its own Support for the Throne or Alliance when desperate, and only to a player at least 3
    // points behind who is not about to win.
    @Test
    void throneAndAllianceOnlyGoWhenDesperateToAWeakPlayer() {
        table.points(nekro, 5);
        table.points(sol, 2);

        assertThat(giveCost("black_sftt", false)).isInfinite();
        assertThat(giveCost("black_an", false)).isInfinite();
        assertThat(giveCost("black_sftt", true)).isCloseTo(6, within(EXACT));
        assertThat(giveCost("black_an", true)).isCloseTo(1.5, within(EXACT));

        table.points(sol, 1);
        assertThat(giveCost("black_sftt", true)).isInfinite();
        assertThat(giveCost("black_an", true)).isInfinite();
    }

    // A Trade Agreement takes its owner's next replenish: 0.6 of their commodities, or 0.5 of the AI's own back.
    // Giving its own costs 0.5 of its commodities, plus 1 while a Trade replenish may still come this round.
    @Test
    void tradeAgreement() {
        assertThat(receiveValue("blue_ta")).isCloseTo(0.6 * 4, within(EXACT));
        assertThat(receiveValue("black_ta")).isCloseTo(0.5 * 3, within(EXACT));
        assertThat(giveCost("black_ta", false)).isCloseTo(1.5 + 1.0, within(EXACT));

        sol.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        assertThat(giveCost("black_ta", false)).isCloseTo(1.5, within(EXACT));

        sol.getSCs().clear();
        nekro.addSC(TRADE);
        assertThat(giveCost("black_ta", false)).isCloseTo(2.5, within(EXACT));
    }

    // A Ceasefire matters when its owner can reach the AI.
    @Test
    void ceasefire() {
        assertThat(receiveValue("blue_cf")).isCloseTo(0.5, within(EXACT));
        assertThat(giveCost("black_cf", false)).isCloseTo(1.0, within(EXACT));
        assertThat(receiveValue("black_cf")).isCloseTo(1.0, within(EXACT));

        table.presence(sol, TradeTable.BESIDE_NEKRO);
        assertThat(receiveValue("blue_cf")).isCloseTo(2.0, within(EXACT));
        assertThat(giveCost("black_cf", false)).isCloseTo(2.0, within(EXACT));
    }

    // Political Secret only matters once agenda phases happen.
    @Test
    void politicalSecret() {
        assertThat(receiveValue("blue_ps")).isCloseTo(0.5, within(EXACT));
        assertThat(giveCost("black_ps", false)).isCloseTo(1.0, within(EXACT));

        table.custodiansTaken(letnev);
        assertThat(receiveValue("blue_ps")).isCloseTo(1.5, within(EXACT));
    }

    // The AI never uses another player's commander, and keeps its own faction note unless paid.
    @Test
    void allianceAndFactionNotes() {
        assertThat(receiveValue("blue_an")).isCloseTo(0.3, within(EXACT));
        assertThat(giveCost("antivirus", false)).isCloseTo(1.5, within(EXACT));
    }

    // Notes the AI knows how to play are worth what playing them brings; any other note 0.3.
    @Test
    void notesItPlays() {
        assertThat(receiveValue("ms")).isCloseTo(1.0, within(EXACT));
        assertThat(receiveValue("ra")).isCloseTo(1.5, within(EXACT));
        assertThat(receiveValue("tekklar")).isCloseTo(1.0, within(EXACT));
        assertThat(receiveValue("gift")).isCloseTo(1.0, within(EXACT));
        assertThat(receiveValue("antivirus")).isCloseTo(0.5, within(EXACT));
        assertThat(receiveValue("greyfire")).isCloseTo(0.5, within(EXACT));
        assertThat(receiveValue("favor")).isCloseTo(0.5, within(EXACT));
        assertThat(receiveValue("war_funding")).isCloseTo(0.3, within(EXACT));
    }

    // Passing on another player's note costs what it is worth to the AI; handing it back to its owner a little less.
    @Test
    void someoneElsesNoteGivenOn() {
        assertThat(giveCost("red_ta", false)).isCloseTo(0.6 * 2, within(EXACT));
        assertThat(NotesForTrade.giveCost(table.game, nekro, letnev, "red_ta", false))
                .isCloseTo(0.6 * 2 - 0.3, within(EXACT));
    }

    // A "TBD" note requested from the AI costs its least harmful note, never Support for the Throne or Alliance.
    @Test
    void genericNoteCostsTheLeastHarmfulNote() {
        nekro.setPromissoryNote("black_sftt", 11);
        nekro.setPromissoryNote("black_an", 12);
        DealItem generic = item("sendingnekro_receivingsol_PNs_generic1");
        assertThat(ComponentValues.give(table.game, nekro, sol, generic, 0.8, true))
                .isInfinite();

        nekro.setPromissoryNote("black_ps", 13);
        assertThat(NotesForTrade.leastHarmful(table.game, nekro, sol, false)).contains("black_ps");
        assertThat(ComponentValues.give(table.game, nekro, sol, generic, 0.8, true))
                .isCloseTo(1.0, within(EXACT));
    }

    // A note in a deal is named by its owner's hand id: the value comes from the note it names.
    @Test
    void notesInADealAreReadByHandId() {
        sol.setPromissoryNote("blue_ta", 17);
        nekro.setPromissoryNote("black_cf", 23);

        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_PNs_17"), 0.8))
                .isCloseTo(2.4, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_PNs_23"), 0.8, false))
                .isCloseTo(1.0, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_PNs_99"), 0.8, false))
                .isInfinite();
    }

    // A note its sender does not hold is named by alias instead: "_" is written as "fin9", and a faction note's alias
    // has none. The value still comes from the note it names.
    @Test
    void notesInADealCanBeNamedByAlias() {
        assertThat(ComponentValues.receive(
                        table.game, nekro, sol, item("sendingsol_receivingnekro_PNs_bluefin9ta"), 0.8))
                .isCloseTo(2.4, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_PNs_ra"), 0.8))
                .isCloseTo(1.5, within(EXACT));
        assertThat(NotesForTrade.aliasOf(nekro, "antivirus")).contains("antivirus");
        assertThat(NotesForTrade.aliasOf(nekro, "bluefin9ta")).contains("blue_ta");
    }
}
