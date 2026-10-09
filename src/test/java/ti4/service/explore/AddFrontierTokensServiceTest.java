package ti4.service.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class AddFrontierTokensServiceTest extends BaseTi4Test {

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
    }

    private static boolean hasFrontier(Tile tile) {
        return tile.getSpaceUnitHolder().getTokenList().stream().anyMatch(token -> token.contains("frontier"));
    }

    // Tile 46 is an empty system, so it qualifies for a frontier token wherever it sits - except in the Fracture.
    @Test
    void fractureSystemsGetNoFrontierToken() {
        Game game = new Game();
        game.setName("frontier-test");
        Tile ordinary = new Tile("46", "101");
        Tile fracture = new Tile("46", "frac1");
        game.setTile(ordinary);
        game.setTile(fracture);

        try (MockedStatic<MessageHelper> mh = mockStatic(MessageHelper.class)) {
            AddFrontierTokensService.addFrontierTokens(null, game);
        }

        assertThat(hasFrontier(game.getTileByPosition("101"))).isTrue();
        assertThat(hasFrontier(game.getTileByPosition("frac1"))).isFalse();
    }
}
