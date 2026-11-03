package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ThirtyKFrontierExploreHandlerTest extends BaseTi4Test {

    // The x3 deck holds 3 Systematic Sympathies; a custom objective can only be scored once per player, so a
    // player who qualifies again needs a fresh objective to actually get the victory point.
    @Test
    void systematicSympathiesUsesAFreshObjectiveForARepeatScore() {
        Game game = new Game();
        Player sol = game.addPlayer("sol-id", "sol");
        Player hacan = game.addPlayer("hacan-id", "hacan");

        String first = ThirtyKFrontierExploreHandler.sympathiesObjectiveFor(game, sol);
        assertThat(first).isEqualTo("Systematic Sympathies");
        assertThat(game.scorePublicObjective(sol.getUserID(), game.addCustomPO(first, 1)))
                .isTrue();

        String second = ThirtyKFrontierExploreHandler.sympathiesObjectiveFor(game, sol);
        assertThat(second).isEqualTo("Systematic Sympathies 2");
        assertThat(game.scorePublicObjective(sol.getUserID(), game.addCustomPO(second, 1)))
                .isTrue();
        assertThat(ThirtyKFrontierExploreHandler.sympathiesObjectiveFor(game, hacan))
                .isEqualTo("Systematic Sympathies");
    }
}
