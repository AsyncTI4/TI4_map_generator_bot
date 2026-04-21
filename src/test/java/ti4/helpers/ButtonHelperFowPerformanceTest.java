package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class ButtonHelperFowPerformanceTest extends BaseTi4Test {
    private static final String PLAYER_ID = "1";
    private static final String PLAYER_NAME = "Alice";

    @Test
    void getPossibleRingsOnlyComputesVisibleTilesOnceInFowPlus() {
        Game game = createFowPlusGame();
        Player player = game.getPlayer(PLAYER_ID);

        try (MockedStatic<FoWHelper> fowHelper = mockStatic(FoWHelper.class)) {
            fowHelper.when(() -> FoWHelper.getTilePositionsToShow(game, player)).thenReturn(Set.of("101", "102"));

            List<Button> buttons = ButtonHelper.getPossibleRings(player, game);

            assertThat(buttons).extracting(Button::getLabel).contains("Ring #1");
            fowHelper.verify(() -> FoWHelper.getTilePositionsToShow(game, player), times(1));
        }
    }

    @Test
    void getTileInARingOnlyComputesVisibleTilesOnceInFowPlus() {
        Game game = createFowPlusGame();
        Player player = game.getPlayer(PLAYER_ID);

        try (MockedStatic<FoWHelper> fowHelper = mockStatic(FoWHelper.class)) {
            fowHelper.when(() -> FoWHelper.getTilePositionsToShow(game, player)).thenReturn(Set.of("101", "102"));

            List<Button> buttons = ButtonHelper.getTileInARing(player, game, "ring_1");

            assertThat(buttons).extracting(Button::getLabel).contains("101 (Mecatol Rex)", "102 (Lodor)");
            fowHelper.verify(() -> FoWHelper.getTilePositionsToShow(game, player), times(1));
        }
    }

    private static Game createFowPlusGame() {
        Game game = new Game();
        game.setFowMode(true);
        game.setFowOption(FOWOption.FOW_PLUS, true);
        game.setTile(new Tile("18", "101"));
        game.setTile(new Tile("26", "102"));

        Player player = game.addPlayer(PLAYER_ID, PLAYER_NAME);
        player.setFaction(game, "arborec");
        player.setColor("red");
        return game;
    }
}
