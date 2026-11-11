package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.testUtils.BaseTi4Test;

// Brother Omar (Yin commander) "satisfies a green technology prerequisite" for good, so the long-term route planners
// (TechPath.skips, TechRoutes) and the stepping-stone count in ResearchPolicy see one green skip while he is unlocked.
// Lives in the strategy package because TechPath.skips and ResearchPolicy.missingPrerequisites are package-private.
class BrotherOmarTechPathTest extends BaseTi4Test {

    private AiTestGame test;
    private Player yin;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        yin = test.addSeat(AiTestGame.SECOND_AI_ID, "yin", "green");
    }

    @Test
    void lockedOmarAddsNoSkip() {
        assertThat(TechPath.skips(test.game, yin)).doesNotContainKey(TechnologyType.BIOTIC);
    }

    @Test
    void unlockedOmarCountsAsOneGreenSkip() {
        yin.unsafeGetLeader("yincommander").setLocked(false);

        assertThat(TechPath.skips(test.game, yin)).containsEntry(TechnologyType.BIOTIC, 1);
    }

    @Test
    void unlockedOmarClosesAGreenGapForSteppingStones() {
        int locked = ResearchPolicy.missingPrerequisites(yin, Mapper.getTech("x89"), null);
        yin.unsafeGetLeader("yincommander").setLocked(false);

        assertThat(ResearchPolicy.missingPrerequisites(yin, Mapper.getTech("x89"), null))
                .isEqualTo(locked - 1);
    }
}
