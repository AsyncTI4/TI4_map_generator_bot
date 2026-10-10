package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class ResearchPolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    // Sol starts with Neural Motivator, one green technology. Hyper Metabolism needs two, so any second green
    // technology is a stepping stone towards it and carries part of its value. Scanlink would be Sol's first
    // yellow technology and unlocks Predictive Intelligence, the best technology needing one yellow.
    @Test
    void valuesATechnologyThatUnlocksABetterOne() {
        double bioStims = ResearchPolicy.value(test.game, test.sol, "bs");
        double scanlink = ResearchPolicy.value(test.game, test.sol, "sdn");

        assertThat(bioStims - ResearchPolicy.baseValue(Mapper.getTech("bs")))
                .isCloseTo(0.3 * ResearchPolicy.baseValue(Mapper.getTech("hm")), Offset.offset(1e-9));
        assertThat(scanlink - ResearchPolicy.baseValue(Mapper.getTech("sdn")))
                .isCloseTo(0.3 * ResearchPolicy.baseValue(Mapper.getTech("pi")), Offset.offset(1e-9));
    }

    // Dacxive Animators is widely rated the weakest technology in the game.
    @Test
    void ranksDacxiveAnimatorsBelowEveryListedTechnology() {
        assertThat(ResearchPolicy.baseValue(Mapper.getTech("dxa")))
                .isLessThan(ResearchPolicy.baseValue(Mapper.getTech("x89")));
    }
}
