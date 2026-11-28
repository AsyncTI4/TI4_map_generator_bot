package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.helpers.ButtonHelper;
import ti4.testUtils.BaseTi4Test;

class HandPressureTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
    }

    private void fillHand(int cards) {
        String[] pool = {"mining_initiative", "industrial_initiative", "intercept", "rout", "sh1", "sh2", "sh3", "sh4"};
        for (int i = 0; i < cards; i++) test.nekro.setActionCard(pool[i % pool.length] + (i / pool.length), i + 1);
    }

    @Test
    void aHandWellUnderTheLimitKeepsTheFullBar() {
        fillHand(2);

        assertThat(HandPressure.share(test.game, test.nekro, PlayMoment.BEFORE_PASSING, "mining_initiative0"))
                .isEqualTo(1.0);
    }

    // The status-phase draw would force a discard anyway, so the card that would go is free to play first
    // (LRR 2.1, note X: no card may be played to get back down to the limit).
    @Test
    void theCardAFullHandWouldDiscardIsFreeToPlayBeforePassing() {
        fillHand(ButtonHelper.getACLimit(test.game, test.nekro));
        String forced = ActionCardValue.discardOrder(test.game, test.nekro).getFirst();

        assertThat(HandPressure.share(test.game, test.nekro, PlayMoment.BEFORE_PASSING, forced))
                .isZero();
        assertThat(HandPressure.share(test.game, test.nekro, PlayMoment.IN_TURN, forced))
                .isEqualTo(HandPressure.NEAR_LIMIT_SHARE);
    }
}
