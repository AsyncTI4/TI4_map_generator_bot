package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.trace.Thinking;
import ti4.ai.trace.ThinkingProfile;
import ti4.ai.trace.ThinkingProfile.SpanTotal;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The self-play stall of aiselfplay737459 (Sol, round 2, holding Overrule and one more card): ranking tactical plans
// values each landing by the explore deck; an Expedition card is worth readying the planet, which can fund the
// action-card expedition; that is priced by the keep value of the cards in hand, and Overrule's keep value asks the
// strategy planner, whose inputs rank tactical plans again. Nothing on the way is cached until it returns, so the cycle
// only ended when the thinking guard cut it ("tactical.candidates re-entered itself 8 deep") and the seat had no
// decision. The trail on this board is the same as in the game:
// tactical.for.expansion > explore.expectedValue > cards.discardOrder > cards.keepValue > strategy.planInputs >
// tactical.candidates > ...
class PlanInputsReentryTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        Tile home = test.nekroHome();
        test.aiIsActive("action");
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        // Two cards in hand make the action-card expedition payable; Overrule is the one whose value plans.
        test.nekro.setActionCard("overrule", 412);
        test.nekro.setActionCard("sabotage", 413);
        test.game.setExploreDeck(new ArrayList<>(List.of("exp1", "exp2", "exp3")));
    }

    @Test
    void rankingPlansWithOverruleInHandAndExpeditionsInTheDeckEnds() {
        ThinkingProfile profile;
        try (Thinking.Decision thinking = Thinking.open(AiSettings.thinkingLimits());
                DecisionMemo.Open memo = DecisionMemo.open()) {
            assertThatCode(() -> TacticalPlanner.ranked(test.game, test.nekro)).doesNotThrowAnyException();
            profile = thinking.profile();
        }

        // The board really walks the cycle: the inner strategy inputs were asked for while the outer ones were open.
        assertThat(profile.spans())
                .extracting(SpanTotal::name)
                .contains("strategy.planInputs", "strategy.planInputs.reentered", "cards.keepValue");
        assertThat(profile.spans())
                .filteredOn(span -> "tactical.candidates".equals(span.name()))
                .allSatisfy(span -> assertThat(span.maxReentry()).isLessThanOrEqualTo(2));
    }

    // Only the inner request goes without plans; the outer inputs are complete and cached as before.
    @Test
    void theOuterInputsKeepTheirPlansAndAreCached() {
        try (Thinking.Decision thinking = Thinking.open(AiSettings.thinkingLimits())) {
            PlanInputs outer = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);

            assertThat(outer.planless()).isFalse();
            assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isSameAs(outer);
        }
    }
}
