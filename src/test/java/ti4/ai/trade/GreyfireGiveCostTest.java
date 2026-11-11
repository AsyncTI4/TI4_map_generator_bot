package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Greyfire Mutagen cannot be played against the Yin (it needs 2 or more non-Yin ground forces) and goes back to the
// Yin once played, so the Yin gives it away for less than its other faction notes.
class GreyfireGiveCostTest extends BaseTi4Test {

    @Test
    void theYinGivesGreyfireMutagenCheaply() {
        TradeTable table = TradeTable.withAiSol();
        Player yin = table.humanSeat("yin", "green");

        assertThat(NotesForTrade.giveCost(table.game, yin, table.sol, "greyfire"))
                .isEqualTo(0.5);
    }
}
