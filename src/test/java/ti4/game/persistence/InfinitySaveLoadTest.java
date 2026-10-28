package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class InfinitySaveLoadTest extends BaseTi4Test {

    @Test
    void infiniteTradeGoodsAndInfantrySurviveSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player player = game.getRealPlayers().getFirst();
            player.setInfiniteTg(true);
            Tile home = player.getHomeSystemTile();
            UnitHolder planet = home.getPlanetUnitHolders().getFirst();
            UnitKey infantry = Units.getUnitKey(UnitType.Infantry, player.getColorID());
            planet.makeInfinite(infantry);
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            Player reloadedPlayer = reloaded.getPlayer(player.getUserID());
            assertThat(reloadedPlayer.isInfiniteTg()).isTrue();
            UnitHolder reloadedPlanet = reloaded.getTileByPosition(home.getPosition())
                    .getUnitHolders()
                    .get(planet.getName());
            assertThat(reloadedPlanet.isInfinite(infantry)).isTrue();
        }
    }
}
