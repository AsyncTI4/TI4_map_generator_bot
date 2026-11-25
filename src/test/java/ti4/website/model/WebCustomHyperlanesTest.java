package ti4.website.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class WebCustomHyperlanesTest extends BaseTi4Test {

    private static final String STRAIGHT = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
    private static final String ONE_WAY = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";

    @Test
    void exportsMatricesOnlyForCustomHyperlaneTiles() {
        Game game = new Game();
        game.setTile(new Tile("hl", "101"));
        game.setTile(new Tile("18", "102"));
        game.setTile(new Tile("hl", "103"));
        game.setTile(new Tile("hl", "104"));
        game.getCustomHyperlaneData().put("101", STRAIGHT);
        // stale data left behind on a tile that is no longer a custom hyperlane
        game.getCustomHyperlaneData().put("102", STRAIGHT);
        // data whose tile was removed
        game.getCustomHyperlaneData().put("999", STRAIGHT);
        // one-way lanes are stored as an asymmetric matrix and must reach the web unchanged
        game.getCustomHyperlaneData().put("104", ONE_WAY);

        assertThat(WebCustomHyperlanes.fromGame(game)).isEqualTo(Map.of("101", STRAIGHT, "104", ONE_WAY));
    }

    @Test
    void skipsMalformedMatrices() {
        Game game = new Game();
        game.setTile(new Tile("hl", "101"));
        game.getCustomHyperlaneData().put("101", "garbage");

        assertThat(WebCustomHyperlanes.fromGame(game)).isEmpty();
    }
}
