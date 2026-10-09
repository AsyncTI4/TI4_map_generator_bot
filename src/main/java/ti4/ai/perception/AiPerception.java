package ti4.ai.perception;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;

@UtilityClass
public class AiPerception {

    public static final String DELEGATION_PREFIX = "aiSeatPick_";
    private static final int COMBAT_THREADS_TO_READ = 2;
    private static final Pattern SYSTEM_POSITION = Pattern.compile("-system-([^-]+)-turn-");

    private record CombatThread(String name, long latestMessageId, List<AiPrompt> prompts) {}

    public static final class Snapshot {
        private final Game game;
        private final Map<String, List<AiPrompt>> privateThreads = new HashMap<>();
        private final List<AiPrompt> main = new ArrayList<>();
        private final List<CombatThread> combatThreads = new ArrayList<>();

        Snapshot(Game game) {
            this.game = game;
        }

        public List<AiPrompt> promptsFor(Player seat, Set<String> publicWindowPrefixes) {
            List<AiPrompt> prompts = new ArrayList<>();
            String faction = seat.getFaction();
            for (AiPrompt prompt : privateThreads.getOrDefault(seat.getUserID(), List.of())) {
                if (isRelevant(PromptSource.AI_THREAD, prompt.buttons(), faction, publicWindowPrefixes)) {
                    prompts.add(prompt);
                }
            }
            for (AiPrompt prompt : main) {
                if (isRelevant(PromptSource.PUBLIC, prompt.buttons(), faction, publicWindowPrefixes)) {
                    prompts.add(prompt);
                }
            }
            combatThreads.stream()
                    .filter(thread -> involves(game, seat, thread.name()))
                    .sorted(Comparator.comparingLong(CombatThread::latestMessageId)
                            .reversed())
                    .limit(COMBAT_THREADS_TO_READ)
                    .forEach(thread -> thread.prompts().stream()
                            .filter(prompt -> isRelevant(
                                    PromptSource.COMBAT_THREAD, prompt.buttons(), faction, publicWindowPrefixes))
                            .forEach(prompts::add));
            return prompts;
        }

        public List<AiPrompt> publicPrompts(Predicate<PromptButton> wanted) {
            return main.stream()
                    .filter(prompt -> prompt.firstEnabled(wanted).isPresent())
                    .sorted(Comparator.comparingLong(AiPrompt::createdAtMillis).reversed())
                    .toList();
        }
    }

    public static List<AiPrompt> collect(Game game, Player seat, Set<String> publicWindowPrefixes) {
        return read(game, List.of(seat)).promptsFor(seat, publicWindowPrefixes);
    }

    public static Snapshot read(Game game, List<Player> seats) {
        Snapshot snapshot = new Snapshot(game);
        if (JdaService.jda == null) return snapshot;
        String botId = JdaService.jda.getSelfUser().getId();
        for (Player seat : seats) {
            ThreadChannel privateThread = privateThread(seat);
            if (privateThread != null) {
                snapshot.privateThreads.put(seat.getUserID(), read(privateThread, botId, PromptSource.AI_THREAD));
            }
        }
        TextChannel main = game.getMainGameChannel();
        if (main == null) return snapshot;
        snapshot.main.addAll(read(main, botId, PromptSource.PUBLIC));
        for (ThreadChannel combat : ongoingCombatThreads(game, main, seats)) {
            snapshot.combatThreads.add(new CombatThread(
                    combat.getName(),
                    combat.getLatestMessageIdLong(),
                    read(combat, botId, PromptSource.COMBAT_THREAD)));
        }
        return snapshot;
    }

    @Nullable
    public static ThreadChannel privateThread(Player seat) {
        String threadId = seat.getCardsInfoThreadID();
        if (JdaService.jda != null && StringUtils.isNumeric(threadId)) {
            ThreadChannel cached = JdaService.jda.getThreadChannelById(threadId);
            if (cached != null) return cached;
        }
        return seat.getCardsInfoThread();
    }

    static boolean isRelevant(PromptSource source, List<PromptButton> buttons, String faction, Set<String> prefixes) {
        if (buttons.stream().anyMatch(button -> button.handlerId().startsWith(DELEGATION_PREFIX))) return false;
        if (source == PromptSource.COMBAT_THREAD) return true;
        if (buttons.isEmpty()) return false;
        if (source == PromptSource.AI_THREAD) return true;
        return buttons.stream()
                .anyMatch(button -> button.isOwnedBy(faction)
                        || (button.isUnowned() && prefixes.stream().anyMatch(button.handlerId()::startsWith)));
    }

    private static List<AiPrompt> read(MessageChannel channel, String botId, PromptSource source) {
        List<Message> history;
        try {
            history = channel.getHistory().retrievePast(AiSettings.HISTORY_SIZE).complete();
        } catch (RuntimeException e) {
            BotLogger.warning("AI players could not read " + channel.getName() + ": " + e.getMessage());
            return List.of();
        }
        List<AiPrompt> prompts = new ArrayList<>();
        for (Message message : history) {
            boolean fromBot = botId.equals(message.getAuthor().getId());
            if (!fromBot && source != PromptSource.COMBAT_THREAD) continue;
            List<PromptButton> buttons = fromBot ? buttonsOf(message) : List.of();
            if (buttons.isEmpty() && source != PromptSource.COMBAT_THREAD) continue;
            prompts.add(new AiPrompt(
                    channel.getId(),
                    message.getId(),
                    source,
                    fromBot ? message.getContentRaw() : "",
                    buttons,
                    message.getTimeCreated().toInstant().toEpochMilli()));
        }
        return prompts;
    }

    static List<PromptButton> buttonsOf(Message message) {
        List<PromptButton> buttons = new ArrayList<>();
        int index = 0;
        for (Button button : message.getComponentTree().findAll(Button.class)) {
            if (button.getCustomId() != null) buttons.add(PromptButton.of(index, button));
            index++;
        }
        return buttons;
    }

    private static List<ThreadChannel> ongoingCombatThreads(Game game, TextChannel main, List<Player> seats) {
        String prefix = game.getName() + "-round-";
        return main.getThreadChannels().stream()
                .filter(thread ->
                        thread.getName().startsWith(prefix) && thread.getName().contains("-vs-"))
                .filter(thread -> !thread.isArchived())
                .filter(thread -> seats.stream().anyMatch(seat -> involves(game, seat, thread.getName())))
                .sorted(Comparator.comparingLong(ThreadChannel::getLatestMessageIdLong)
                        .reversed())
                .limit((long) COMBAT_THREADS_TO_READ * Math.max(1, seats.size()))
                .toList();
    }

    private static boolean involves(Game game, Player seat, String threadName) {
        return (threadName.contains(seat.getFaction()) || threadName.contains(seat.getColor()))
                && isOngoing(game, seat, threadName);
    }

    static boolean isOngoing(Game game, Player seat, String threadName) {
        Matcher matcher = SYSTEM_POSITION.matcher(threadName);
        if (!matcher.find()) return false;
        Tile tile = game.getTileByPosition(matcher.group(1));
        if (tile == null) return false;
        if (opposesSeat(seat, ButtonHelper.getPlayersWithShipsInTheSystem(game, tile))) return true;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (opposesSeat(seat, ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet))) return true;
        }
        return false;
    }

    private static boolean opposesSeat(Player seat, List<Player> present) {
        return present.contains(seat) && present.stream().anyMatch(other -> other != seat);
    }
}
