package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.PromptButton;

class StrategyCardPickLogTest {

    @Test
    void listsEveryCardBestFirstWithItsBreakdown() {
        StrategyCardRanking.Pick warfare =
                pick(6, StrategyCard.WARFARE, 10.42, new CardPlan.Benefit(CardUse.WARFARE_BUILD_THEN_MOVE, 10.06));
        StrategyCardRanking.Pick diplomacy = pick(
                2,
                StrategyCard.DIPLOMACY,
                7.98,
                new CardPlan.Benefit(CardUse.DIPLOMACY_READY, 3.0),
                new CardPlan.Benefit(CardUse.DIPLOMACY_PROTECT, 5.24));

        String line = StrategyCardPickLog.describe(List.of(warfare, diplomacy));

        assertThat(line)
                .isEqualTo("6 Warfare 10.42 [build at home then move out +10.06 / prior/tg/denial +0.36]"
                        + " | 2 Diplomacy 7.98 [lock a threatened system +5.24 / ready planets +3.00 /"
                        + " prior/tg/denial -0.26]");
    }

    @Test
    void neverWritesTheStoredValueSeparators() {
        // The game save joins stored values with "," and ":", so the line must contain neither.
        String line = StrategyCardPickLog.describe(List.of(
                pick(6, StrategyCard.WARFARE, 1.0, new CardPlan.Benefit(CardUse.WARFARE_BUILD_THEN_MOVE, 1.0))));

        assertThat(line).doesNotContain(",", ":", ";");
    }

    private static StrategyCardRanking.Pick pick(
            int initiative, StrategyCard card, double value, CardPlan.Benefit... benefits) {
        PromptButton button = new PromptButton(0, "scPick_" + initiative, "scPick_" + initiative, "sol", "", false);
        return new StrategyCardRanking.Pick(button, new CardPlan(card, initiative, List.of(benefits)), value, 0);
    }
}
