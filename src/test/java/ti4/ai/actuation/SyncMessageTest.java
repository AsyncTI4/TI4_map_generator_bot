package ti4.ai.actuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;

class SyncMessageTest {

    // AnnotationHandler replies "The button failed..." on the pressed message when a handler throws. For the AI
    // that reply is swallowed and turned into a failure signal instead of a public message.
    @Test
    void handlerFailureReplyIsCapturedAndNotSent() {
        Message real = mock(Message.class);
        SyncMessage sync = new SyncMessage(real);

        MessageCreateAction reply = sync.proxy().reply("The button failed. An exception has been logged.");
        reply.queue();

        assertThat(sync.handlerFailed()).isTrue();
        verify(real, never()).reply(anyString());
    }

    @Test
    void otherRepliesGoThroughUnchanged() {
        Message real = mock(Message.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(real.reply("Rolled 7")).thenReturn(action);
        SyncMessage sync = new SyncMessage(real);

        assertThat(sync.proxy().reply("Rolled 7")).isSameAs(action);
        assertThat(sync.handlerFailed()).isFalse();
    }

    // Handler edits go out without blocking while the game lock is held; the actuator waits for them afterwards,
    // so the AI's next look at the message sees the edit. Reads keep returning the original message, as in JDA.
    @Test
    void editsAreTrackedAndAwaitableWithoutBlockingTheHandler() {
        Message real = mock(Message.class);
        Message edited = mock(Message.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(real.editMessage("Net gain of: 1.")).thenReturn(edit);
        when(edit.setComponents(anyCollection())).thenReturn(edit);
        when(edit.submit()).thenReturn(CompletableFuture.completedFuture(edited));
        when(real.getContentRaw()).thenReturn("original");
        SyncMessage sync = new SyncMessage(real);
        AtomicReference<Object> callback = new AtomicReference<>();

        sync.proxy().editMessage("Net gain of: 1.").setComponents(List.of()).queue(callback::set);

        verify(edit).submit();
        verify(edit, never()).complete();
        verify(edit, never()).queue(any(), any());
        assertThat(sync.awaitPendingEdits(Duration.ofSeconds(1))).isTrue();
        assertThat(callback.get()).isSameAs(edited);
        assertThat(sync.proxy().getContentRaw()).isEqualTo("original");
    }

    @Test
    void aFailedEditIsReportedToTheHandlersFailureCallback() {
        Message real = mock(Message.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(real.editMessage("x")).thenReturn(edit);
        when(edit.submit()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("gone")));
        SyncMessage sync = new SyncMessage(real);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        sync.proxy().editMessage("x").queue(null, failure::set);

        assertThat(sync.awaitPendingEdits(Duration.ofSeconds(1))).isFalse();
        assertThat(failure.get()).hasMessage("gone");
    }
}
