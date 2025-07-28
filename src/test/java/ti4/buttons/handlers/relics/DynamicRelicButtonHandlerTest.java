package ti4.buttons.handlers.relics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.service.ComponentActionService;
import ti4.service.MessageService;
import ti4.service.RelicService;
import ti4.service.ServiceRegistry;
import ti4.testUtils.BaseTi4Test;
import ti4.stubs.TestGame;
import ti4.stubs.TestPlayer;

@ExtendWith(MockitoExtension.class)
class DynamicRelicButtonHandlerTest extends BaseTi4Test {

    @Mock
    private RelicService relicService;

    @Mock
    private ComponentActionService componentActionService;

    @Mock
    private MessageService messageService;

    private DynamicRelicButtonHandler handler;
    private ButtonInteractionEvent event;
    private TextChannel channel;
    private TestPlayer player;
    private TestGame game;

    @BeforeEach
    void setUp() {
        handler = new DynamicRelicButtonHandler(relicService, componentActionService, messageService);
        event = mock(ButtonInteractionEvent.class);
        channel = mock(TextChannel.class);
        game = new TestGame(channel);
        game.setName("testGame");
        player = new TestPlayer("testUserId", "testUserName", game, channel);
        player.setFaction("sol");
    }

    @Test
    void drawRelicFromFrag_shouldCallAllServices() {
        // When
        handler.drawRelicFromFrag(event, player, game);

        // Then
        verify(relicService).drawRelicAndNotify(player, event, game);
        verify(componentActionService).serveNextComponentActionButtons(event, game, player);
        verify(messageService).deleteMessage(event);
    }

}