package ti4.image.playerarea;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.map.MapFrame;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class UnknownTechPlayerAreaTest extends BaseTi4Test {

    // baldrick_ff2 is a real id that was deleted from the Ignis Aurora tech file (#2799) while games still held it.
    private static final String REMOVED_TECH = "baldrick_ff2";

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("unknown-tech-player-area-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        player = game.addPlayer("p1", "sol");
        player.setFaction(game, "sol");
        player.setColor("blue");
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction("sol").getUnits()));
    }

    @Test
    void removedTechIdReallyHasNoModel() {
        assertNull(Mapper.getTech(REMOVED_TECH));
    }

    // Player.addTech refuses unknown ids, so the list is edited directly the way loading a save fills it.
    @Test
    void playerAreaRendersWhenPlayerHoldsUnknownTechs() {
        player.getTechs().add("gd");
        player.getTechs().add(REMOVED_TECH);
        player.getPurgedTechs().add(REMOVED_TECH);
        player.getFactionTechs().add(REMOVED_TECH);
        game.setStoredValue("zealotsHeroPurged", "true");

        assertDoesNotThrow(this::drawPlayerArea);
    }

    private void drawPlayerArea() {
        int width = 3000;
        BufferedImage image = new BufferedImage(width, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        PlayerAreas.drawSingle(
                PlayerAreaContext.publicView(graphics, game, new WebsiteOverlays()), player, new Point(5, 5), width);
    }
}
