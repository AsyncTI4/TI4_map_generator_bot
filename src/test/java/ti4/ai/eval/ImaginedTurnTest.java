package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Imagining an opponent's turn rates its moves from what the table can see: its unscored secrets and its hand stay
// hidden for the length of the imagined turn, and only for that seat.
class ImaginedTurnTest extends BaseTi4Test {

    private final AiTestGame test = new AiTestGame();
    private final Player rival = test.addSeat("200000000000000002", "sardakk", "red");

    @Test
    void hidesOnlyTheImaginedSeatsSecretsAndHand() {
        rival.setSecret("sar");
        rival.setActionCard("sabo1");
        test.nekro.setSecret("dyp");

        ImaginedTurn.of(rival, () -> {
            assertThat(ImaginedTurn.underway()).isTrue();
            assertThat(ImaginedTurn.unscoredSecrets(rival)).isEmpty();
            assertThat(ImaginedTurn.actionCards(rival)).isEmpty();
            assertThat(ImaginedTurn.unscoredSecrets(test.nekro)).isNotEmpty();
            return null;
        });

        assertThat(ImaginedTurn.underway()).isFalse();
        assertThat(ImaginedTurn.unscoredSecrets(rival)).isNotEmpty();
        assertThat(ImaginedTurn.actionCards(rival)).isNotEmpty();
    }

    // Control the Region (ships in six systems) is the seat's own secret, so the imagined seat does not chase it.
    @Test
    void anImaginedSeatChasesOnlyPublicObjectives() {
        test.nekroHome();
        test.nekro.setSecret("ctr");

        double seen = new ObjectiveValue(test.game, test.nekro).gainAt("ctr", 6);
        double imagined = ImaginedTurn.of(test.nekro, () -> new ObjectiveValue(test.game, test.nekro))
                .gainAt("ctr", 6);

        assertThat(seen).isPositive();
        assertThat(imagined).isZero();
    }
}
