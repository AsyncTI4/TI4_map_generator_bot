package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// The Xxcha pays a strategy token each time a holder plays Political Favor and loses the agenda it may have wanted, so
// it very rarely lends the card: a base price of 5 plus a token for each agenda phase still to come, well above the
// flat price of any other faction note.
class PoliticalFavorTradeTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekro.addOwnedPromissoryNoteByID("favor");
        test.nekro.setPromissoryNote("favor");
    }

    private double giveCost() {
        return NotesForTrade.giveCost(test.game, test.nekro, test.sol, "favor");
    }

    @Test
    void costsATokenForEachAgendaPhaseStillToComeOnTopOfTheBase() {
        test.game.setRound(2);

        assertThat(giveCost()).isCloseTo(5 + 3, within(EXACT));
    }

    @Test
    void costsTheBaseOnceNoAgendaPhaseIsLeft() {
        test.game.setRound(6);

        assertThat(giveCost()).isCloseTo(5, within(EXACT));
    }

    @Test
    void isDearerToLendThanAnotherFactionNote() {
        test.game.setRound(2);
        test.nekro.addOwnedPromissoryNoteByID("antivirus");
        test.nekro.setPromissoryNote("antivirus");

        assertThat(giveCost()).isGreaterThan(NotesForTrade.giveCost(test.game, test.nekro, test.sol, "antivirus"));
    }
}
