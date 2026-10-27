package ti4.image.playerarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import ti4.helpers.Constants;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.map.MapFrame;
import ti4.model.Mapper;
import ti4.service.transaction.DebtPoolIconService;
import ti4.testUtils.BaseTi4Test;

class DebtPoolIconTest extends BaseTi4Test {

    private static final String POOL = "favours";

    private Game game;
    private Player debtor;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("debt-pool-icon-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        debtor = seat("p1", "sol", "blue");
        Player creditor = seat("p2", "hacan", "yellow");
        debtor.addDebtTokens(creditor.getColor(), 2, POOL);
    }

    private Player seat(String userID, String faction, String color) {
        Player player = game.addPlayer(userID, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    private void drawDebtorArea() {
        DebtPoolIconService.assignMissingIcons(game);
        BufferedImage image = new BufferedImage(3000, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        PlayerAreas.drawSingle(
                PlayerAreaContext.publicView(graphics, game, new WebsiteOverlays()), debtor, new Point(5, 5), 3000);
        graphics.dispose();
    }

    // Before the fix an icon-less pool got a ThreadLocalRandom emoji on every render, so it changed each refresh.
    @Test
    void poolWithoutIconKeepsTheFirstIconItIsGiven() {
        drawDebtorArea();
        String firstIcon = game.getDebtPoolIcon(POOL);
        assertNotNull(firstIcon);

        drawDebtorArea();
        assertEquals(firstIcon, game.getDebtPoolIcon(POOL));
    }

    @Test
    void chosenIconIsTheSameForTheSameGameAndPool() {
        drawDebtorArea();
        String firstIcon = game.getDebtPoolIcon(POOL);

        setUp();
        drawDebtorArea();
        assertEquals(firstIcon, game.getDebtPoolIcon(POOL));
    }

    @Test
    void drawingAPoolWithoutAnIconDoesNotStoreOne() {
        BufferedImage image = new BufferedImage(3000, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        PlayerAreas.drawSingle(
                PlayerAreaContext.publicView(graphics, game, new WebsiteOverlays()), debtor, new Point(5, 5), 3000);
        graphics.dispose();

        assertNull(game.getDebtPoolIcon(POOL));
    }

    @Test
    void theDefaultPoolNeverGetsAnIconAssigned() {
        Player creditor = game.getPlayerFromColorOrFaction("hacan");
        debtor.addDebtTokens(creditor.getColor(), 1, Constants.DEBT_DEFAULT_POOL);

        DebtPoolIconService.assignMissingIcons(game);

        assertNull(game.getDebtPoolIcon(Constants.DEBT_DEFAULT_POOL));
        assertNotNull(game.getDebtPoolIcon(POOL));
    }
}
