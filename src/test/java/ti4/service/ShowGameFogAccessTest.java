package ti4.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
import ti4.service.fow.MapSegmentService;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.FogEvents;

class ShowGameFogAccessTest extends BaseTi4Test {

    private static final String PLAYER = "111";
    private static final String GM = "222";
    private static final String SEATLESS_GM = "333";
    private static final String NO_PRIVATE_CHANNEL = "444";
    private static final String OUTSIDER = "999";

    private Game game;
    private Guild gameGuild;
    private Guild otherGuild;

    @BeforeEach
    void setUp() {
        Game fogGame = new Game();
        fogGame.newGameSetup();
        fogGame.setName("fogtest");
        fogGame.setFowMode(true);
        Player player = seat(fogGame, PLAYER, "sol", "blue");
        Player gm = seat(fogGame, GM, "hacan", "yellow");
        Player seatlessGm = fogGame.addPlayer(SEATLESS_GM, "seatless-gm");
        seat(fogGame, NO_PRIVATE_CHANNEL, "xxcha", "green");
        game = spy(fogGame);
        doReturn(List.of(gm, seatlessGm)).when(game).getPlayersWithGMRole();
        gameGuild = guild("game-guild");
        otherGuild = guild("other-guild");
        doReturn(gameGuild).when(game).getGuild();

        // Seated players need a resolvable private channel for any view inside the game; green has none.
        JDA jda = mock(JDA.class);
        player.setPrivateChannelID("blue-private-id");
        gm.setPrivateChannelID("yellow-private-id");
        when(jda.getTextChannelById("blue-private-id")).thenReturn(mock(TextChannel.class));
        when(jda.getTextChannelById("yellow-private-id")).thenReturn(mock(TextChannel.class));
        JdaService.jda = jda;
    }

    private static Guild guild(String id) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        return guild;
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
        return ShowGameService.fogMapAccess(game, eventIn(userId, channelName, gameGuild));
    }

    private FogMapAccess accessFromOtherGuild(String userId, String channelName) {
        return ShowGameService.fogMapAccess(game, eventIn(userId, channelName, otherGuild));
    }

    private static GenericInteractionCreateEvent eventIn(String userId, String channelName, Guild guild) {
        GenericInteractionCreateEvent event = FogEvents.eventIn(userId, channelName);
        when(event.getGuild()).thenReturn(guild);
        return event;
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
    void sameNamedChannelsInAnotherGuildAreNotTheGame() {
        // /show_game takes any game name, so anyone can name a channel "fogtest-gm-room" on another server.
        assertEquals(FogMapAccess.REFUSED, accessFromOtherGuild(OUTSIDER, "fogtest-gm-room"));
        assertEquals(FogMapAccess.REFUSED, accessFromOtherGuild(PLAYER, "fogtest-blue-private"));
        assertEquals(FogMapAccess.FULL_MAP, accessFromOtherGuild(GM, "fogtest-gm-room"));
    }

    @Test
    void customNamedPrivateChannelCountsAsInsideTheGame() {
        // /game setup_channels can assign any text channel; it is recognised by ID, and since its name
        // lacks "-private" the player still gets their own vision instead of the full map.
        GenericInteractionCreateEvent event = eventIn(PLAYER, "hideout", gameGuild);
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
    void seatedGmGetsTheirOwnVisionInsideTheGame() {
        assertEquals(FogMapAccess.PLAYER_VIEW, access(GM, "fogtest-lobby"));
        GenericInteractionCreateEvent ownChannel = eventIn(GM, "gm-hideout", gameGuild);
        when(ownChannel.getChannel().getId()).thenReturn("yellow-private-id");
        assertEquals(FogMapAccess.PLAYER_VIEW, ShowGameService.fogMapAccess(game, ownChannel));
    }

    @Test
    void insideTheGameOnlySeatsWithAPrivateChannelSeeAMap() {
        String commonChannel = "fogtest-anonymous-announcements-private";
        assertEquals(FogMapAccess.REFUSED, access(OUTSIDER, commonChannel));
        assertEquals(FogMapAccess.REFUSED, access(SEATLESS_GM, commonChannel));
        assertEquals(FogMapAccess.FULL_MAP, access(SEATLESS_GM, "general"));
        // Nothing could redirect this player's vision out of the shared channel.
        assertEquals(FogMapAccess.REFUSED, access(NO_PRIVATE_CHANNEL, commonChannel));
    }

    @Test
    void communityRoleMembersActForTheirTeamSeat() {
        // Community seats are bound to a Discord role; the members pressing buttons are not the seat's user.
        Player team = seat(game, "555", "letnev", "orange");
        team.setRoleIDForCommunity("team-role-id");
        team.setPrivateChannelID("orange-private-id");
        Role teamRole = mock(Role.class);
        when(JdaService.jda.getRoleById("team-role-id")).thenReturn(teamRole);
        when(JdaService.jda.getTextChannelById("orange-private-id")).thenReturn(mock(TextChannel.class));
        game.setCommunityMode(true);

        assertEquals(
                FogMapAccess.AS_ASKED,
                ShowGameService.fogMapAccess(game, teamMemberIn("fogtest-orange-private", teamRole)));
        assertEquals(
                FogMapAccess.PLAYER_VIEW, ShowGameService.fogMapAccess(game, teamMemberIn("fogtest-lobby", teamRole)));
        assertEquals("555", MapSegmentService.viewerId(game, teamMemberIn("fogtest-lobby", teamRole)));
        assertEquals(FogMapAccess.REFUSED, access(OUTSIDER, "fogtest-lobby"));
    }

    private GenericInteractionCreateEvent teamMemberIn(String channelName, Role role) {
        GenericInteractionCreateEvent event = eventIn("666", channelName, gameGuild);
        // Seats without a community role compare against null, which List.of() rejects but JDA's list allows.
        when(event.getMember().getRoles()).thenReturn(Collections.singletonList(role));
        return event;
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
