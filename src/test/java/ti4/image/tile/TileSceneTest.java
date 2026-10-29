package ti4.image.tile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FogViewer;
import ti4.testUtils.BaseTi4Test;

// seesPosition decides which neighbours fog feature views and control borders may look at.
class TileSceneTest extends BaseTi4Test {

    private Game game;
    private Player viewer;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("tile-scene-test");
        game.setFowMode(true);
        viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        Tile seen = new Tile("19", "000");
        Tile hidden = new Tile("20", "101");
        game.setTile(seen);
        game.setTile(hidden);
        seen.setTileFog(viewer, false);
        hidden.setTileFog(viewer, true);
    }

    @Test
    void privateViewSeesOnlyUnfoggedPositions() {
        TileScene scene = new TileScene(game, null, new FogViewer(game, true, viewer), new RenderWarnings());

        assertTrue(scene.seesPosition("000"));
        assertFalse(scene.seesPosition("101"));
        assertFalse(scene.seesPosition("102"));
    }

    @Test
    void unfoggedViewSeesEverything() {
        TileScene scene = new TileScene(game, null, FogViewer.unfogged(game), new RenderWarnings());

        assertTrue(scene.seesPosition("101"));
    }
}
