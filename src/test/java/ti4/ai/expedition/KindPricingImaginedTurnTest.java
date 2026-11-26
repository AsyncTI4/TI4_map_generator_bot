package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// A rival imagining the seat's turn cannot see its hand or secrets. Keep values worked out from that empty hand must
// not be served to the seat's own decision on the same save, nor the seat's real keep values to the rival.
class KindPricingImaginedTurnTest extends BaseTi4Test {

    private AiTestGame test;
    private Game game;
    private Player seat;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        game = test.game;
        test.aiIsActive("action");
        test.nekroHome();
        seat = test.nekro;
        seat.setStrategicCC(2);
        seat.setTacticalCC(3);
        seat.setActionCard("strategize1", 412);
        seat.setActionCard("sabo1", 413);
        seat.setActionCard("mining_initiative", 414);
    }

    private List<Double> rankedKeeps() {
        return ActionCardValue.discardOrder(game, seat).stream()
                .map(alias -> ActionCardValue.keepValue(game, seat, alias))
                .toList();
    }

    private Optional<KindPricing.Price> secretPrice() {
        return KindPricing.of(game, seat, ExpeditionKind.SECRET, Holdings.of(game, seat), SpendHorizon.AHEAD);
    }

    @Test
    void anImaginedHandIsNotServedToTheSeatsOwnPricing() {
        List<Double> expected = rankedKeeps();

        List<Double> imagined = ImaginedTurn.of(seat, () -> KindPricing.keepValues(game, seat));
        List<Double> own = KindPricing.keepValues(game, seat);

        assertThat(imagined).isEmpty();
        assertThat(own).hasSize(3).isEqualTo(expected);
    }

    @Test
    void theSeatsOwnHandIsNotServedToAnImaginedTurn() {
        List<Double> own = KindPricing.keepValues(game, seat);
        List<Double> imagined = ImaginedTurn.of(seat, () -> KindPricing.keepValues(game, seat));

        assertThat(own).hasSize(3);
        assertThat(imagined).isEmpty();
    }

    // Only the seat knows it holds a secret, so an imagined turn sees nothing to give up for the secret expedition.
    @Test
    void anImaginedTurnDoesNotPriceAHiddenSecret() {
        seat.setSecret("mtm");

        Optional<KindPricing.Price> imagined = ImaginedTurn.of(seat, this::secretPrice);

        assertThat(imagined).isEmpty();
        assertThat(secretPrice()).isPresent();
    }
}
