package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.perception.PromptButton;
import ti4.testUtils.BaseTi4Test;

// After a cancel removed the last hit (Maneuvering Jets on SPACE CANNON DEFENCE, or any "Cancel a Hit"), the engine
// used to leave an "Auto-assign Hits" button for 0 hits; pressing it changed nothing, so the seat pressed it until the
// game stalled. The AI ignores any such button, ground or space.
class ZeroHitAssignmentTest extends BaseTi4Test {

    private static PromptButton button(String handlerId) {
        return new PromptButton(0, "FFCC_sardakk_" + handlerId, handlerId, "sardakk", "Auto-assign Hits", false);
    }

    @Test
    void anAssignmentOfNoHitsIsIgnored() {
        assertThat(CombatRules.assignsNothing(button("autoAssignGroundHits_thundersedge_0")))
                .isTrue();
        assertThat(CombatRules.assignsNothing(button("autoAssignGroundHits_thundersedge_1")))
                .isFalse();
        assertThat(CombatRules.assignsNothing(button("autoAssignSpaceHits_204_0")))
                .isTrue();
        assertThat(CombatRules.assignsNothing(button("autoAssignSpaceCannonOffenceHits_204_0")))
                .isTrue();
        assertThat(CombatRules.assignsNothing(button("autoAssignSpaceHits_204_2")))
                .isFalse();
    }
}
