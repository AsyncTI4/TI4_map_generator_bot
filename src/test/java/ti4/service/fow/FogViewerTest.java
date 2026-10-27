package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.FogEvents;

class FogViewerTest extends BaseTi4Test {

    private Game game;
    private Player first;
    private Player second;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("fog-viewer-test");
        game.setFowMode(true);
        first = seat("111", "sol", "blue");
        second = seat("222", "hacan", "yellow");
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    @Test
    void channelEndingInPrivateIsAPrivateViewOfTheEventPlayer() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventIn("111", "fog-111-private"), null);

        assertTrue(viewer.privateView());
        assertSame(first, viewer.player());
    }

    @Test
    void ordinaryChannelIsNotPrivateButStillKnowsThePlayer() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventIn("111", "game-actions"), null);

        assertFalse(viewer.privateView());
        assertSame(first, viewer.player());
    }

    @Test
    void threadUnderAPrivateChannelIsPrivate() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventInThreadUnder("111", "fog-111-private"), null);

        assertTrue(viewer.privateView());
    }

    @Test
    void threadUnderAnOrdinaryChannelIsNotPrivate() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventInThreadUnder("111", "game-actions"), null);

        assertFalse(viewer.privateView());
    }

    @Test
    void userOverriddenEventIsPrivateInAnyChannel() {
        GenericInteractionCreateEvent event =
                org.mockito.Mockito.mock(UserOverridenGenericInteractionCreateEvent.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn("222");
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(member);

        FogViewer viewer = FogViewer.resolve(game, event, null);

        assertTrue(viewer.privateView());
        assertSame(second, viewer.player());
    }

    @Test
    void nonFogGameIsNeverPrivate() {
        game.setFowMode(false);

        assertFalse(FogViewer.resolve(game, FogEvents.eventIn("111", "fog-111-private"), null)
                .privateView());
    }

    @Test
    void nullEventIsUnfoggedWithoutAViewer() {
        FogViewer viewer = FogViewer.resolve(game, null, null);

        assertFalse(viewer.privateView());
        assertNull(viewer.player());
    }

    @Test
    void explicitViewerWinsOverTheEventPlayer() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventIn("111", "fog-111-private"), second);

        assertSame(second, viewer.player());
    }

    @Test
    void privateViewFromANonPlayerHasNoViewerAndNoKnowledge() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventIn("999", "gm-private"), null);

        assertTrue(viewer.privateView());
        assertNull(viewer.player());
        assertFalse(viewer.canSeeStats(first));
        assertFalse(viewer.seesHomeSystem(first));
        assertFalse(viewer.canSeeStatsOfFaction("sol"));
        assertEquals(0, viewer.visibleSystems().size());
        assertEquals(0, viewer.positionsInReach().size());
    }

    @Test
    void viewerCanSeeItsOwnStats() {
        FogViewer viewer = FogViewer.resolve(game, FogEvents.eventIn("111", "fog-111-private"), null);

        assertTrue(viewer.canSeeStats(first));
        assertFalse(viewer.canSeeStats(null));
    }
}
