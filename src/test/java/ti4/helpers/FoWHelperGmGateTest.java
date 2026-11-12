package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class FoWHelperGmGateTest extends BaseTi4Test {

    private Game game;
    private ButtonInteractionEvent event;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUser().getId()).thenReturn("123");
    }

    @Test
    void normalGamesStayOpen() {
        assertThat(FoWHelper.refusesNonGmInFog(game, event)).isFalse();
        verify(event, never()).reply(anyString());
    }

    // The test game has no guild, so nobody holds the GM role.
    @Test
    void fogRefusesNonGmWithAnEphemeralReply() {
        game.setFowMode(true);
        when(event.isAcknowledged()).thenReturn(false);
        assertThat(FoWHelper.refusesNonGmInFog(game, event)).isTrue();
        verify(event).reply("Only the GM can use this in Fog of War.");
    }

    @Test
    void fogUsesTheHookOnceAcknowledged() {
        game.setFowMode(true);
        when(event.isAcknowledged()).thenReturn(true);
        assertThat(FoWHelper.refusesNonGmInFog(game, event)).isTrue();
        verify(event, never()).reply(anyString());
        verify(event.getHook().setEphemeral(true)).sendMessage("Only the GM can use this in Fog of War.");
    }
}
