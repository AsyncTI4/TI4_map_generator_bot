package ti4.discord.interactions.buttons.handlers.strategycard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class PickStrategyCardButtonHandlerTest extends BaseTi4Test {

    @Test
    void deflectionSpendsFromChoosersStrategyPoolOnly() {
        Game game = new Game();
        game.setStoredValue("deflectedSC", "4");

        Player deflectionPlayer = new Player("deflectionPlayer", "", game);
        deflectionPlayer.setFleetCC(2);
        deflectionPlayer.setStrategicCC(3);

        Player choosingPlayer = new Player("choosingPlayer", "", game);
        choosingPlayer.setFleetCC(2);
        choosingPlayer.setStrategicCC(2);

        boolean result = PickStrategyCardButtonHandler.applyDeflectionCostIfNeeded(game, choosingPlayer, 4, null);

        assertThat(result).isTrue();
        assertThat(choosingPlayer.getStrategicCC()).isEqualTo(1);
        assertThat(choosingPlayer.getFleetCC()).isEqualTo(2);
        assertThat(deflectionPlayer.getStrategicCC()).isEqualTo(3);
        assertThat(deflectionPlayer.getFleetCC()).isEqualTo(2);
    }

    @Test
    void deflectionBlocksChooserWithoutStrategyToken() {
        Game game = new Game();
        game.setStoredValue("deflectedSC", "4");

        Player choosingPlayer = new Player("choosingPlayer", "", game);
        choosingPlayer.setFleetCC(2);
        choosingPlayer.setStrategicCC(0);

        boolean result = PickStrategyCardButtonHandler.applyDeflectionCostIfNeeded(game, choosingPlayer, 4, null);

        assertThat(result).isFalse();
        assertThat(choosingPlayer.getStrategicCC()).isZero();
        assertThat(choosingPlayer.getFleetCC()).isEqualTo(2);
    }

    @Test
    void deflectionDoesNothingForOtherStrategyCards() {
        Game game = new Game();
        game.setStoredValue("deflectedSC", "4");

        Player choosingPlayer = new Player("choosingPlayer", "", game);
        choosingPlayer.setFleetCC(2);
        choosingPlayer.setStrategicCC(2);

        boolean result = PickStrategyCardButtonHandler.applyDeflectionCostIfNeeded(game, choosingPlayer, 5, null);

        assertThat(result).isTrue();
        assertThat(choosingPlayer.getStrategicCC()).isEqualTo(2);
        assertThat(choosingPlayer.getFleetCC()).isEqualTo(2);
    }
}
