package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.RenderRandom;
import ti4.model.Mapper;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.testUtils.BaseTi4Test;

class FogPlayerAreasHeightTest extends BaseTi4Test {

    private static final int MIN_PLAYER_AREA_HEIGHT = 300;
    private static final int MAX_PLAYER_AREA_HEIGHT = 420;
    private static final int GENEROUS_TAIL = 80;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("fog-player-areas-test");
        game.setFowMode(true);
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        addSeat("p1", "sol", "blue");
        addSeat("p2", "hacan", "yellow");
        addSeat("p3", "jolnar", "purple");
        addSeat("p4", "letnev", "red");
    }

    private void addSeat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
    }

    // A fog player sees only their own stats, so the three hidden player areas must not leave empty canvas.
    @Test
    void fogPlayerViewOnlyKeepsRoomForPlayerAreasItDraws() {
        BufferedImage unfogged = paintStats(null);
        BufferedImage asSol = paintStats(privateViewOf("p1"));

        int saved = unfogged.getHeight() - asSol.getHeight();
        assertTrue(saved >= 3 * MIN_PLAYER_AREA_HEIGHT, "three hidden areas were dropped, saved " + saved);
        assertTrue(saved <= 3 * MAX_PLAYER_AREA_HEIGHT, "only the three hidden areas were dropped, saved " + saved);
    }

    @Test
    void fogPlayerViewEndsJustBelowTheOneAreaItDraws() {
        BufferedImage asSol = paintStats(privateViewOf("p1"));

        int tail = asSol.getHeight() - 1 - lastNonBlackRow(asSol);
        assertTrue(tail >= 0 && tail < GENEROUS_TAIL, "tail below the last drawn pixel was " + tail);
    }

    private BufferedImage paintStats(UserOverridenGenericInteractionCreateEvent event) {
        try (MockedStatic<DateTimeHelper> dateTime = mockStatic(DateTimeHelper.class, Mockito.CALLS_REAL_METHODS);
                AutoCloseable random = RenderRandom.neutral()) {
            dateTime.when(DateTimeHelper::getFormattedTimestamp).thenReturn("2000.01.01 - 00.00.00");
            return MapRenderer.paint(
                    MapRenderer.resolve(new MapRenderRequest(game, DisplayType.stats, event, null)),
                    RenderTimings.disabled());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int lastNonBlackRow(BufferedImage image) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    return y;
                }
            }
        }
        return -1;
    }

    private static UserOverridenGenericInteractionCreateEvent privateViewOf(String userId) {
        UserOverridenGenericInteractionCreateEvent event =
                mock(UserOverridenGenericInteractionCreateEvent.class, RETURNS_DEEP_STUBS);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(member);
        when(event.getMessageChannel().getName()).thenReturn("fog-" + userId + "-private");
        when(event.getChannel().getName()).thenReturn("fog-" + userId + "-private");
        return event;
    }
}
