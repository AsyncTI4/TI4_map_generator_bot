package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.testUtils.BaseTi4Test;

class FogPlayerAreasHeightTest extends BaseTi4Test {

    // Matches MapGenerator's per-player estimate for a seat with no teammates and fewer than 4 secrets.
    private static final int PLAYER_AREA_HEIGHT = 340;

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
    void fogPlayerViewOnlyReservesRoomForPlayerAreasItDraws() {
        int unfogged = statsHeight(null);
        int asSol = statsHeight(privateViewOf("p1"));

        assertEquals(unfogged - 3 * PLAYER_AREA_HEIGHT, asSol);
    }

    @Test
    void eliminatedHiddenPlayersGiveBackTheirSmallerArea() {
        game.getPlayer("p4").setEliminated(true);
        int unfogged = statsHeight(null);
        int asSol = statsHeight(privateViewOf("p1"));

        // Two hidden live seats plus one eliminated seat, whose area is 190 shorter.
        assertEquals(unfogged - 2 * PLAYER_AREA_HEIGHT - (PLAYER_AREA_HEIGHT - 190), asSol);
    }

    private int statsHeight(UserOverridenGenericInteractionCreateEvent event) {
        try (MapGenerator generator = new MapGenerator(game, DisplayType.stats, event, null)) {
            return generator.imageHeight();
        }
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
