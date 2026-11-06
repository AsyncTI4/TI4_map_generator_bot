package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class YinAgentOfferTest extends BaseTi4Test {

    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;

    @Test
    void theHolderIsAskedWhoseUnitWasDestroyedInThePressedChannel() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        ButtonInteractionEvent event = combatButtonPress("Space combat in 101");

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<ButtonHelper> buttonHelper = mockStatic(ButtonHelper.class)) {
            YinAgent.offerOwners("yinagent_" + ACTIVE, event, fixture.game, fixture.user);

            ArgumentCaptor<String> text = ArgumentCaptor.captor();
            ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.captor();
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                    any(MessageChannel.class), text.capture(), buttons.capture()));
            assertThat(text.getValue()).contains("please choose the faction to give");
            // One choice per player at the table, each an owned press of the agent itself.
            assertThat(buttons.getValue())
                    .hasSize(2)
                    .extracting(button -> ComponentIdEnvelope.decode(button.getCustomId()))
                    .allSatisfy(envelope -> {
                        assertThat(envelope.ownerFaction()).isEqualTo(fixture.user.getFaction());
                        assertThat(envelope.handlerId()).startsWith("exhaustAgent_yinagent_" + ACTIVE + "_");
                    });
            buttonHelper.verify(() -> ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event));
        }
        verify(event.getMessage(), never()).editMessage(anyString());
    }

    @Test
    void aPressedMessageWithoutTextIsLabelledCombatSoItSurvivesLosingItsButton() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        ButtonInteractionEvent event = combatButtonPress("");

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<ButtonHelper> buttonHelper = mockStatic(ButtonHelper.class)) {
            YinAgent.offerOwners("yinagent_" + ACTIVE, event, fixture.game, fixture.user);

            messages.verify(() ->
                    MessageHelper.sendMessageToChannelWithButtons(any(MessageChannel.class), anyString(), anyList()));
            buttonHelper.verify(() -> ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event));
        }
        verify(event.getMessage()).editMessage("Combat");
    }

    private static ButtonInteractionEvent combatButtonPress(String pressedMessageText) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getMessage().getContentRaw()).thenReturn(pressedMessageText);
        when(event.getMessageChannel().getId()).thenReturn("combat");
        return event;
    }
}
