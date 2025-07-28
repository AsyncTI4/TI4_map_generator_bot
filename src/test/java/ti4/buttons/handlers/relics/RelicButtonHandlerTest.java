package ti4.buttons.handlers.relics;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;
import ti4.stubs.TestGame;
import ti4.stubs.TestPlayer;

@ExtendWith(MockitoExtension.class)
class RelicButtonHandlerTest extends BaseTi4Test {
    private ButtonInteractionEvent event;
    private TextChannel channel;
    private TestPlayer player;
    private TestGame game;

    @BeforeEach
    void setUp() {
        event = mock();
        channel = mock();
        game = new TestGame(channel);
        game.setName("testGame");
        player = new TestPlayer("testUserId", "testUserName", game, channel);
        player.setFaction("sol");
    }

    @Test
    void drawRelicFromFrag_shouldServeNextComponentActionButtons() {
        try (MockedStatic<RelicHelper> relicHelperMock = mockStatic(RelicHelper.class);
             MockedStatic<MessageHelper> messageHelperMock = mockStatic(MessageHelper.class);
             MockedStatic<ButtonHelper> buttonHelperMock = mockStatic(ButtonHelper.class)) {
            ArgumentCaptor<List<Button>> buttonsCaptor = ArgumentCaptor.forClass(List.class);

            // When
            RelicButtonHandler.drawRelicFromFrag(event, player, game);

            // Then
            relicHelperMock.verify(() -> RelicHelper.drawRelicAndNotify(player, event, game));
            messageHelperMock.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                eq(channel),
                eq("Use buttons to end turn or do another action."),
                buttonsCaptor.capture()
            ));

            // Check that we have tactical action and component action buttons
            List<Button> actualButtons = buttonsCaptor.getValue();
            assertNotNull(actualButtons);
            assertFalse(actualButtons.isEmpty());

            boolean hasTacticalAction = actualButtons.stream()
                .anyMatch(button -> button.getLabel().contains("End Turn"));

            boolean hasComponentAction = actualButtons.stream()
                .anyMatch(button -> button.getLabel().contains("Transaction"));

            assertTrue(hasTacticalAction, "Should contain an end turn button");
            assertTrue(hasComponentAction, "Should contain a transaction button");
        }
    }
}