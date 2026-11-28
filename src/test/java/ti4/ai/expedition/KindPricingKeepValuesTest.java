package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.HandPricingRecursionTable;
import ti4.ai.actioncards.ActionCardValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class KindPricingKeepValuesTest extends BaseTi4Test {

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

    // Outside the loop the hand is priced exactly as before: each card's keep value, in discard order.
    @Test
    void aHandPricedOnceIsEachCardsKeepValueInDiscardOrder() {
        List<Double> expected = rankedKeeps();

        assertThat(KindPricing.keepValues(game, seat)).isEqualTo(expected);
        assertThat(KindPricing.keepValues(game, seat)).isEqualTo(expected);
    }

    // On the table that used to recurse, the hand is priced at finite values, and the priors used inside the loop are
    // not cached in place of the outer pricing.
    @Test
    void theHandOnTheRecursingTableIsPricedAtFiniteValuesAndRepricedTheSame() {
        HandPricingRecursionTable table = HandPricingRecursionTable.throughTechnology();

        List<Double> first = KindPricing.keepValues(table.game, table.seat);
        List<Double> again = KindPricing.keepValues(table.game, table.seat);

        assertThat(first).hasSize(2).allSatisfy(keep -> assertThat(keep).isFinite());
        assertThat(again).isEqualTo(first);
    }
}
