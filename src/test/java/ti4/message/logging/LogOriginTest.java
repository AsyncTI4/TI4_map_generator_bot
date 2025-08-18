package ti4.message.logging;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.helpers.DateTimeHelper;
import ti4.map.Game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class LogOriginTest {

    private final Guild guild = mock(Guild.class);
    private final GuildChannel channel = mock(GuildChannel.class);
    private final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    private final Game game = mock(Game.class);

    @BeforeEach
    void beforeEach() {
        when(event.isFromGuild()).thenReturn(true);
        User user = mock(User.class);
        when(user.getEffectiveName()).thenReturn("Tester");
        when(event.getUser()).thenReturn(user);
        when(event.getCommandString()).thenReturn("/ping");
        when(guild.getId()).thenReturn("1");
        when(event.getGuild()).thenReturn(guild);
        when(channel.getGuild()).thenReturn(guild);
        when(game.getGuild()).thenReturn(guild);
        when(game.gameJumpLinks()).thenReturn("TestGame [tt] [act]");
    }

    @Test
    void logStringContainsPreformattedEventAndGameInfo() {
        try (MockedStatic<DateTimeHelper> mocked = mockStatic(DateTimeHelper.class)) {
            mocked.when(DateTimeHelper::getCurrentTimestamp).thenReturn("`timestamp`");

            LogOrigin origin = new LogOrigin(event, game);
            String log = new TestEventLog(origin).getLogString();

            String expected =
                """
                **__`timestamp`__** Tester used command `/ping`
                
                Game info: TestGame [tt] [act]
                """;
            assertThat(log).isEqualTo(expected);
        }
    }

    private static class TestEventLog extends AbstractEventLog {

        TestEventLog(LogOrigin source) {
            super(source);
        }

        TestEventLog(LogOrigin source, String message) {
            super(source, message);
        }

        @Override
        public String getChannelName() {
            return "channelName";
        }

        @Override
        public String getThreadName() {
            return "threadName";
        }

        @Override
        public String getMessagePrefix() {
            return "messagePrefix";
        }
    }
}
