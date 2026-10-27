package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class GameStyxHomebrewTest extends BaseTi4Test {

    private static Game prophecyOfKingsGame() {
        Game game = new Game();
        game.setName("styx-homebrew");
        game.newGameSetup();
        for (int i = 1; i <= 6; i++) {
            Player player = game.addPlayer("user" + i, "player" + i);
            player.setFaction("sol");
            player.setColor("blue");
        }
        game.setVp(10);
        game.setMaxSOCountPerPlayer(3);
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("a", "b", "c", "d", "e")));
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("f", "g", "h", "i", "j")));
        game.setThundersEdge(false);
        return game;
    }

    @Test
    void aProphecyOfKingsGameWithoutStyxIsNotHomebrew() {
        assertThat(prophecyOfKingsGame().hasHomebrew()).isFalse();
    }

    @Test
    void aProphecyOfKingsGameWhereAPlayerHoldsStyxIsHomebrew() {
        Game game = prophecyOfKingsGame();
        game.getRealAndEliminatedPlayers().getFirst().addPlanet("styx");

        assertThat(game.hasHomebrew()).isTrue();
    }

    @Test
    void aProphecyOfKingsGameWithStyxOnTheMapIsHomebrew() {
        Game game = prophecyOfKingsGame();
        game.setTile(new Tile("fracture4", "frac4"));

        assertThat(game.hasHomebrew()).isTrue();
    }

    @Test
    void aThundersEdgeGameWithStyxIsNotHomebrew() {
        Game game = prophecyOfKingsGame();
        game.setThundersEdge(true);
        game.getRealAndEliminatedPlayers().getFirst().addPlanet("styx");
        game.setTile(new Tile("fracture4", "frac4"));

        assertThat(game.hasHomebrew()).isFalse();
    }
}
