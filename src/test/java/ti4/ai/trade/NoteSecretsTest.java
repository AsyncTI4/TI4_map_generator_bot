package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.brain.AiMemory;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Strengthen Bonds (another player's note in your play area) and Betray a Friend (beat a player whose note is in your
 * play area) make the notes that land in a play area worth buying. Nekro (black) and Sol (blue) are neighbouring AI
 * seats that each hold their own Support for the Throne and Alliance.
 */
class NoteSecretsTest extends BaseTi4Test {

    private static final String SOL_THRONE = "blue_sftt";
    private static final String SOL_ALLIANCE = "blue_an";

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        table.game.setRound(3);
        table.nekroAndSolNeighbour();
        for (String suffix : List.of("_sftt", "_an")) {
            nekro.addOwnedPromissoryNoteByID("black" + suffix);
            nekro.setPromissoryNote("black" + suffix);
            sol.addOwnedPromissoryNoteByID("blue" + suffix);
            sol.setPromissoryNote("blue" + suffix);
        }
        table.stock(nekro, 3, 8);
        table.stock(sol, 4, 0);
        table.points(nekro, 3);
        table.points(sol, 3);
    }

    @Test
    void strengthenBondsMakesAPlayAreaNoteWorthMostOfAPoint() {
        double without = NotesForTrade.receiveValue(table.game, nekro, SOL_ALLIANCE);
        nekro.setSecret(NoteSecrets.STRENGTHEN_BONDS);

        assertThat(NotesForTrade.receiveValue(table.game, nekro, SOL_ALLIANCE)).isGreaterThan(without + 3);
        assertThat(NoteSecrets.value(table.game, nekro, "black_an")).isZero();
    }

    // One note from another player already meets the secret: a second one adds nothing.
    @Test
    void stopsChasingOnceAnotherPlayersNoteIsInThePlayArea() {
        nekro.setSecret(NoteSecrets.STRENGTHEN_BONDS);
        table.humanSeat("letnev", "red");
        nekro.getPromissoryNotesInPlayArea().add("red_sftt");

        assertThat(NoteSecrets.value(table.game, nekro, SOL_ALLIANCE)).isZero();
    }

    // Betray a Friend wants a note from a neighbour it could fight, and only one from each.
    @Test
    void betrayAFriendWantsANeighboursNote() {
        nekro.setSecret(NoteSecrets.BETRAY_A_FRIEND);

        assertThat(NoteSecrets.value(table.game, nekro, SOL_THRONE)).isPositive();

        nekro.getPromissoryNotesInPlayArea().add(SOL_ALLIANCE);
        assertThat(NoteSecrets.value(table.game, nekro, SOL_THRONE)).isZero();
    }

    // Holding the secret, Nekro offers trade goods for Sol's Alliance.
    @Test
    void offersTradeGoodsForAPlayAreaNote() {
        nekro.setSecret(NoteSecrets.STRENGTHEN_BONDS);

        assertThat(NoteDeals.best(table.context(), List.of(sol)))
                .anyMatch(option -> option.deal().notes("sol").contains(NotesForTrade.detailOf(SOL_ALLIANCE))
                        && option.deal().total("nekro", ItemType.TRADE_GOODS) > 0);
    }

    // Every ask that did not land raises the next one: by 1 early, 2 in the middle rounds and 3 near the end.
    @Test
    void eachUnansweredAskRaisesTheNextByMoreLaterInTheGame() {
        nekro.setSecret(NoteSecrets.STRENGTHEN_BONDS);
        AiMemory memory = new AiMemory();
        int first = NoteSecrets.firstAsk(table.game, nekro, sol, SOL_ALLIANCE);

        assertThat(NoteSecrets.askFloor(memory, table.game, nekro, sol, SOL_ALLIANCE))
                .isEqualTo(first);
        NoteSecrets.asked(memory, sol);
        NoteSecrets.asked(memory, sol);
        assertThat(NoteSecrets.askFloor(memory, table.game, nekro, sol, SOL_ALLIANCE))
                .isEqualTo(Math.min(NoteSecrets.MAX_PRICE, first + 4));

        table.game.setRound(2);
        assertThat(NoteSecrets.step(table.game)).isEqualTo(1);
        table.points(sol, 7);
        assertThat(NoteSecrets.step(table.game)).isEqualTo(3);
    }
}
