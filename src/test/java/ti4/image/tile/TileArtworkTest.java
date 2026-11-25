package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FogViewer;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class TileArtworkTest extends BaseTi4Test {

    // With "Hide unseen hyperlanes", a remembered hyperlane must not change when the GM edits or clears it out of
    // sight.
    @Test
    void rememberedHyperlaneLooksTheSameWhateverItsCurrentData() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("tile-artwork-test");
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES, true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        Tile tile = new Tile("hl", "101");
        game.setTile(tile);
        tile.setTileFog(viewer, true);
        TileScene scene = new TileScene(game, null, new FogViewer(game, true, viewer), new RenderWarnings());

        CustomHyperlaneService.insertData(
                game, "101", "0,0,0,2,0,0;0,0,0,0,1,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        int[] before = pixels(TileArtwork.hyperlaneImage(scene, tile));
        CustomHyperlaneService.insertData(
                game, "101", "0,0,1,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        assertThat(pixels(TileArtwork.hyperlaneImage(scene, tile))).isEqualTo(before);
        game.getCustomHyperlaneData().remove("101");
        assertThat(pixels(TileArtwork.hyperlaneImage(scene, tile))).isEqualTo(before);
    }

    private static int[] pixels(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
    }
}
