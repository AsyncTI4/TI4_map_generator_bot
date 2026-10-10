package ti4.service.testbed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.logging.BotLogger;

final class TestBedMessageLog extends ListenerAdapter implements AutoCloseable {

    static final long BEFORE_RUN = -1;

    record Entry(Message message, long arrival, long touched) {}

    private final Map<String, Map<Long, Entry>> byChannel = new HashMap<>();
    private final Set<String> seeded = new HashSet<>();
    private final Function<MessageChannel, List<Message>> seeder;
    private final String guildId;
    private final JDA jda;
    private long arrivals;

    private TestBedMessageLog(JDA jda, String guildId, Function<MessageChannel, List<Message>> seeder) {
        this.jda = jda;
        this.guildId = guildId;
        this.seeder = seeder;
    }

    static TestBedMessageLog open(JDA jda, String guildId) {
        TestBedMessageLog log = new TestBedMessageLog(jda, guildId, TestBedPress::recentHistory);
        jda.addEventListener(log);
        return log;
    }

    static TestBedMessageLog detached(Function<MessageChannel, List<Message>> seeder) {
        return new TestBedMessageLog(null, null, seeder);
    }

    @Override
    public void close() {
        if (jda != null) jda.removeEventListener(this);
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (isWatched(event.isFromGuild() ? event.getGuild().getId() : null)) received(event.getMessage());
    }

    @Override
    public void onMessageUpdate(@Nonnull MessageUpdateEvent event) {
        if (isWatched(event.isFromGuild() ? event.getGuild().getId() : null)) updated(event.getMessage());
    }

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        deleted(event.getChannel().getId(), List.of(event.getMessageIdLong()));
    }

    @Override
    public void onMessageBulkDelete(@Nonnull MessageBulkDeleteEvent event) {
        deleted(
                event.getChannel().getId(),
                event.getMessageIds().stream().map(Long::parseLong).toList());
    }

    private boolean isWatched(String eventGuildId) {
        return guildId == null || guildId.equals(eventGuildId);
    }

    synchronized void received(Message message) {
        arrivals++;
        channel(message.getChannel().getId()).put(message.getIdLong(), new Entry(message, arrivals, arrivals));
        notifyAll();
    }

    synchronized void updated(Message message) {
        Map<Long, Entry> channel = channel(message.getChannel().getId());
        Entry previous = channel.get(message.getIdLong());
        long arrival = previous != null ? previous.arrival() : BEFORE_RUN;
        arrivals++;
        channel.put(message.getIdLong(), new Entry(message, arrival, arrivals));
        notifyAll();
    }

    synchronized void deleted(String channelId, Collection<Long> messageIds) {
        Map<Long, Entry> channel = byChannel.get(channelId);
        if (channel == null) return;
        messageIds.forEach(channel::remove);
        arrivals++;
        notifyAll();
    }

    synchronized long mark() {
        return arrivals;
    }

    List<Message> newestFirst(MessageChannel channel) {
        seedOnce(channel);
        return entries(channel.getId()).stream().map(Entry::message).toList();
    }

    List<Message> arrivedAfter(MessageChannel channel, long mark) {
        return entries(channel.getId()).stream()
                .filter(entry -> entry.arrival() > mark)
                .map(Entry::message)
                .toList();
    }

    List<Message> touchedAfter(MessageChannel channel, long mark) {
        return entries(channel.getId()).stream()
                .filter(entry -> entry.touched() > mark)
                .map(Entry::message)
                .toList();
    }

    synchronized boolean awaitChange(long seenMark, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (arrivals == seenMark) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return false;
            try {
                wait(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private synchronized List<Entry> entries(String channelId) {
        Map<Long, Entry> channel = byChannel.get(channelId);
        if (channel == null) return List.of();
        List<Entry> entries = new ArrayList<>(channel.values());
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.message().getIdLong())
                .reversed());
        return entries;
    }

    private void seedOnce(MessageChannel channel) {
        synchronized (this) {
            if (!seeded.add(channel.getId())) return;
        }
        List<Message> history;
        try {
            history = seeder.apply(channel);
        } catch (RuntimeException e) {
            BotLogger.warning("Test bed could not read " + channel.getName() + ": " + e.getMessage());
            return;
        }
        synchronized (this) {
            Map<Long, Entry> known = channel(channel.getId());
            for (Message message : history) {
                known.putIfAbsent(message.getIdLong(), new Entry(message, BEFORE_RUN, BEFORE_RUN));
            }
        }
    }

    private Map<Long, Entry> channel(String channelId) {
        return byChannel.computeIfAbsent(channelId, id -> new HashMap<>());
    }
}
