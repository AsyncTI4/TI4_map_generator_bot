package ti4.image.playerarea;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.map.MapFrame;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class PlayerAreaWarningsTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private List<Tile> tiles;
    private MockedStatic<MessageHelper> messages;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("player-area-warnings-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        tiles = new ArrayList<>(game.getTileMap().values());
        player = game.addPlayer("p1", "sol");
        player.setFaction(game, "sol");
        player.setColor("blue");
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction("sol").getUnits()));
        player.addAbility("incursion");
        messages = mockStatic(MessageHelper.class, Mockito.CALLS_REAL_METHODS);
        messages.when(() -> MessageHelper.sendMessageToChannel(Mockito.<MessageChannel>any(), anyString()))
                .then(invocation -> null);
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void placeBreaches(int count) {
        for (int i = 0; i < tiles.size(); i++) {
            if (i < count) {
                tiles.get(i).getSpaceUnitHolder().addToken(Constants.TOKEN_BREACH_ACTIVE);
            } else {
                tiles.get(i).getSpaceUnitHolder().removeToken(Constants.TOKEN_BREACH_ACTIVE);
            }
        }
    }

    // Mirrors MapRenderPipeline: draw the areas, then send what the render reported.
    private void renderAndSend() {
        BufferedImage image = new BufferedImage(3000, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        PlayerAreaContext ctx = PlayerAreaContext.publicView(graphics, game, new WebsiteOverlays());
        PlayerAreas.drawSingle(ctx, player, new Point(5, 5), 3000);
        graphics.dispose();
        ctx.warnings().send(game);
    }

    private void assertBreachWarningsSent(int expected) {
        messages.verify(
                () -> MessageHelper.sendMessageToChannel(any(), Mockito.contains("too many Breach tokens")),
                times(expected));
    }

    @Test
    void drawingOnlyReportsAndSendingHappensOnce() {
        placeBreaches(8);

        BufferedImage image = new BufferedImage(3000, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        PlayerAreas.drawSingle(
                PlayerAreaContext.publicView(graphics, game, new WebsiteOverlays()), player, new Point(5, 5), 3000);
        graphics.dispose();
        assertBreachWarningsSent(0);

        renderAndSend();
        assertBreachWarningsSent(1);
    }

    @Test
    void unchangedWarningIsNotRepeatedOnTheNextRender() {
        placeBreaches(8);
        renderAndSend();
        renderAndSend();
        renderAndSend();

        assertBreachWarningsSent(1);
    }

    @Test
    void changedCountWarnsAgain() {
        placeBreaches(8);
        renderAndSend();
        placeBreaches(9);
        renderAndSend();

        assertBreachWarningsSent(2);
    }

    @Test
    void resolvedWarningWarnsAgainWhenItComesBack() {
        placeBreaches(8);
        renderAndSend();
        placeBreaches(3);
        renderAndSend();
        placeBreaches(8);
        renderAndSend();

        assertBreachWarningsSent(2);
    }
}
