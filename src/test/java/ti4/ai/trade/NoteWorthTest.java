package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.ThronePoint;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * What a Support for the Throne point and an Alliance are worth, on the user's guidance: the throne point is only a
 * heavy loss when it decides the game, and Alliances follow the expert table of buy values (early rounds 1-3, late
 * round 4+, plus a modifier while the owner's commander is locked). No note is ever worth less than 0.25.
 */
class NoteWorthTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private TradeTable table;
    private Player nekro;
    private Player sol;
    private Player letnev;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        letnev = table.humanSeat("letnev", "red");
        table.game.setRound(5);
    }

    // Nekro holds Sol's point but sits on 3 (2 without it) while Sol leads on 7: four behind, the point barely
    // matters. 6 per point x 0.5 x (1 - 4/5).
    @Test
    void throneIsCheapWhenTheHolderIsFarFromWinning() {
        table.points(nekro, 3);
        table.points(sol, 7);
        table.points(letnev, 3);

        assertThat(ThronePoint.kept(table.game, nekro, sol)).isCloseTo(6 * 0.5 * 0.2, within(EXACT));
    }

    // Nekro is on 9 of 10 with the point and has nothing else to score: losing it costs the game, a full point at
    // the endgame rate (capped at 12).
    @Test
    void throneIsAFullPointWhenItDecidesTheGame() {
        table.points(nekro, 9);
        table.points(sol, 5);

        assertThat(ThronePoint.kept(table.game, nekro, sol)).isCloseTo(12, within(EXACT));
    }

    // Sol, who gave the note, is on 9 and about to win whatever Nekro does: Nekro's point is a lost cause.
    @Test
    void throneIsCheapWhenTheGiverWinsAnyway() {
        table.points(nekro, 4);
        table.points(sol, 9);

        assertThat(ThronePoint.kept(table.game, nekro, sol)).isCloseTo(0.15 * 8, within(EXACT));
    }

    // Receiving the point that reaches the goal is a win.
    @Test
    void theWinningThroneIsWorthTheGame() {
        table.points(nekro, 9);

        assertThat(ThronePoint.gained(table.game, nekro, sol)).isEqualTo(ScoringStakes.WIN_VALUE);
    }

    // Muaat's Alliance: 4 early, 1 from round 4, 1 less while Muaat's commander is locked.
    @Test
    void allianceBuyValueFollowsTheTableByRound() {
        Player muaat = table.aiSeat("muaat", "yellow");
        muaat.unsafeGetLeader("muaatcommander").setLocked(false);

        table.game.setRound(2);
        assertThat(AllianceValue.buyValue(table.game, nekro, muaat, false)).isCloseTo(4, within(EXACT));
        table.game.setRound(4);
        assertThat(AllianceValue.buyValue(table.game, nekro, muaat, false)).isCloseTo(1, within(EXACT));

        muaat.unsafeGetLeader("muaatcommander").setLocked(true);
        table.game.setRound(2);
        assertThat(AllianceValue.buyValue(table.game, nekro, muaat, false)).isCloseTo(3, within(EXACT));
    }

    // The faction-specific bonus: Xxcha's Alliance is worth 2 more to Nekro than to Sol.
    @Test
    void allianceBuyValueAddsTheBuyersBonus() {
        Player xxcha = table.aiSeat("xxcha", "yellow");
        xxcha.unsafeGetLeader("xxchacommander").setLocked(false);
        table.game.setRound(2);

        assertThat(AllianceValue.buyValue(table.game, nekro, xxcha, true)).isCloseTo(3, within(EXACT));
        assertThat(AllianceValue.buyValue(table.game, sol, xxcha, true)).isCloseTo(1, within(EXACT));
    }

    // Hacan's late, locked Alliance is worth 0 - 1 by the table, but no note is worth less than 0.25.
    @Test
    void noAllianceIsWorthLessThanAQuarter() {
        Player hacan = table.aiSeat("hacan", "yellow");
        hacan.addOwnedPromissoryNoteByID("yellow_an");

        assertThat(AllianceValue.buyValue(table.game, nekro, hacan, false)).isCloseTo(0.25, within(EXACT));
        assertThat(NotesForTrade.receiveValue(table.game, nekro, "yellow_an")).isCloseTo(0.25, within(EXACT));
    }

    // Even with nobody else to sell to and a neighbour's goodwill, the AI never gives a note for nothing.
    @Test
    void neverGivesANoteAwayForFree() {
        nekro.addOwnedPromissoryNoteByID("black_an");
        nekro.addOwnedPromissoryNoteByID("black_ta");
        table.stock(nekro, 0, 0);
        table.nekroAndSolNeighbour();

        assertThat(NotesForTrade.giveCost(table.game, nekro, sol, "black_an")).isGreaterThanOrEqualTo(0.25);
        assertThat(NotesForTrade.giveCost(table.game, nekro, sol, "black_ta")).isGreaterThanOrEqualTo(0.25);
    }

    // Titans' Alliance is worth 4 early to Mentak (2 + 2) and 2 to Sol. Selling it to Sol forgoes 60% of what Mentak
    // would pay; selling it to Mentak forgoes only 60% of Sol's price, so Mentak is the better sale.
    @Test
    void sellingPrefersTheBetterBuyer() {
        Player titans = table.aiSeat("titans", "yellow");
        titans.addOwnedPromissoryNoteByID("yellow_an");
        letnev.setFaction("mentak");
        table.game.setRound(2);
        titans.unsafeGetLeader("titanscommander").setLocked(false);

        double toSol = NotesForTrade.giveCost(table.game, titans, sol, "yellow_an");
        double toMentak = NotesForTrade.giveCost(table.game, titans, letnev, "yellow_an");

        assertThat(toSol).isCloseTo(0.6 * 4, within(EXACT));
        assertThat(toMentak).isCloseTo(0.6 * 2, within(EXACT));
        assertThat(NotesForTrade.receiveValue(table.game, letnev, "yellow_an"))
                .isGreaterThan(NotesForTrade.receiveValue(table.game, sol, "yellow_an"));
    }
}
