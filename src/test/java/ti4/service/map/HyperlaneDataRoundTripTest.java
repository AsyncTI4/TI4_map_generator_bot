package ti4.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

// One map with every kind of hyperlane data: a straight one-way hyperlane (101), a roundabout with portals (102),
// an empty hyperlane used as a portal interchange (103), and a bridged system (000). Both ways out and back in must
// bring all of it back.
class HyperlaneDataRoundTripTest extends BaseTi4Test {

    private static final String ONE_WAY = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
    private static final String ROUNDABOUT = "1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0";
    private static final String BRIDGE_TYPED =
            "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";

    private Game game;

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
        game = mapWithEveryKind("round-trip-source");
    }

    @Test
    void managerExportAndImportBringEverythingBack() {
        String exported = CustomHyperlaneService.exportHyperlaneText(game);
        Game copy = tilesOnly("round-trip-text");
        CustomHyperlaneService.importHyperlaneData(copy, exported);
        assertSameHyperlaneData(copy);
    }

    @Test
    void mapJsonExportAndImportBringEverythingBack() {
        String json = MapJsonIOService.exportMapAsJson(null, game, false, false, false);
        Game copy = new Game();
        copy.setName("round-trip-json");
        MapJsonIOService.importMapFromJson(copy, json, null);
        assertSameHyperlaneData(copy);
    }

    private void assertSameHyperlaneData(Game copy) {
        assertThat(copy.getCustomHyperlaneData()).isEqualTo(game.getCustomHyperlaneData());
        assertThat(HyperlaneBridges.all(copy)).isEqualTo(HyperlaneBridges.all(game));
        assertThat(HyperlanePortals.all(copy)).isEqualTo(HyperlanePortals.all(game));
    }

    private static Game mapWithEveryKind(String name) {
        Game game = tilesOnly(name);
        game.getCustomHyperlaneData().put("101", ONE_WAY);
        game.getCustomHyperlaneData().put("102", ROUNDABOUT);
        HyperlaneBridges.set(game, "000", BRIDGE_TYPED);
        HyperlanePortals.set(
                game, "102", HyperlanePortals.parseTyped("A, 3=B>, C<").slots());
        HyperlanePortals.set(game, "103", HyperlanePortals.parseTyped("A, 5=D").slots());
        return game;
    }

    private static Game tilesOnly(String name) {
        Game game = new Game();
        game.newGameSetup();
        game.setName(name);
        game.setTile(new Tile("19", "000"));
        game.setTile(new Tile("hl", "101"));
        game.setTile(new Tile("hl", "102"));
        game.setTile(new Tile("hl", "103"));
        return game;
    }
}
