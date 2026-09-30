package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class FoWHelperStabarsQolTest extends BaseTi4Test {

    private static Game game(boolean fow, boolean toggle) {
        Game game = new Game();
        game.setFowMode(fow);
        game.setFowOption(FOWOption.STABARS_QOL, toggle);
        return game;
    }

    @Test
    void onlyActiveInFogGamesWithTheToggleOn() {
        assertTrue(FoWHelper.isStabarsQol(game(true, true)));
        assertFalse(FoWHelper.isStabarsQol(game(true, false)));
    }

    // The toggle must never change a non-fog game, even if the option was stored on it somehow.
    @Test
    void neverActiveOutsideFog() {
        assertFalse(FoWHelper.isStabarsQol(game(false, true)));
        assertFalse(FoWHelper.isStabarsQol(game(false, false)));
    }

    // Existing fog games have no stored value for the new option and must keep today's behaviour.
    @Test
    void offByDefault() {
        Game game = new Game();
        game.setFowMode(true);
        assertFalse(FoWHelper.isStabarsQol(game));
    }
}
