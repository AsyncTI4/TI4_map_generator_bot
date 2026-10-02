package ti4.service.bothelper;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import ti4.discord.JdaService;
import ti4.json.PersistenceManager;
import ti4.logging.BotLogger;
import tools.jackson.core.type.TypeReference;

@UtilityClass
public class KeepThreadAliveService {

    private static final String fileName = "keptAliveThreads.json";
    private static List<KeptThread> keptThreadsCache;

    public record KeptThread(
            String threadId, String guildId, String parentChannelId, String label, boolean keepPinned) {}

    public static void add(ThreadChannel thread, boolean keepPinned) {
        List<KeptThread> kept = readKeptThreads();
        kept.removeIf(k -> k.threadId().equals(thread.getId()));
        kept.add(new KeptThread(
                thread.getId(),
                thread.getGuild().getId(),
                thread.getParentChannel().getId(),
                thread.getName(),
                keepPinned));
        saveKeptThreads(kept);
    }

    public static void remove(String threadId) {
        List<KeptThread> kept = readKeptThreads();
        if (kept.removeIf(k -> k.threadId().equals(threadId))) {
            saveKeptThreads(kept);
        }
    }

    public static List<KeptThread> getAll() {
        return new ArrayList<>(readKeptThreads());
    }

    public static String summarize() {
        List<KeptThread> kept = readKeptThreads();
        if (kept.isEmpty()) {
            return "No threads are currently being kept alive.";
        }
        StringBuilder sb = new StringBuilder("__**Threads currently being kept alive:**__");
        for (KeptThread k : kept) {
            sb.append("\n> ")
                    .append(k.label())
                    .append(" — <#")
                    .append(k.threadId())
                    .append(">")
                    .append(k.keepPinned() ? " (kept pinned)" : "");
        }
        return sb.toString();
    }

    public static void refreshThread(KeptThread kept) {
        ThreadChannel thread = resolveThread(kept);
        if (thread == null) {
            BotLogger.warning("KeepThreadAliveService: could not find thread \"" + kept.label() + "\" ("
                    + kept.threadId() + "); it may have been deleted.");
            return;
        }
        refreshThread(thread, kept.keepPinned());
    }

    public static void refreshThread(ThreadChannel thread, boolean keepPinned) {
        if (thread.isLocked()) {
            BotLogger.warning("KeepThreadAliveService: thread \"" + thread.getName() + "\" (" + thread.getId()
                    + ") is locked; cannot keep it alive.");
            return;
        }
        ThreadChannelManager manager = thread.getManager();
        boolean modified = false;
        if (thread.isArchived()) {
            manager = manager.setArchived(false);
            modified = true;
        }
        if (keepPinned && thread.getParentChannel().getType() == ChannelType.FORUM && !thread.isPinned()) {
            manager = manager.setPinned(true);
            modified = true;
        }
        if (modified) {
            manager.complete();
        }
        thread.sendMessage("Keeping this thread active.").complete().delete().queue();
    }

    private static ThreadChannel resolveThread(KeptThread kept) {
        long threadId = Long.parseLong(kept.threadId());
        ThreadChannel cachedThread = JdaService.jda.getThreadChannelById(threadId);
        if (cachedThread != null) return cachedThread;

        Guild guild = JdaService.jda.getGuildById(kept.guildId());
        if (guild == null) return null;

        try {
            ThreadChannel activeThread = guild.retrieveActiveThreads().complete().stream()
                    .filter(thread -> thread.getIdLong() == threadId)
                    .findFirst()
                    .orElse(null);
            if (activeThread != null) return activeThread;
        } catch (Exception e) {
            BotLogger.warning(
                    "KeepThreadAliveService: failed to retrieve active threads for guild " + kept.guildId(), e);
        }

        IThreadContainer parent = guild.getChannelById(IThreadContainer.class, kept.parentChannelId());
        if (parent == null) return null;

        try {
            ThreadChannel archivedPublicThread = parent.retrieveArchivedPublicThreadChannels().complete().stream()
                    .filter(thread -> thread.getIdLong() == threadId)
                    .findFirst()
                    .orElse(null);
            if (archivedPublicThread != null) return archivedPublicThread;
        } catch (Exception e) {
            BotLogger.warning(
                    "KeepThreadAliveService: failed to retrieve archived public threads in channel "
                            + kept.parentChannelId(),
                    e);
        }

        if (parent instanceof TextChannel textChannel) {
            try {
                ThreadChannel archivedPrivateThread =
                        textChannel.retrieveArchivedPrivateThreadChannels().complete().stream()
                                .filter(thread -> thread.getIdLong() == threadId)
                                .findFirst()
                                .orElse(null);
                if (archivedPrivateThread != null) return archivedPrivateThread;
            } catch (Exception e) {
                BotLogger.warning(
                        "KeepThreadAliveService: failed to retrieve archived private threads in channel "
                                + kept.parentChannelId(),
                        e);
            }
        }

        return null;
    }

    private static List<KeptThread> readKeptThreads() {
        if (keptThreadsCache != null) return keptThreadsCache;

        try {
            List<KeptThread> kept = PersistenceManager.readObjectFromJsonFile(fileName, new TypeReference<>() {});
            keptThreadsCache = kept == null ? new ArrayList<>() : new ArrayList<>(kept);
        } catch (Exception e) {
            BotLogger.error("Failed to read json data for Kept Alive Threads.", e);
            keptThreadsCache = new ArrayList<>();
        }
        return keptThreadsCache;
    }

    private static void saveKeptThreads(List<KeptThread> kept) {
        try {
            PersistenceManager.writeObjectToJsonFile(fileName, kept);
            keptThreadsCache = kept;
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for Kept Alive Threads.", e);
        }
    }
}
