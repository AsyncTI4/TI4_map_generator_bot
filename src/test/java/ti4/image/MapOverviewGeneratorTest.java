package ti4.image;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class MapOverviewGeneratorTest extends BaseTi4Test {

    private static Game gameWith(String... centres) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("overview-test");
        game.setFowMode(true);
        for (String centre : centres) {
            MapFrame.positionsWithin(centre, 1).forEach(position -> game.setTile(new Tile("19", position)));
        }
        return game;
    }

    @Test
    void spreadOutMapWithFractureAndCornersIsScaledDownToTheNormalMaximum() {
        // Far north and far south clusters are ~7000px apart: too tall for one normal map image.
        Game game = gameWith("1201", "1237");
        for (int index = 1; index <= 7; index++) {
            game.setTile(new Tile("2" + index, "frac" + index));
        }
        game.setTile(new Tile("25", "tl"));
        game.setTile(new Tile("26", "br"));
        MapSegment.setAutoSectors(game, true);

        BufferedImage image = MapOverviewGenerator.render(game, true);

        assertTrue(image.getWidth() <= MapFrame.MAX_WIDTH);
        assertTrue(image.getHeight() <= MapFrame.MAX_HEIGHT);
        assertTrue(image.getHeight() >= MapFrame.MAX_HEIGHT - 1, "the tall map fills the height it is scaled to");
    }

    @Test
    void smallMapIsNotBlownUp() {
        BufferedImage image = MapOverviewGenerator.render(gameWith("000"), false);
        // One 3-hex-tall cluster plus padding stays at its natural size.
        assertTrue(image.getHeight() < 3 * TileGenerator.TILE_HEIGHT + 2 * 200 + 10);
    }
}
