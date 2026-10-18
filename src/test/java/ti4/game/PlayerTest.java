package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.exceptions.MissingAccessException;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;

class PlayerTest {

    private final PrintStream originalSystemOut = System.out;

    @AfterEach
    void afterEach() {
        JdaService.jda = null;
        JdaService.testingMode = false;
        System.setOut(originalSystemOut);
    }

    private static JDA installMockJda() {
        JDA jda = mock(JDA.class);
        // GameManager's async warmup, started by other test classes, can finish while this mock is
        // installed. It sets the bot's presence when it does, so getPresence must not return null.
        when(jda.getPresence()).thenReturn(mock(Presence.class));
        JdaService.jda = jda;
        return jda;
    }

    @Test
    void getCardsInfoThreadJumpLinkReturnsNullWithoutCreatingThreadWhenArchivedThreadLookupLacksAccess() {
        JdaService.testingMode = true;

        JDA jda = installMockJda();

        Game game = new Game();
        game.setName("old-game");
        game.setMainChannelID("actions-channel");

        Player player = new Player("user-id", "user/name", game);

        TextChannel actionsChannel = mock(TextChannel.class);
        Guild guild = mock(Guild.class);
        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> activeThreads = mock(RestAction.class);
        ThreadChannelPaginationAction archivedPrivateThreads = mock(ThreadChannelPaginationAction.class);

        when(jda.getTextChannelById("actions-channel")).thenReturn(actionsChannel);
        when(actionsChannel.getThreadChannels()).thenReturn(List.of());
        when(actionsChannel.getGuild()).thenReturn(guild);
        when(guild.getThreadChannelsByName("Cards Info-old-game-username", true))
                .thenReturn(List.of());
        when(guild.retrieveActiveThreads()).thenReturn(activeThreads);
        when(activeThreads.complete()).thenReturn(List.of());
        RuntimeException archivedLookupFailure = mock(MissingAccessException.class);
        when(actionsChannel.retrieveArchivedPrivateThreadChannels()).thenReturn(archivedPrivateThreads);
        when(archivedPrivateThreads.complete()).thenThrow(archivedLookupFailure);

        assertThat(player.getCardsInfoThreadJumpLink()).isNull();
        verify(actionsChannel, never()).createThreadChannel(anyString(), anyBoolean());
    }

    @Test
    void createCardsInfoThreadCreatesPrivateThreadWithoutLookupOrGreeting() {
        JdaService.testingMode = true;

        JDA jda = installMockJda();

        Game game = new Game();
        game.setName("new-game");
        game.setMainChannelID("actions-channel");

        Player player = new Player("user-id", "user/name", game);

        TextChannel actionsChannel = mock(TextChannel.class);
        ThreadChannelAction createThread = mock(ThreadChannelAction.class, RETURNS_SELF);
        ThreadChannel thread = mock(ThreadChannel.class);

        when(jda.getTextChannelById("actions-channel")).thenReturn(actionsChannel);
        when(actionsChannel.createThreadChannel("Cards Info-new-game-username", true))
                .thenReturn(createThread);
        when(createThread.complete()).thenReturn(thread);
        when(thread.getId()).thenReturn("thread-id");

        assertThat(player.createCardsInfoThread()).isSameAs(thread);
        assertThat(player.getCardsInfoThreadID()).isEqualTo("thread-id");
        verify(createThread).setInvitable(false);

        // A brand-new game has no thread to find, so the guild-wide lookups must be skipped.
        verify(actionsChannel, never()).getGuild();
        verify(actionsChannel, never()).retrieveArchivedPrivateThreadChannels();

        // The greeting pings the player, so game launch sends it only after they hold the game role.
        verify(thread).getId();
        verifyNoMoreInteractions(thread);
    }

    @Test
    void getCardsInfoThreadJumpLinkDoesNotWarnWhenEndedGameHasNoActionsChannel() {
        JdaService.testingMode = true;

        Game game = new Game();
        game.setName("old-game");
        game.setHasEnded(true);

        Player player = new Player("user-id", "user/name", game);
        ByteArrayOutputStream capturedStdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdout, true, StandardCharsets.UTF_8));

        assertThat(player.getCardsInfoThreadJumpLink()).isNull();
        assertThat(capturedStdout.toString(StandardCharsets.UTF_8)).doesNotContain("Player.getCardsInfoThread");
    }
}
