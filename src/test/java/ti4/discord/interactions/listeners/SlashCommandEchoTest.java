package ti4.discord.interactions.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Test;

/**
 * Every slash command is echoed into the channel it ran in, and the moderator log and the "privately used" notice only
 * run once that echo is sent. JDA's command string includes every option value, so deciding on it let any player hide
 * any command by typing one of the exempt command names into a text option.
 */
class SlashCommandEchoTest {

    @Test
    void optionTextCannotSuppressTheEcho() {
        SlashCommandInteractionEvent event =
                event("player stats", "/player stats faction_or_color:red /rules ask trade_goods:+9");

        assertThat(SlashCommandListener.isEchoedInChannel(event)).isTrue();
    }

    @Test
    void ordinaryCommandsAreEchoed() {
        assertThat(SlashCommandListener.isEchoedInChannel(event("player stats", "/player stats trade_goods:+1")))
                .isTrue();
    }

    @Test
    void privateMessagingCommandsStayUnechoed() {
        assertThat(SlashCommandListener.isEchoedInChannel(event("rules ask", "/rules ask question:why")))
                .isFalse();
        assertThat(SlashCommandListener.isEchoedInChannel(event("fow whisper", "/fow whisper msg:hi")))
                .isFalse();
        assertThat(SlashCommandListener.isEchoedInChannel(event("bothelper impersonate", "/bothelper impersonate")))
                .isFalse();
    }

    private static SlashCommandInteractionEvent event(String fullCommandName, String commandString) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getFullCommandName()).thenReturn(fullCommandName);
        when(event.getCommandString()).thenReturn(commandString);
        return event;
    }
}
