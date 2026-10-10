package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.AnnotationHandler;

// The pressed message as the handler sees it: edits are tracked so a press can wait for them, and the
// "The button failed" reply marks the press as failed while still reaching Discord.
class TestBedSyncMessageTest {

    private Message real;

    @BeforeEach
    void setUp() {
        real = mock(Message.class);
        when(real.getId()).thenReturn("99");
    }

    @Test
    void waitsForQueuedEditsToLand() {
        CompletableFuture<Message> edit = new CompletableFuture<>();
        MessageEditAction action = mock(MessageEditAction.class, RETURNS_SELF);
        when(action.submit()).thenReturn(edit);
        when(real.editMessage(any(CharSequence.class))).thenReturn(action);
        TestBedSyncMessage synced = new TestBedSyncMessage(real);

        synced.proxy().editMessage("Choose a system").setComponents(List.of()).queue();

        assertFalse(synced.awaitPendingEdits(Duration.ofMillis(50)));
        edit.complete(real);
        assertTrue(synced.awaitPendingEdits(Duration.ofMillis(50)));
        verify(action).setComponents(List.of());
    }

    // A failed edit still counts as landed: the press is over, waiting longer would not help.
    @Test
    void failedEditsDoNotBlock() {
        MessageEditAction action = mock(MessageEditAction.class, RETURNS_SELF);
        when(action.submit()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("gone")));
        when(real.editMessage(any(CharSequence.class))).thenReturn(action);
        TestBedSyncMessage synced = new TestBedSyncMessage(real);

        synced.proxy().editMessage("x").queue();

        assertTrue(synced.awaitPendingEdits(Duration.ofMillis(50)));
    }

    @Test
    void flagsTheHandlerFailureReplyAndStillSendsIt() {
        when(real.reply(any(CharSequence.class))).thenReturn(mock(MessageCreateAction.class));
        TestBedSyncMessage synced = new TestBedSyncMessage(real);

        synced.proxy().reply("Something else");
        assertFalse(synced.handlerFailed());

        synced.proxy().reply(AnnotationHandler.BUTTON_FAILURE_PREFIX + " An exception has been logged.");
        assertTrue(synced.handlerFailed());
        verify(real).reply(AnnotationHandler.BUTTON_FAILURE_PREFIX + " An exception has been logged.");
    }
}
