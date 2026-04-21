package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import net.dv8tion.jda.api.requests.RestAction;
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

    @Test
    void getCardsInfoThreadJumpLinkReturnsNullWithoutCreatingThreadWhenArchivedThreadLookupLacksAccess() {
        JdaService.testingMode = true;

        JDA jda = mock(JDA.class);
        JdaService.jda = jda;

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
