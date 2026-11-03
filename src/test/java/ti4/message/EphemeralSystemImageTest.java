package ti4.message;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CancellationException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import ti4.logging.BotLogger;

class EphemeralSystemImageTest {

    @Test
    void waitsForThePreviewBeforeReturningToActivation() {
        ButtonInteractionEvent event = activationEvent();
        FileUpload file = mock(FileUpload.class);
        WebhookMessageCreateAction<Message> action = imageAction(event, file);

        MessageHelper.sendEphemeralFileInResponseToButtonPressComplete(file, event);

        verify(action).addFiles(file);
        verify(action).setEphemeral(true);
        verify(action).complete();
        verifyNoMoreInteractions(action);
    }

    @Test
    void failedAcknowledgementDoesNotAbortActivation() {
        ButtonInteractionEvent event = activationEvent();
        FileUpload file = mock(FileUpload.class);
        WebhookMessageCreateAction<Message> action = imageAction(event, file);
        CancellationException failure = new CancellationException("join");
        when(action.complete()).thenThrow(failure);

        try (var logger = mockStatic(BotLogger.class)) {
            assertThatCode(() -> MessageHelper.sendEphemeralFileInResponseToButtonPressComplete(file, event))
                    .doesNotThrowAnyException();

            logger.verify(() -> BotLogger.catchRestError(failure));
        }
    }

    @Test
    void failurePreparingThePreviewDoesNotAbortActivation() {
        ButtonInteractionEvent event = activationEvent();
        FileUpload file = mock(FileUpload.class);
        IllegalStateException failure = new IllegalStateException("Interaction hook unavailable");
        when(event.getHook()).thenThrow(failure);

        try (var logger = mockStatic(BotLogger.class)) {
            assertThatCode(() -> MessageHelper.sendEphemeralFileInResponseToButtonPressComplete(file, event))
                    .doesNotThrowAnyException();

            logger.verify(() -> BotLogger.catchRestError(failure));
        }
    }

    private ButtonInteractionEvent activationEvent() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getButton().getCustomId()).thenReturn("FFCC_sol_ringTile_101");
        return event;
    }

    @SuppressWarnings("unchecked")
    private WebhookMessageCreateAction<Message> imageAction(ButtonInteractionEvent event, FileUpload file) {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> action = mock(WebhookMessageCreateAction.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage("Here is a picture of the active system")).thenReturn(action);
        when(action.addFiles(file)).thenReturn(action);
        when(action.setEphemeral(true)).thenReturn(action);
        return action;
    }
}
