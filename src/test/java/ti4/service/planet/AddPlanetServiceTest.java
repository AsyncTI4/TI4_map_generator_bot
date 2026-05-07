package ti4.service.planet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class AddPlanetServiceTest extends BaseTi4Test {

    @Test
    void ministerOfExplorationGrantsTradeGoodWhenGainingControlOfAPlanet() {
        Game game = electedMinisterGame();
        game.setTile(new Tile("18", "18"));

        AddPlanetService.addPlanet(game.getPlayer("user"), "mr", game);

        assertThat(game.getPlayer("user").getTg()).isEqualTo(1);
    }

    @Test
    void ministerOfExplorationDoesNotGrantTradeGoodWhenGainingControlOfASpaceStation() {
        Game game = electedMinisterGame();
        game.setTile(new Tile("111", "111"));

        AddPlanetService.addPlanet(game.getPlayer("user"), "oluzstation", game);

        assertThat(game.getPlayer("user").getTg()).isZero();
    }

    private Game electedMinisterGame() {
        Game game = new Game();
        game.setLaws(Map.of("minister_exploration", 1));
        game.setLawsInfo(Map.of("minister_exploration", "arborec"));
        var player = game.addPlayer("user", "User");
        player.setFaction("arborec");
        return game;
    }
}
