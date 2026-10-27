package ti4.service.planet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class FlipTileServiceTest extends BaseTi4Test {

    @Test
    void unlockMalliceFlipsTheLockedWormholeNexus() {
        Game game = new Game();
        game.setTile(new Tile("82a", "201"));

        String planet = FlipTileService.unlockMalliceIfNeeded(null, "lockedmallice", game);

        assertThat(planet).isEqualTo("mallice");
        assertThat(game.getTileByPosition("201").getTileID()).isEqualTo("82b");
        assertThat(game.getTileFromPlanet("mallice")).isNotNull();
    }

    // A stale or double-pressed button can still say "lockedmallice" after the nexus has flipped;
    // this used to throw a NullPointerException and lose the units already removed by the handler.
    @Test
    void unlockMalliceToleratesAnAlreadyFlippedWormholeNexus() {
        Game game = new Game();
        game.setTile(new Tile("82b", "201"));

        String planet = FlipTileService.unlockMalliceIfNeeded(null, "lockedmallice", game);

        assertThat(planet).isEqualTo("mallice");
        assertThat(game.getTileByPosition("201").getTileID()).isEqualTo("82b");
    }

    @Test
    void unlockMalliceHandlesTheHexagonVariant() {
        Game game = new Game();
        game.setTile(new Tile("82ah", "201"));

        String planet = FlipTileService.unlockMalliceIfNeeded(null, "hexlockedmallice", game);

        assertThat(planet).isEqualTo("hexmallice");
        assertThat(game.getTileByPosition("201").getTileID()).isEqualTo("82bh");
    }

    @Test
    void unlockMalliceLeavesOtherPlanetsAlone() {
        Game game = new Game();
        game.setTile(new Tile("82a", "201"));

        String planet = FlipTileService.unlockMalliceIfNeeded(null, "mecatol", game);

        assertThat(planet).isEqualTo("mecatol");
        assertThat(game.getTileByPosition("201").getTileID()).isEqualTo("82a");
    }
}
