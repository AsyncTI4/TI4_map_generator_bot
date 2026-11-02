package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class ExploreHelperTest {

    @Test
    void checkForMech_withoutPlanetIsFalse() {
        // Regression: /explore use of a hazardous card without a planet used to NPE in AliasHandler.resolveTile.
        assertFalse(ExploreHelper.checkForMech(null, mock(Game.class), mock(Player.class)));
    }

    @Test
    void checkForInf_withoutPlanetIsFalse() {
        assertFalse(ExploreHelper.checkForInf(null, mock(Game.class), mock(Player.class)));
    }

    @Test
    void checkForMech_planetNotOnTheMapIsFalse() {
        assertFalse(ExploreHelper.checkForMech("notaplanet", mock(Game.class), mock(Player.class)));
    }

    @Test
    void checkForInf_planetNotOnTheMapIsFalse() {
        assertFalse(ExploreHelper.checkForInf("notaplanet", mock(Game.class), mock(Player.class)));
    }
}
