package ti4.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.ShowGameService.FogMapAccess;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.FogEvents;

class ShowGameFogAccessTest extends BaseTi4Test {

    private static final String PLAYER = "111";
    private static final String GM = "222";
    private static final String OUTSIDER = "999";

    private Game game;

    @BeforeEach
    void setUp() {
        Game fogGame = new Game();
        fogGame.newGameSetup();
        fogGame.setName("fogtest");
        fogGame.setFowMode(true);
        Player player = seat(fogGame, PLAYER, "sol", "blue");
        Player gm = seat(fogGame, GM, "hacan", "yellow");
        game = spy(fogGame);
        doReturn(List.of(gm)).when(game).getPlayersWithGMRole();

        // Only the regular player gets a resolvable private channel; it is what PLAYER_VIEW needs.
        JDA jda = mock(JDA.class);
        player.setPrivateChannelID("blue-private-id");
        when(jda.getTextChannelById("blue-private-id")).thenReturn(mock(TextChannel.class));
        JdaService.jda = jda;
    }

    @AfterEach
    void tearDown() {
        JdaService.jda = null;
    }

    private static Player seat(Game game, String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        return player;
    }

    private FogMapAccess access(String userId, String channelName) {
        return ShowGameService.fogMapAccess(game, FogEvents.eventIn(userId, channelName));
    }

    @Test
    void nonFogAndEndedGamesAreUnchanged() {
        game.setFowMode(false);
        assertEquals(FogMapAccess.AS_ASKED, access(OUTSIDER, "general"));

        game.setFowMode(true);
        game.setHasEnded(true);
        assertEquals(FogMapAccess.AS_ASKED, access(OUTSIDER, "general"));
    }

    @Test
    void outsideTheGameOnlyTheGmSeesTheMap() {
        assertEquals(FogMapAccess.FULL_MAP, access(GM, "general"));
        assertEquals(FogMapAccess.REFUSED, access(PLAYER, "general"));
        assertEquals(FogMapAccess.REFUSED, access(OUTSIDER, "general"));
        // Another fog game's private channel would otherwise count as a fogged view.
        assertEquals(FogMapAccess.REFUSED, access(PLAYER, "othergame-red-private"));
        assertEquals(FogMapAccess.FULL_MAP, access(GM, "othergame-red-private"));
    }

    @Test
    void insideTheGameKeepsTheExistingViews() {
        assertEquals(FogMapAccess.AS_ASKED, access(PLAYER, "fogtest-blue-private"));
        assertEquals(FogMapAccess.AS_ASKED, access(PLAYER, "fogtest-common-private"));
        assertEquals(FogMapAccess.AS_ASKED, access(GM, "fogtest-yellow-private"));
        assertEquals(FogMapAccess.AS_ASKED, access(GM, "fogtest-gm-room"));
    }

    @Test
    void gmRoomShowsTheMapWhoeverAsks() {
        // GM detection depends on a "<game> GM" role that can be missing; the room itself is GM-only.
        assertEquals(FogMapAccess.AS_ASKED, access(OUTSIDER, "fogtest-gm-room"));
    }

    @Test
    void customNamedPrivateChannelCountsAsInsideTheGame() {
        // /game setup_channels can assign any text channel; it is recognised by ID, and since its name
        // lacks "-private" the player still gets their own vision instead of the full map.
        GenericInteractionCreateEvent event = FogEvents.eventIn(PLAYER, "hideout");
        when(event.getChannel().getId()).thenReturn("blue-private-id");
        assertEquals(FogMapAccess.PLAYER_VIEW, ShowGameService.fogMapAccess(game, event));

        assertEquals(FogMapAccess.REFUSED, access(PLAYER, "hideout"));
    }

    @Test
    void otherGameChannelsRenderThePlayersOwnVision() {
        assertEquals(FogMapAccess.PLAYER_VIEW, access(PLAYER, "fogtest-lobby"));
        assertEquals(FogMapAccess.REFUSED, access(OUTSIDER, "fogtest-lobby"));
    }

    @Test
    void playerViewEventsAreNeverRedirected() {
        GenericInteractionCreateEvent event = mock(UserOverridenGenericInteractionCreateEvent.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(OUTSIDER);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(mock(Member.class));

        assertEquals(FogMapAccess.AS_ASKED, ShowGameService.fogMapAccess(game, event));
    }
}
