package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class StinginessTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private TradeTable table;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
    }

    private static Deal deal(String from, String to, String... raws) {
        return new Deal(List.of(raws).stream()
                .map(raw -> DealItem.parse(raw, from, to).orElseThrow())
                .toList());
    }

    private double rivalry() {
        return Stinginess.rivalry(table.game, table.nekro, table.sol);
    }

    // Rivalry is how much the AI minds the partner gaining: nothing for a player 2 or more points behind, a little
    // for a peer, more per point ahead, more for a strict leader and for a player within 3 points of the goal, and
    // everything for a player about to win.
    @Test
    void rivalryRisesWithThePartnersStanding() {
        Player letnev = table.humanSeat("letnev", "red");
        table.points(table.nekro, 3);

        table.points(table.sol, 1);
        assertThat(rivalry()).as("2 behind").isCloseTo(0, within(EXACT));

        table.points(table.sol, 1);
        assertThat(rivalry()).as("1 behind").isCloseTo(0.1, within(EXACT));

        table.points(table.sol, 1);
        assertThat(rivalry()).as("equal").isCloseTo(0.1, within(EXACT));

        table.points(letnev, 5);
        table.points(table.sol, 2);
        assertThat(rivalry()).as("2 ahead, sharing the lead").isCloseTo(0.3, within(EXACT));

        table.points(table.sol, 1);
        assertThat(rivalry()).as("3 ahead, sole leader").isCloseTo(0.6, within(EXACT));

        table.points(table.sol, 1);
        assertThat(rivalry()).as("4 ahead, sole leader within 3 of the goal").isCloseTo(0.9, within(EXACT));

        table.points(table.sol, 1);
        assertThat(Stinginess.nearWin(table.game, table.sol)).isTrue();
        assertThat(rivalry()).as("about to win").isCloseTo(1.0, within(EXACT));
    }

    // Near a win means within 2 points of the goal and nobody ahead.
    @Test
    void nearWinNeedsTheLead() {
        table.points(table.sol, 8);
        table.points(table.nekro, 9);

        assertThat(Stinginess.nearWin(table.game, table.sol)).isFalse();
        assertThat(Stinginess.nearWin(table.game, table.nekro)).isTrue();
    }

    // A wash: the partner turns 3 worthless-ish commodities (0.5 each) into 3 trade goods.
    @Test
    void partnerGainOfAWash() {
        Deal wash = deal("nekro", "sol", "sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_Comms_3");

        assertThat(Stinginess.partnerGain(table.game, table.sol, table.nekro, wash))
                .isCloseTo(1.5, within(EXACT));
    }

    // With Mirror Computing a trade good spends as 2.
    @Test
    void mirrorComputingDoublesTradeGoods() {
        Deal gift = deal("nekro", "sol", "sendingnekro_receivingsol_TGs_2");
        assertThat(Stinginess.partnerGain(table.game, table.sol, table.nekro, gift))
                .isCloseTo(2, within(EXACT));

        table.sol.addTech("mc");

        assertThat(Stinginess.partnerGain(table.game, table.sol, table.nekro, gift))
                .isCloseTo(4, within(EXACT));
    }

    // Letnev's Munitions Reserves turn trade goods into rerolls, which only matters against a neighbour.
    @Test
    void munitionsOnlyAgainstANeighbour() {
        Player letnev = table.humanSeat("letnev", "red");
        table.presence(table.nekro, TradeTable.NEKRO_SPOT);
        table.presence(letnev, TradeTable.FAR_SPOT);
        Deal gift = deal("nekro", "letnev", "sendingnekro_receivingletnev_TGs_2");

        assertThat(Stinginess.partnerGain(table.game, letnev, table.nekro, gift))
                .isCloseTo(2, within(EXACT));

        table.presence(letnev, TradeTable.BESIDE_NEKRO);

        assertThat(Stinginess.partnerGain(table.game, letnev, table.nekro, gift))
                .isCloseTo(2.5, within(EXACT));
    }

    // Hacan with Quantum Datahub Node and a strategy token swaps strategy cards for 3 trade goods: a deal that takes
    // it to 3 is worth 1.5 more to it.
    @Test
    void datahubCrossingThreeWithAStrategyToken() {
        Player hacan = table.humanSeat("hacan", "red");
        hacan.addTech("qdn");
        hacan.setStrategicCC(1);
        table.stock(hacan, 0, 1);
        Deal gift = deal("nekro", "hacan", "sendingnekro_receivinghacan_TGs_2");

        assertThat(Stinginess.partnerGain(table.game, hacan, table.nekro, gift)).isCloseTo(3.5, within(EXACT));

        hacan.setStrategicCC(0);
        assertThat(Stinginess.partnerGain(table.game, hacan, table.nekro, gift)).isCloseTo(2, within(EXACT));
    }

    // Hacan's commander unlocks at 10 trade goods.
    @Test
    void hacanCommanderCrossingTen() {
        Player hacan = table.humanSeat("hacan", "red");
        assertThat(hacan.hasLeaderUnlocked("hacancommander")).isFalse();
        table.stock(hacan, 0, 8);
        Deal gift = deal("nekro", "hacan", "sendingnekro_receivinghacan_TGs_2");

        assertThat(Stinginess.partnerGain(table.game, hacan, table.nekro, gift)).isCloseTo(4, within(EXACT));

        table.stock(hacan, 0, 10);
        assertThat(Stinginess.partnerGain(table.game, hacan, table.nekro, gift)).isCloseTo(2, within(EXACT));
    }

    // A deal that completes the partner's spend objective: never when it would win them the game, nor when they are
    // far ahead; fine for a player behind.
    @Test
    void vetoesFeedingAWinOrTheLeader() {
        table.solHome();
        table.nekroAndSolNeighbour();
        table.reveal("trade_routes");
        table.stock(table.sol, 0, 3);
        Deal twoTradeGoods =
                deal("nekro", "sol", "sendingnekro_receivingsol_TGs_2", "sendingsol_receivingnekro_Comms_2");
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, twoTradeGoods))
                .isEmpty();

        table.points(table.nekro, 1);
        table.points(table.sol, 6);
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, twoTradeGoods))
                .contains("feeds the leader");

        table.points(table.sol, 3);
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, twoTradeGoods))
                .contains("could let them win");
    }

    @Test
    void vetoesWhatItCannotTradeOrJudge() {
        table.nekroAndSolNeighbour();
        Deal cards = deal("nekro", "sol", "sendingsol_receivingnekro_ACs_3", "sendingnekro_receivingsol_TGs_1");
        Deal twoNotes = deal("nekro", "sol", "sendingnekro_receivingsol_PNs_1", "sendingnekro_receivingsol_PNs_2");
        Deal genericPair = deal("nekro", "sol", "sendingnekro_receivingsol_PNs_generic2");

        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, cards)).contains("unsupported items");
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, twoNotes))
                .contains("two notes");
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, genericPair))
                .contains("two notes");
    }

    @Test
    void vetoesIllegalDeals() {
        Deal wash = deal("nekro", "sol", "sendingnekro_receivingsol_Comms_2", "sendingsol_receivingnekro_Comms_2");
        Deal debt = deal("nekro", "sol", "sendingsol_receivingnekro_SendDebt_2");

        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, wash)).contains("illegal");
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, debt)).isEmpty();
    }

    // Mentak pillages a neighbour who ends a transaction with 3 or more trade goods.
    @Test
    void pillageLeaksToAMentakNeighbour() {
        Player mentak = table.humanSeat("mentak", "red");
        table.nekroAndSolNeighbour();
        table.presence(mentak, TradeTable.BESIDE_NEKRO);
        table.stock(table.nekro, 3, 2);
        Deal sale = deal("nekro", "sol", "sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_1");
        Deal smallSale = deal("nekro", "sol", "sendingnekro_receivingsol_Comms_3");

        assertThat(Stinginess.pillageLeak(table.game, table.nekro, table.sol, sale))
                .isCloseTo(1, within(EXACT));
        assertThat(Stinginess.pillageLeak(table.game, table.nekro, table.sol, smallSale))
                .isCloseTo(0, within(EXACT));
    }

    // Debt-only deals skip Pillage in the bot.
    @Test
    void noPillageOnDebtOnlyDeals() {
        Player mentak = table.humanSeat("mentak", "red");
        table.presence(table.nekro, TradeTable.NEKRO_SPOT);
        table.presence(mentak, TradeTable.BESIDE_NEKRO);
        table.stock(table.nekro, 0, 5);
        Deal debt = deal("nekro", "sol", "sendingsol_receivingnekro_SendDebt_2");

        assertThat(Stinginess.pillageLeak(table.game, table.nekro, table.sol, debt))
                .isCloseTo(0, within(EXACT));
    }

    // A Mentak player who chose not to pillage their own transactions is never pillaged through, nor pillages, a
    // deal they are part of.
    @Test
    void noPillageAfterAnOptedOutParty() {
        Player mentak = table.humanSeat("mentak", "red");
        table.presence(table.nekro, TradeTable.NEKRO_SPOT);
        table.presence(mentak, TradeTable.BESIDE_NEKRO);
        table.stock(table.nekro, 3, 2);
        Deal sale =
                deal("nekro", "mentak", "sendingnekro_receivingmentak_Comms_3", "sendingmentak_receivingnekro_TGs_1");
        assertThat(Stinginess.pillageLeak(table.game, table.nekro, mentak, sale))
                .isCloseTo(1, within(EXACT));

        table.game.setStoredValue("willPillageOwnTransactionsmentak", "yes");

        assertThat(Stinginess.pillageLeak(table.game, table.nekro, mentak, sale))
                .isCloseTo(0, within(EXACT));
    }

    // Twilight's Fall ignores that opt-out (TransactionHelper.hasOptedOutOfOwnTransactionPillage), so the engine still
    // checks Pillage after the deal and the leak stays.
    @Test
    void optingOutDoesNotCountInTwilightsFall() {
        Player mentak = table.humanSeat("mentak", "red");
        table.presence(table.nekro, TradeTable.NEKRO_SPOT);
        table.presence(mentak, TradeTable.BESIDE_NEKRO);
        table.stock(table.nekro, 3, 2);
        table.game.setStoredValue("willPillageOwnTransactionsmentak", "yes");
        table.game.setTwilightsFallMode(true);
        Deal sale =
                deal("nekro", "mentak", "sendingnekro_receivingmentak_Comms_3", "sendingmentak_receivingnekro_TGs_1");

        assertThat(Stinginess.pillageLeak(table.game, table.nekro, mentak, sale))
                .isCloseTo(1, within(EXACT));
    }

    // A note the sender does not hold is requested by alias, and a faction note's alias has no "_" to write as "fin9".
    // It still counts as a note, so two of them are "two notes" rather than "unsupported items".
    @Test
    void bareAliasNotesCountAsNotes() {
        table.nekroAndSolNeighbour();
        Deal twoOwnNotes =
                deal("nekro", "sol", "sendingnekro_receivingsol_PNs_antivirus", "sendingnekro_receivingsol_PNs_ra");

        assertThat(Stinginess.vetoes(table.game, table.nekro, table.sol, twoOwnNotes))
                .contains(Stinginess.Veto.TWO_NOTES)
                .doesNotContain(Stinginess.Veto.UNSUPPORTED);
    }
}
