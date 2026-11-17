package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ColorChangeHelperTest extends BaseTi4Test {

    private static Player player(String userId, String color) {
        Game game = new Game();
        game.setName("color-lock-test");
        Player player = new Player(userId, userId, game);
        player.setColor(color);
        return player;
    }

    @Test
    void riftsetIsReservedForItsCreator() {
        assertThat(ColorChangeHelper.isColorAllowedForPlayer("riftset", player("someone", "red")))
                .isFalse();
        assertThat(ColorChangeHelper.isColorAllowedForPlayer("riftset", player(Constants.eronousId, "red")))
                .isTrue();
        assertThat(ColorChangeHelper.isColorAllowedForPlayer("blue", player("someone", "red")))
                .isTrue();
    }

    @Test
    void reservedColorStaysReservedForPlayerAlreadyHoldingIt() {
        // Seats handed riftset while setup skipped the check lose it on a re-setup.
        assertThat(ColorChangeHelper.isColorAllowedForPlayer("riftset", player("someone", "riftset")))
                .isFalse();
    }

    @Test
    void unknownColorDoesNotThrow() {
        assertThat(ColorChangeHelper.isColorAllowedForPlayer("not-a-color", player("someone", null)))
                .isTrue();
    }
}
