package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ComponentValuesTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

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

    private double giveCost(String alias) {
        return NotesForTrade.giveCost(table.game, nekro, sol, alias);
    }

    // Trade goods are worth 1 either way, and a commodity received becomes a trade good.
    @Test
    void tradeGoodsAndReceivedCommoditiesAreWorthOne() {
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_TGs_2"), 0.8))
                .isCloseTo(2, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Comms_3"), 0.8))
                .isCloseTo(3, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_TGs_2"), 0.8))
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
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Comms_3"), 0.8))
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
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_SendDebt_2"), 0.8))
                .isCloseTo(1.6, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_ClearDebt_2"), 0.8))
                .isCloseTo(1.6, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_ClearDebt_2"), 0.8))
                .isCloseTo(0.8, within(EXACT));
    }

    // A relic fragment is worth 2 trade goods, and 3 for the one that makes three of a kind (unknown fragments
    // count as any kind). A fragment that completes a set is worth 1 more in a trade, as the relic comes a turn sooner.
    @Test
    void theFragmentThatCompletesASetIsWorthMore() {
        nekro.setCrf(1);
        nekro.setUrf(1);
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_CRF1"), 0.8))
                .isCloseTo(4, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_IRF1"), 0.8))
                .isCloseTo(2, within(EXACT));

        nekro.setCrf(0);
        nekro.setUrf(0);
        nekro.setHrf(2);
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_URF1"), 0.8))
                .isCloseTo(4, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_CRF3"), 0.8))
                .isCloseTo(8, within(EXACT));
    }

    // A fragment found by exploring is valued like one received: 2, and 3 when it makes three of a kind.
    @Test
    void aFragmentFoundByExploringIsValuedLikeAReceivedOne() {
        nekro.setCrf(1);
        nekro.setUrf(1);

        assertThat(ComponentValues.gainedFragment(nekro, "CRF")).isCloseTo(3, within(EXACT));
        assertThat(ComponentValues.gainedFragment(nekro, "IRF")).isCloseTo(2, within(EXACT));

        nekro.setSecret("dhw");
        nekro.setCrf(0);
        nekro.setUrf(0);
        assertThat(ComponentValues.gainedFragment(nekro, "HRF")).isCloseTo(4, within(EXACT));
    }

    // Without a partner to leave out, an own commodity is worth 0.3 until someone else could wash it.
    @Test
    void anOwnCommodityHasAValueWithoutAPartner() {
        assertThat(ComponentValues.ownCommodity(table.game, nekro)).isCloseTo(0.3, within(EXACT));
    }

    // Destroy Heretical Works scores for purging 2 fragments: while the AI holds it, each of its first 2 is worth 2
    // more.
    @Test
    void destroyHereticalWorksMakesTheFirstTwoFragmentsValuable() {
        nekro.setSecret("dhw");

        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_IRF3"), 0.8))
                .isCloseTo(4 + 4 + 3 + 1, within(EXACT));

        nekro.setCrf(2);
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_Frags_IRF1"), 0.8))
                .isCloseTo(2, within(EXACT));
    }

    // Giving a fragment costs what holding it was worth: 2, and 3 for one of a set of three. Unknown fragments can
    // stand in, so a lone cultural fragment next to an unknown one is still 2 but the unknown one is not spare.
    @Test
    void givingAFragmentCostsWhatItWasWorth() {
        nekro.setCrf(1);
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8))
                .isCloseTo(2, within(EXACT));

        nekro.setCrf(3);
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8))
                .isCloseTo(3, within(EXACT));

        nekro.setCrf(4);
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8))
                .isCloseTo(2, within(EXACT));
    }

    // Holding Destroy Heretical Works, the first two fragments are worth 4 each, so the AI will not part with them
    // cheaply — but only if it is not asked to judge from the other side of the table.
    @Test
    void hereticalWorksKeepsTwoFragmentsAtHome() {
        nekro.setCrf(2);
        nekro.setSecret("dhw");

        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8))
                .isCloseTo(4, within(EXACT));
        assertThat(ComponentValues.give(
                        table.game, nekro, sol, item("sendingnekro_receivingsol_Frags_CRF1"), 0.8, true))
                .isCloseTo(2, within(EXACT));
    }

    // Someone else's Support for the Throne is a point (worth 6 at 0 points), of which half counts while nobody is
    // near the goal, discounted by the chance of losing it over the 5 rounds the game has left; the AI's own back is
    // worth what it denies the holder.
    @Test
    void supportForTheThrone() {
        table.game.setRound(4);
        double fit = ThroneValue.fit(table.game, nekro, sol);
        assertThat(receiveValue("blue_sftt")).isCloseTo(0.5 * 6 * Math.pow(0.98, 5) + fit, within(EXACT));
        assertThat(receiveValue("black_sftt")).isCloseTo(1.0, within(EXACT));

        // Sol holds it face up, which is one of its 4 points; the two are level, so Sol is a peer (rivalry 0.1).
        sol.getPromissoryNotesInPlayArea().add("black_sftt");
        table.points(nekro, 4);
        table.points(sol, 3);
        assertThat(receiveValue("black_sftt")).isCloseTo(1.0 + 0.1 * 6, within(EXACT));
    }

    // A neighbour's Support for the Throne is easier to lose and fences off the border: it is worth less.
    @Test
    void aNeighboursThroneIsWorthLess() {
        double afar = receiveValue("blue_sftt");

        table.nekroAndSolNeighbour();

        assertThat(receiveValue("blue_sftt")).isLessThan(afar);
    }

    // Giving the AI's own Support for the Throne costs the option of a better deal later: 1/8 of a point per round
    // left (5 here), at most 3/4 of one, less the peace it buys on a border. The point it hands over is charged by the
    // rivalry term, not here.
    @Test
    void ownThroneCostsTheOptionOfKeepingIt() {
        table.game.setRound(4);
        double fit = ThroneValue.fit(table.game, nekro, sol);
        assertThat(giveCost("black_sftt")).isCloseTo(0.5 * 5 / 4 * 6 - fit, within(EXACT));

        table.nekroAndSolNeighbour();
        assertThat(giveCost("black_sftt")).isLessThan(0.5 * 5 / 4 * 6);
    }

    // Late in the game there is little left to wait for: the option of keeping it shrinks.
    @Test
    void theOptionShrinksAsTheGameEnds() {
        double early = giveCost("black_sftt");

        table.points(letnev, 9);

        assertThat(giveCost("black_sftt")).isLessThan(early);
    }

    // Sol's Alliance is worth 1 by the table, reduced by 1 (to the 0.25 floor) while its commander is locked. Giving
    // the
    // AI's own to Sol forgoes 60% of what Letnev would pay for it (Nekro's is worth 2 early).
    @Test
    void allianceFollowsTheCommander() {
        assertThat(receiveValue("blue_an")).isCloseTo(0.25, within(EXACT));

        sol.unsafeGetLeader("solcommander").setLocked(false);
        assertThat(receiveValue("blue_an")).isCloseTo(1.0, within(EXACT));
        assertThat(giveCost("black_an")).isCloseTo(0.6 * 2, within(EXACT));
    }

    // Military Support costs its owner a strategy token whenever it is played, so Sol never gives its own away while it
    // holds one; with none it comes straight back, but no note is ever given for less than 0.25.
    @Test
    void militarySupportIsNeverGivenWhileItsOwnerHoldsAStrategyToken() {
        sol.setStrategicCC(1);
        assertThat(NotesForTrade.giveCost(table.game, sol, nekro, "ms")).isInfinite();

        sol.setStrategicCC(0);
        assertThat(NotesForTrade.giveCost(table.game, sol, nekro, "ms"))
                .isCloseTo(Math.max(0.25, MilitarySupport.RETURN_RISK), within(EXACT));
    }

    // A Trade Agreement is worth its owner's commodities less 1, given or received.
    @Test
    void tradeAgreement() {
        assertThat(receiveValue("blue_ta")).isCloseTo(4 - 1, within(EXACT));
        assertThat(receiveValue("black_ta")).isCloseTo(3 - 1, within(EXACT));
        assertThat(giveCost("black_ta")).isCloseTo(3 - 1, within(EXACT));
    }

    // A Ceasefire is not given away lightly; without a threat, receiving one is worth only the 0.25 every note is
    // worth.
    @Test
    void ceasefire() {
        assertThat(receiveValue("blue_cf")).isCloseTo(0.25, within(EXACT));
        assertThat(giveCost("black_cf")).isCloseTo(2.5, within(EXACT));
        assertThat(receiveValue("black_cf")).isCloseTo(1.0, within(EXACT));

        table.nekroAndSolNeighbour();
        assertThat(receiveValue("blue_cf")).isCloseTo(0.5, within(EXACT));
    }

    // Political Secret is worth 1 per agenda phase still to come, through round 5: in round 3, rounds 4 and 5 before
    // the custodians are taken, rounds 3 to 5 after; afterwards only the 0.25 floor every note keeps.
    @Test
    void politicalSecret() {
        assertThat(receiveValue("blue_ps")).isCloseTo(2, within(EXACT));
        assertThat(giveCost("black_ps")).isCloseTo(2, within(EXACT));

        table.custodiansTaken(letnev);
        assertThat(receiveValue("blue_ps")).isCloseTo(3, within(EXACT));

        table.game.setRound(6);
        assertThat(receiveValue("blue_ps")).isCloseTo(0.25, within(EXACT));
    }

    // The AI keeps its own faction note unless paid.
    @Test
    void factionNotes() {
        assertThat(giveCost("antivirus")).isCloseTo(1.5, within(EXACT));
    }

    // Notes the AI knows how to play are worth what playing them brings; any other note 0.3.
    @Test
    void notesItPlays() {
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
        assertThat(giveCost("red_ta")).isCloseTo(2 - 1, within(EXACT));
        assertThat(NotesForTrade.giveCost(table.game, nekro, letnev, "red_ta")).isCloseTo(2 - 1 - 0.3, within(EXACT));
    }

    // A "TBD" note requested from the AI costs its least harmful note, never Support for the Throne. Its Alliance costs
    // 60% of the 2 Letnev would pay for it.
    @Test
    void genericNoteCostsTheLeastHarmfulNote() {
        nekro.setPromissoryNote("black_sftt", 11);
        DealItem generic = item("sendingnekro_receivingsol_PNs_generic1");
        assertThat(ComponentValues.give(table.game, nekro, sol, generic, 0.8)).isInfinite();

        nekro.setPromissoryNote("black_ps", 13);
        nekro.setPromissoryNote("black_an", 12);
        assertThat(NotesForTrade.leastHarmful(table.game, nekro, sol)).contains("black_an");
        assertThat(ComponentValues.give(table.game, nekro, sol, generic, 0.8)).isCloseTo(0.6 * 2, within(EXACT));
    }

    // A note in a deal is named by its owner's hand id: the value comes from the note it names.
    @Test
    void notesInADealAreReadByHandId() {
        sol.setPromissoryNote("blue_ta", 17);
        nekro.setPromissoryNote("black_cf", 23);

        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_PNs_17"), 0.8))
                .isCloseTo(3.0, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_PNs_23"), 0.8))
                .isCloseTo(2.5, within(EXACT));
        assertThat(ComponentValues.give(table.game, nekro, sol, item("sendingnekro_receivingsol_PNs_99"), 0.8))
                .isInfinite();
    }

    // A note its sender does not hold is named by alias instead: "_" is written as "fin9", and a faction note's alias
    // has none. The value still comes from the note it names.
    @Test
    void notesInADealCanBeNamedByAlias() {
        assertThat(ComponentValues.receive(
                        table.game, nekro, sol, item("sendingsol_receivingnekro_PNs_bluefin9ta"), 0.8))
                .isCloseTo(3.0, within(EXACT));
        assertThat(ComponentValues.receive(table.game, nekro, sol, item("sendingsol_receivingnekro_PNs_ra"), 0.8))
                .isCloseTo(1.5, within(EXACT));
        assertThat(NotesForTrade.aliasOf(nekro, "antivirus")).contains("antivirus");
        assertThat(NotesForTrade.aliasOf(nekro, "bluefin9ta")).contains("blue_ta");
    }
}
