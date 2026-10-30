package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.brain.StrategyCard;
import ti4.ai.strategy.CardPlan.Benefit;

class CardPlanTest {

    private static final CardPlan WARFARE = new CardPlan(
            StrategyCard.WARFARE,
            6,
            List.of(
                    new Benefit(CardUse.WARFARE_TOKEN, 0.75),
                    new Benefit(CardUse.WARFARE_DOUBLE_BUILD, 4.2, "301"),
                    new Benefit(CardUse.WARFARE_FAR_REACH, 0)));

    @Test
    void valueSumsTheBenefitsAndMainIsTheLargest() {
        assertThat(WARFARE.value()).isCloseTo(4.95, within(1e-9));
        assertThat(WARFARE.main().orElseThrow().use()).isEqualTo(CardUse.WARFARE_DOUBLE_BUILD);
    }

    // Benefits worth nothing are left out, the largest comes first.
    @Test
    void describeNamesTheCardAndItsUses() {
        assertThat(WARFARE.describe()).isEqualTo("Warfare: double build at home (+4.2), +1 command token (+0.8)");
    }

    // An empty target must still decode, and values are written with a dot whatever the default locale is.
    @Test
    void encodeDecodeRoundTrips() {
        String encoded = WARFARE.encode();

        assertThat(encoded).isEqualTo("WARFARE_TOKEN:0.75:;WARFARE_DOUBLE_BUILD:4.20:301;WARFARE_FAR_REACH:0.00:");
        assertThat(CardPlan.decode(StrategyCard.WARFARE, 6, encoded)).contains(WARFARE);
    }

    @Test
    void decodesNothingAsAnEmptyPlanAndRejectsGarbage() {
        assertThat(CardPlan.decode(StrategyCard.IMPERIAL, 8, "").orElseThrow().benefits())
                .isEmpty();
        assertThat(CardPlan.decode(StrategyCard.IMPERIAL, 8, "NOT_A_USE:1.00:")).isEmpty();
        assertThat(CardPlan.decode(StrategyCard.IMPERIAL, 8, "IMPERIAL_SECRET:1.00"))
                .isEmpty();
    }
}
