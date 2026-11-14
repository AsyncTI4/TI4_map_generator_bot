package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FogViewer;
import ti4.service.map.HyperlanePortals;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

// A roundabout at 000 with spokes N, SE and SW.
class PortalTokensTest extends BaseTi4Test {

    private static final String ROUNDABOUT = "1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0";
    private static final int PAD = TileGeometry.TILE_PADDING;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("hl", "000"));
        game.getCustomHyperlaneData().put("000", ROUNDABOUT);
    }

    @Test
    void tokensAppearAtTheirSlots() {
        BufferedImage plain = render();
        HyperlanePortals.set(
                game, "000", HyperlanePortals.parseTyped("0=A, 3=B").slots());
        BufferedImage withPortals = render();
        Point2D slot3 = PortalTokens.slotPoint(3, 104);
        int x = PAD + (int) slot3.getX(), y = PAD + (int) slot3.getY();
        assertThat(withPortals.getRGB(x, y)).isNotEqualTo(plain.getRGB(x, y));
        assertThat(withPortals.getRGB(PAD + 172, PAD + 150)).isNotEqualTo(plain.getRGB(PAD + 172, PAD + 150));
    }

    @Test
    void everyLetterHasItsOwnColour() {
        Set<Integer> colours = new HashSet<>();
        for (char letter = 'A'; letter <= 'Z'; letter++)
            colours.add(PortalTokens.colourOf(letter).getRGB());
        assertThat(colours).hasSize(26);
    }

    // An entry-only slot carries the green lane arrow halfway along its connector.
    @Test
    void oneWaySlotsShowAnArrow() {
        HyperlanePortals.set(
                game, "000", HyperlanePortals.parseTyped("0=A, 1=C>").slots());
        Point2D middle = PortalTokens.slotPoint(1, (60 + 104 - 21) / 2.0);
        Color pixel = new Color(render().getRGB(PAD + (int) middle.getX(), PAD + (int) middle.getY()));
        assertThat(pixel.getGreen()).isGreaterThan(pixel.getRed() + 60);
    }

    // A one-way portal in the middle has no connector, so its arrow sits between the ring and the token.
    @Test
    void aOneWayCentrePortalShowsItsArrowAboveTheToken() {
        HyperlanePortals.set(game, "000", HyperlanePortals.parseTyped("C>").slots());
        Color pixel = new Color(render().getRGB(PAD + 172, PAD + 150 - 43));
        assertThat(pixel.getGreen()).isGreaterThan(pixel.getRed() + 60);
    }

    // With "Hide unseen hyperlanes", a player whose vision never went through the portal doesn't see its token.
    @Test
    void hiddenFromPlayersWhoHaventSeenThroughThePortal() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES, true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        game.getTileByPosition("000").setTileFog(viewer, false);
        BufferedImage plain = privateRender(viewer);
        HyperlanePortals.set(game, "000", HyperlanePortals.parseTyped("0=A").slots());
        BufferedImage withPortal = privateRender(viewer);
        assertThat(withPortal.getRGB(PAD + 172, PAD + 150)).isEqualTo(plain.getRGB(PAD + 172, PAD + 150));
    }

    private BufferedImage render() {
        return TileLayers.of(game, null, null).draw(game.getTileByPosition("000"), TileStep.Tile);
    }

    private BufferedImage privateRender(Player viewer) {
        return TileLayers.forPreparedViewer(game, new FogViewer(game, true, viewer), null)
                .draw(game.getTileByPosition("000"), TileStep.Tile);
    }
}
