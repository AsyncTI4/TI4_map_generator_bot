package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.testUtils.BaseTi4Test;

class ManagedGameTest extends BaseTi4Test {

    @Test
    void guildComesFromTheMainGameChannelWhenNoGuildIdIsStored() {
        // Game files never store a guild id, so Game.getGuild() derives it from the channels and so must this.
        Guild guild = mock(Guild.class);
        TextChannel mainGameChannel = mock(TextChannel.class);
        when(mainGameChannel.getGuild()).thenReturn(guild);
        JDA jda = mock(JDA.class);
        when(jda.getTextChannelById("222")).thenReturn(mainGameChannel);
        JdaService.jda = jda;

        ManagedGame managedGame = new ManagedGame(state(null, "222", "333", uniqueUserId()));

        assertThat(managedGame.getMainGameChannel()).isSameAs(mainGameChannel);
        assertThat(managedGame.getTableTalkChannel()).isNull();
        assertThat(managedGame.getGuild()).isSameAs(guild);
    }

    @Test
    void guildFallsBackToTheTableTalkChannel() {
        Guild guild = mock(Guild.class);
        TextChannel tableTalkChannel = mock(TextChannel.class);
        when(tableTalkChannel.getGuild()).thenReturn(guild);
        JDA jda = mock(JDA.class);
        when(jda.getTextChannelById("333")).thenReturn(tableTalkChannel);
        JdaService.jda = jda;

        ManagedGame managedGame = new ManagedGame(state(null, "222", "333", uniqueUserId()));

        assertThat(managedGame.getGuild()).isSameAs(guild);
    }

    @Test
    void storedGuildIdWinsOverTheChannels() {
        Guild storedGuild = mock(Guild.class);
        TextChannel mainGameChannel = mock(TextChannel.class);
        when(mainGameChannel.getGuild()).thenReturn(mock(Guild.class));
        JDA jda = mock(JDA.class);
        when(jda.getGuildById("111")).thenReturn(storedGuild);
        when(jda.getTextChannelById("222")).thenReturn(mainGameChannel);
        JdaService.jda = jda;

        ManagedGame managedGame = new ManagedGame(state("111", "222", null, uniqueUserId()));

        assertThat(managedGame.getGuild()).isSameAs(storedGuild);
    }

    @Test
    void participantsBecomeManagedPlayers() {
        String userId = uniqueUserId();

        ManagedGame managedGame = new ManagedGame(state(null, null, null, userId));

        assertThat(managedGame.getPlayerIds()).containsExactly(userId);
        assertThat(managedGame.getRealPlayers())
                .extracting(ManagedPlayer::getName)
                .containsExactly("Someone");
        assertThat(managedGame.getPlayer(userId).getGames()).contains(managedGame);
    }

    private static ManagedGameState state(
            String guildId, String mainGameChannelId, String tableTalkChannelId, String userId) {
        return new ManagedGameState(
                "managed-game-" + UUID.randomUUID(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                null,
                0,
                0,
                1,
                guildId,
                mainGameChannelId,
                tableTalkChannelId,
                null,
                List.of(new ManagedGameState.Participant(userId, "Someone", true)));
    }

    private static String uniqueUserId() {
        return "managed-game-user-" + UUID.randomUUID();
    }
}
