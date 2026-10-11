package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class ActionCardValueTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
    }

    private double keep(String alias) {
        return ActionCardValue.keepValue(test.game, test.nekro, alias);
    }

    // The AI has no rule that plays a card like Intercept, so holding it is worth nothing and it is the first to go.
    @Test
    void aCardNoRulePlaysIsWorthNothing() {
        assertThat(keep("intercept")).isZero();
        assertThat(keep("rout")).isZero();
        assertThat(keep("mb1")).isZero();
    }

    @Test
    void aCardARulePlaysIsWorthItsPriorBeforeTheBoardAsksForIt() {
        assertThat(keep("industrial_initiative")).isEqualTo(ActionCardPrior.keepPrior("industrial_initiative"));
        assertThat(keep("sabo1")).isEqualTo(ActionCardPrior.keepPrior("sabo1"));
    }

    @Test
    void anEconomyCardTheBoardNeedsNowIsWorthWhatItWouldPlayFor() {
        test.nekro.setActionCard("mining_initiative", 1);

        double playValue = ActionCardValue.playValue(test.game, test.nekro, "mining_initiative");

        assertThat(playValue).isGreaterThan(ActionCardPrior.keepPrior("mining_initiative"));
        assertThat(keep("mining_initiative")).isEqualTo(Math.min(playValue, ActionCardValue.ECONOMY_CAP));
    }

    @Test
    void aSecondSabotageIsWorthLessThanTheFirst() {
        test.nekro.setActionCard("sabo1", 1);
        test.nekro.setActionCard("sabo2", 2);

        assertThat(keep("sabo2")).isLessThan(keep("sabo1"));
        assertThat(keep("sabo2")).isEqualTo(0.6 * ActionCardPrior.keepPrior("sabo2"));
    }

    @Test
    void aLoneSabotageIsWorthTheFullPrior() {
        test.nekro.setActionCard("sabo3", 3);

        assertThat(keep("sabo3")).isEqualTo(ActionCardPrior.keepPrior("sabo3"));
    }

    @Test
    void aCardNoRulePlaysIsDiscardedBeforeOneThatIsUsable() {
        test.nekro.setActionCard("overrule", 1);
        test.nekro.setActionCard("industrial_initiative", 2);
        test.nekro.setActionCard("sabo1", 3);

        assertThat(ActionCardValue.discardOrder(test.game, test.nekro))
                .containsExactly("overrule", "industrial_initiative", "sabo1");
    }

    // Among cards that are worth nothing the weaker one goes first.
    @Test
    void cardsNoRulePlaysAreSortedByTheirPrior() {
        test.nekro.setActionCard("overrule", 1);
        test.nekro.setActionCard("mjets1", 2);
        test.nekro.setActionCard("mb1", 3);

        assertThat(ActionCardValue.discardOrder(test.game, test.nekro)).containsExactly("mjets1", "mb1", "overrule");
    }

    @Test
    void theExtraSabotageGoesBeforeTheFirstOne() {
        test.nekro.setActionCard("sabo1", 1);
        test.nekro.setActionCard("sabo2", 2);
        test.nekro.setActionCard("industrial_initiative", 3);

        List<String> order = ActionCardValue.discardOrder(test.game, test.nekro);

        assertThat(order.indexOf("sabo2")).isLessThan(order.indexOf("sabo1"));
    }

    @Test
    void aLegacySeatKeepsValuingCardsTheOldWay() {
        AiSettings.markLegacyActionCards(test.nekro);

        assertThat(keep("sabo1")).isEqualTo(1.5);
        assertThat(keep("mb1")).isEqualTo(1.0);
        assertThat(keep("intercept")).isZero();
        assertThat(keep("mjets1")).isEqualTo(0.5);
        assertThat(keep("industrial_initiative")).isEqualTo(3.0);
    }

    @Test
    void aSeatIsLegacyOnlyWhenMarked() {
        assertThat(AiSettings.usesLegacyActionCards(test.nekro)).isFalse();

        AiSettings.markLegacyActionCards(test.nekro);

        assertThat(AiSettings.usesLegacyActionCards(test.nekro)).isTrue();
        assertThat(AiSettings.usesLegacyActionCards(test.sol)).isFalse();
    }
}
