package ti4.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.helpers.DateTimeHelper;
import ti4.spring.service.deploy.ActiveLeaseService;

class LogOriginTest {

    private final Guild guild = mock(Guild.class);
    private final GuildChannel channel = mock(GuildChannel.class);
    private final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    private final ButtonInteractionEvent buttonEvent = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
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

        User buttonUser = mock(User.class);
        when(buttonUser.getEffectiveName()).thenReturn("Tester");
        when(buttonEvent.getUser()).thenReturn(buttonUser);
    }

    @Test
    void logStringContainsPreformattedEventAndGameInfo() {
        try (MockedStatic<DateTimeHelper> mockedTime = mockStatic(DateTimeHelper.class);
                MockedStatic<ActiveLeaseService> mockedActiveLeaseService = mockStatic(ActiveLeaseService.class)) {
            mockedTime.when(DateTimeHelper::getCurrentTimestamp).thenReturn("`timestamp`");
            mockedActiveLeaseService
                    .when(ActiveLeaseService::getCurrentProcessLogPrefix)
                    .thenReturn("");

            LogOrigin origin = new LogOrigin(event, game);
            String log = new TestEventLog(origin).getLogString();

            String expected = """
                **__`timestamp`__** Tester used command `/ping`

                Game info: TestGame [tt] [act]
                """;
            assertThat(log).isEqualTo(expected);
        }
    }

    @Test
    void buttonLogContainsChannelJumpLink() {
        try (MockedStatic<DateTimeHelper> mockedTime = mockStatic(DateTimeHelper.class);
                MockedStatic<ActiveLeaseService> mockedActiveLeaseService = mockStatic(ActiveLeaseService.class)) {
            mockedTime.when(DateTimeHelper::getCurrentTimestamp).thenReturn("`timestamp`");
            mockedActiveLeaseService
                    .when(ActiveLeaseService::getCurrentProcessLogPrefix)
                    .thenReturn("");

            Button button = mock(Button.class);
            Message message = mock(Message.class);
            when(button.getCustomId()).thenReturn("combatRoll_307_space");
            when(button.getLabel()).thenReturn("Roll Space Combat");
            when(buttonEvent.getButton()).thenReturn(button);
            when(buttonEvent.getChannel().getName()).thenReturn("pbd123-actions");
            when(buttonEvent.getMessage()).thenReturn(message);
            when(message.getJumpUrl()).thenReturn("https://discord.com/channels/1/2/3");

            LogOrigin origin = new LogOrigin(buttonEvent);
            String log = new TestEventLog(origin).getLogString();

            assertThat(log)
                    .contains(
                            "Tester pressed button __**Roll Space Combat**__  `[combatRoll_307_space]` in: [pbd123-actions](https://discord.com/channels/1/2/3)");
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
