package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.service.fow.FogViewer;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridges;
import ti4.testUtils.BaseTi4Test;

// System 19 with a one-way hyperlane leading in from the north and one leading out to the south; bridged N -> S
// where a test asks for it.
class HyperlaneBridgeOverlayTest extends BaseTi4Test {

    private static final List<String> AROUND = MapPositionMapper.getAdjacentTilePositions("000");
    private static final int PAD = TileGeometry.TILE_PADDING;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "000"));
        oneWayHyperlane(AROUND.get(0), 0, 3);
        oneWayHyperlane(AROUND.get(3), 0, 3);
    }

    @Test
    void offByDefaultSoTheSystemLooksAsBefore() {
        BufferedImage withLanes = render();
        game.getCustomHyperlaneData().clear();
        assertThat(pixels(withLanes)).isEqualTo(pixels(render()));
    }

    @Test
    void trailRunsAlongTheRimWithGatesAtBothEnds() {
        BufferedImage before = render();
        bridgeNorthToSouth();
        BufferedImage after = render();

        int cx = PAD + TileGeometry.TILE_WIDTH / 2, cy = PAD + TileGeometry.TILE_HEIGHT / 2;
        assertThat(after.getRGB(cx, cy)).isEqualTo(before.getRGB(cx, cy));
        assertThat(changedPixels(before, after)).isGreaterThan(2000);
        assertThat(isReddish(after.getRGB(cx, PAD + 9))).isTrue();
        assertThat(isGreenish(after.getRGB(cx, PAD + TileGeometry.TILE_HEIGHT - 9)))
                .isTrue();
    }

    // Sides 1, 2, 4 and 5 face systems the bridged system still borders, so the rim trail must not read as a wall.
    @Test
    void trailTunnelsUnderEdgesFacingANeighbouringSystem() {
        bridgeNorthToSouth();
        BufferedImage walled = render();
        for (int side : List.of(1, 2, 4, 5)) game.setTile(new Tile("20", AROUND.get(side)));
        BufferedImage tunnelled = render();
        assertThat(changedPixels(walled, tunnelled)).isBetween(100, 3000);

        for (int side : List.of(1, 2, 4, 5)) game.addBorderAnomaly("000", side, "spatial_tear");
        assertThat(pixels(render())).isEqualTo(pixels(walled));
    }

    // A never-seen system is drawn from what the viewer knows: the two lanes leading to it, not its own art.
    @Test
    void neverSeenSystemShowsTheBridgeOnlyWhenBothLanesAreVisible() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        game.getTileByPosition(AROUND.get(0)).setTileFog(viewer, false);
        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, false);
        Tile unexplored = viewer.buildFogTile("000", viewer);

        BufferedImage plain = privateLayers(viewer).draw(unexplored, TileStep.Tile);
        bridgeNorthToSouth();
        assertThat(changedPixels(plain, privateLayers(viewer).draw(unexplored, TileStep.Tile)))
                .isGreaterThan(2000);

        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, true);
        assertThat(pixels(privateLayers(viewer).draw(unexplored, TileStep.Tile)))
                .isEqualTo(pixels(plain));
    }

    // A scene belongs to one render, so each game state gets its own.
    private TileLayers privateLayers(Player viewer) {
        return TileLayers.forPreparedViewer(game, new FogViewer(game, true, viewer), null);
    }

    @Test
    void removingTheBridgeRestoresTheArt() {
        BufferedImage plain = render();
        bridgeNorthToSouth();
        HyperlaneBridges.remove(game, "000");
        assertThat(pixels(render())).isEqualTo(pixels(plain));
    }

    private void bridgeNorthToSouth() {
        HyperlaneBridges.set(game, "000", "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
    }

    private BufferedImage render() {
        return TileLayers.of(game, null, null).draw(game.getTileByPosition("000"), TileStep.Tile);
    }

    private void oneWayHyperlane(String position, int from, int to) {
        int[][] lanes = new int[6][6];
        lanes[from][to] = 1;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static int changedPixels(BufferedImage a, BufferedImage b) {
        int[] pa = pixels(a), pb = pixels(b);
        int changed = 0;
        for (int i = 0; i < pa.length; i++) if (pa[i] != pb[i]) changed++;
        return changed;
    }

    private static boolean isReddish(int rgb) {
        return ((rgb >> 16) & 0xff) > ((rgb >> 8) & 0xff) + 80;
    }

    private static boolean isGreenish(int rgb) {
        return ((rgb >> 8) & 0xff) > ((rgb >> 16) & 0xff) + 80;
    }
}
