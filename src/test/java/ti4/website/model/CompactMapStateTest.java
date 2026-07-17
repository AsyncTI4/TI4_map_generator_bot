package ti4.website.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class CompactMapStateTest extends BaseTi4Test {

    @Test
    void serializesUnknownTokenWithNullEntityId() {
        Game game = new Game();
        Tile tile = new Tile("18", "000");
        tile.getSpaceUnitHolder().addToken("unknown-token.png");
        game.setTile(tile);

        String serialized = CompactMapState.serialize(game);

        assertThat(serialized).contains("[\"t\",null,1]");
    }
}
