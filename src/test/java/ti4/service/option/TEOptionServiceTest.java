package ti4.service.option;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.service.fow.GMService;
import ti4.testUtils.BaseTi4Test;

/**
 * The Twilight's Fall homebrew-option flow (reachable from the FoW setup wizard's Step 0) used to post
 * its toggle confirmations unconditionally to {@code game.getMainGameChannel()}, leaking setup chatter
 * to every player in FoW games. {@link TEOptionService} now routes through a FoW-aware channel helper;
 * these tests pin down which channel-resolution path gets used in each mode.
 */
class TEOptionServiceTest extends BaseTi4Test {

    @Test
    void twilightFallHomebrewRoutesToGmChannelInFowGames() {
        Game game = new Game();
        game.setName("te-homebrew-fow-routing-test");
        game.setFowMode(true);

        TextChannel gmChannel = mock(TextChannel.class);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

        try (MockedStatic<GMService> gmService = mockStatic(GMService.class)) {
            gmService.when(() -> GMService.getGMChannel(game)).thenReturn(gmChannel);

            TEOptionService.twilightDSSetup(event, game, "twilightDSSetup_justds");

            gmService.verify(() -> GMService.getGMChannel(game), times(1));
        }
    }

    @Test
    void twilightFallHomebrewDoesNotTouchGmChannelOutsideFow() {
        Game game = new Game();
        game.setName("te-homebrew-nonfow-routing-test");
        game.setFowMode(false);

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

        try (MockedStatic<GMService> gmService = mockStatic(GMService.class)) {
            TEOptionService.twilightDSSetup(event, game, "twilightDSSetup_justds");

            gmService.verify(() -> GMService.getGMChannel(game), never());
        }
    }
}
