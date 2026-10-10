package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The run-scoped log that replaces history polling: it knows what arrived or changed after a mark, and wakes
// waiters as soon as something happens.
class TestBedMessageLogTest {

    private MessageChannelUnion channel;

    @BeforeEach
    void setUp() {
        channel = mock(MessageChannelUnion.class);
        when(channel.getId()).thenReturn("main");
        when(channel.getName()).thenReturn("main");
    }

    @Test
    void separatesOlderHistoryFromWhatArrivedDuringTheRun() {
        Message old = message(10, "before the run");
        AtomicInteger reads = new AtomicInteger();
        TestBedMessageLog log = TestBedMessageLog.detached(seeded -> {
            reads.incrementAndGet();
            return List.of(old);
        });
        long start = log.mark();
        Message fresh = message(20, "posted by the step");
        log.received(fresh);

        assertEquals(List.of(fresh), log.arrivedAfter(channel, start));
        assertEquals(List.of(fresh, old), log.newestFirst(channel));
        // History is read from Discord once per channel; later reads come from the log.
        log.newestFirst(channel);
        assertEquals(1, reads.get());
    }

    // A step-scoped check sees only what came after the step began; an edit counts as touched but not as new.
    @Test
    void marksSplitSteps() {
        TestBedMessageLog log = TestBedMessageLog.detached(seeded -> List.of());
        Message first = message(10, "step one");
        log.received(first);
        long stepTwo = log.mark();
        Message edited = message(10, "step one, edited");
        log.updated(edited);
        Message second = message(30, "step two");
        log.received(second);

        assertEquals(List.of(second), log.arrivedAfter(channel, stepTwo));
        assertEquals(List.of(second, edited), log.touchedAfter(channel, stepTwo));
    }

    @Test
    void deletedMessagesDisappear() {
        TestBedMessageLog log = TestBedMessageLog.detached(seeded -> List.of());
        log.received(message(10, "carrier"));
        log.deleted("main", List.of(10L));
        assertEquals(List.of(), log.arrivedAfter(channel, 0));
    }

    @Test
    void wakesAWaiterWhenAMessageArrives() throws InterruptedException {
        TestBedMessageLog log = TestBedMessageLog.detached(seeded -> List.of());
        long mark = log.mark();
        Thread poster = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.received(message(10, "hello"));
        });
        long started = System.currentTimeMillis();
        poster.start();
        assertTrue(log.awaitChange(mark, 5000));
        assertTrue(System.currentTimeMillis() - started < 4000);
        poster.join();
        assertFalse(log.awaitChange(log.mark(), 20));
    }

    private Message message(long id, String content) {
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(id);
        when(message.getChannel()).thenReturn(channel);
        when(message.getContentRaw()).thenReturn(content);
        return message;
    }
}
