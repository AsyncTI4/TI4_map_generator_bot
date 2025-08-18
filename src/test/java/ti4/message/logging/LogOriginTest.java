package ti4.message.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.helpers.DateTimeHelper;
import ti4.map.Game;

class LogOriginTest {

    private static class TestEventLog extends AbstractEventLog {
        TestEventLog(LogOrigin source) {
            super(source);
        }

        @Override
        public String getChannelName() {
            return "";
        }

        @Override
        public String getThreadName() {
            return "";
        }

        @Override
        public String getMessagePrefix() {
            return "";
        }
    }

    @Test
    void logStringContainsPreformattedEventAndGameInfo() {
        try (MockedStatic<DateTimeHelper> mocked = mockStatic(DateTimeHelper.class)) {
            mocked.when(DateTimeHelper::getCurrentTimestamp).thenReturn("`timestamp`");

            SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
            when(event.isFromGuild()).thenReturn(true);
            User user = mock(User.class);
            when(user.getEffectiveName()).thenReturn("Tester");
            when(event.getUser()).thenReturn(user);
            when(event.getCommandString()).thenReturn("/ping");
            Guild guild = mock(Guild.class);
            when(guild.getId()).thenReturn("1");
            when(event.getGuild()).thenReturn(guild);
            GuildChannel channel = mock(GuildChannel.class);
            when(event.getGuildChannel()).thenReturn(channel);
            when(channel.getGuild()).thenReturn(guild);

            Game game = mock(Game.class);
            when(game.getGuild()).thenReturn(guild);
            when(game.gameJumpLinks()).thenReturn("TestGame [tt] [act]");

            LogOrigin origin = new LogOrigin(event, game);
            String expected = "**__`timestamp`__** Tester used command `/ping`\n\nGame info: TestGame [tt] [act]\n\n";

            String log = new TestEventLog(origin).getLogString();
            assertThat(log).isEqualTo(expected);
        }
    }
}
