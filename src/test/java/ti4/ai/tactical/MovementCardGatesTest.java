package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.MovementCard;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The planner runs a whole extra pass per held movement card, so the gate opens only when the card would let a fleet
// reach somewhere new or sooner.
class MovementCardGatesTest extends BaseTi4Test {

    // Nav Suite takes away a rift's +1 as well as its roll (Nav Suite IV), and a rift route is already legal, so a rift
    // alone never opens the pass. An asteroid field the cruiser cannot enter does (Nav Suite III).
    @Test
    void onlyAnAnomalyThatKeepsAFleetOutOpensTheNavSuitePass() {
        AiTestGame test = new AiTestGame();
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        String next = AiTestGame.neighbourOf(AiTestGame.HOME);

        test.place("41", next);
        assertThat(MovementCardGates.worthAPass(test.game, test.nekro, MovementCard.NAV_SUITE, List.of(home)))
                .isFalse();

        test.place("44", next);
        assertThat(MovementCardGates.worthAPass(test.game, test.nekro, MovementCard.NAV_SUITE, List.of(home)))
                .isTrue();
    }
}
