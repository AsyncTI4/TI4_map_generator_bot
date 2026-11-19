package ti4.service.megagame;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.utility.DiscordThreadUtility;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TransactionHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.service.game.MegagameUndoService;

@UtilityClass
public class MegagameTradeOverviewService {

    private static final String CHANNEL_SUFFIX = "-trade-overview";
    private static final String THREAD_PREFIX = "Trades · ";
    private static final String SECTION_KEY = "megagameTradeOverviewSection";
    private static final String FACTION_ROUND_KEY = "megagameTradeOverviewRound_";
    private static final String NOTHING = "Nothing";
    private static final String TRADE_ARROWS = " ⇄ ";
    private static final EnumSet<Permission> PLAYER_ALLOW =
            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY);
    private static final EnumSet<Permission> PLAYER_DENY = EnumSet.of(
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_SEND_IN_THREADS,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.CREATE_PRIVATE_THREADS);
    private static final long THREAD_LOOKUP_TIMEOUT_SECONDS = 60;
    private static final int NOTICE_SEARCH = 10;
    private static final String PANEL_THREAD = "Tradables";
    private static final ThreadChannel.AutoArchiveDuration PANEL_ARCHIVE =
            ThreadChannel.AutoArchiveDuration.TIME_1_WEEK;
    private static final Map<String, CompletableFuture<Void>> POST_QUEUES = new ConcurrentHashMap<>();

    public static boolean isActive(Game game) {
        return game != null
                && !game.isFowMode()
                && game.getTags().stream().anyMatch(MegagameUndoService.MEGAGAME_TAG::equalsIgnoreCase);
    }

    public static String channelName(Game game) {
        return (game.getName() + CHANNEL_SUFFIX).toLowerCase();
    }

    public static void recordTrade(Game game, Player p1, Player p2) {
        if (!isActive(game)) return;
        try {
            MessageEmbed trade = tradeEmbed(game, p1, p2);
            MessageEmbed sectionHeader = sectionHeaderIfNew(game);
            MessageEmbed p1Header = roundHeaderIfNew(game, p1);
            MessageEmbed p2Header = roundHeaderIfNew(game, p2);
            String p1ThreadName = threadName(p1);
            String p2ThreadName = threadName(p2);
            enqueue(
                    game,
                    "post a trade to the trade overview",
                    channel -> threadOrNull(channel, p1ThreadName)
                            .thenCompose(p1Thread -> threadOrNull(channel, p2ThreadName)
                                    .thenCompose(p2Thread -> channel.sendMessageEmbeds(withHeader(
                                                    sectionHeader, linkedToThreads(trade, p1Thread, p2Thread)))
                                            .submit()
                                            .thenAccept(posted -> {
                                                MessageEmbed backLink = linkedToOverview(trade, posted);
                                                sendIfPresent(p1Thread, withHeader(p1Header, backLink));
                                                sendIfPresent(p2Thread, withHeader(p2Header, backLink));
                                            }))));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to record a trade in the megagame trade overview.", e);
        }
    }

    public static String ensureChannel(Game game) {
        if (game.isFowMode()) {
            return "The trade overview is not available in Fog of War.";
        }
        if (game.getGuild() == null) {
            return "Could not create the trade overview channel: no server found for this game.";
        }
        Role gameRole = gameRole(game);
        TextChannel existing = channelOrNull(game);
        if (existing == null) {
            enqueue(game, "create the trade overview channel", _ -> CompletableFuture.completedFuture(null));
            return "Creating trade overview channel `#" + channelName(game) + "`." + missingRoleNote(game, gameRole);
        }
        if (gameRole != null) {
            existing.upsertPermissionOverride(gameRole)
                    .grant(PLAYER_ALLOW)
                    .deny(PLAYER_DENY)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        MegagameTradablesPanel.refreshNow(game);
        return "Trade overview channel: " + existing.getAsMention() + missingRoleNote(game, gameRole);
    }

    static String phaseLabel(String phase) {
        if (StringUtils.isBlank(phase)) return "Unknown Phase";
        String lower = phase.toLowerCase();
        if (lower.startsWith("strategy")) return "Strategy Phase";
        if (lower.startsWith("action")) return "Action Phase";
        if (lower.startsWith("status")) return "Status Phase";
        if (lower.startsWith("agenda")) return "Agenda Phase";
        if ("miltydraft".equals(lower) || "playersetup".equals(lower)) return "Setup";
        return StringUtils.capitalize(phase) + " Phase";
    }

    static Color phaseColour(String phaseLabel) {
        return switch (phaseLabel) {
            case "Strategy Phase" -> new Color(87, 242, 135);
            case "Action Phase" -> new Color(88, 101, 242);
            case "Status Phase" -> new Color(254, 231, 92);
            case "Agenda Phase" -> new Color(155, 89, 182);
            default -> new Color(149, 165, 166);
        };
    }

    static String sectionKey(int round, String phaseLabel) {
        return round + ":" + phaseLabel;
    }

    static String fieldValue(List<String> lines) {
        if (lines.isEmpty()) return "- " + NOTHING;
        String joined =
                String.join("\n", lines.stream().map(line -> "- " + line).toList());
        return StringUtils.abbreviate(joined, MessageEmbed.VALUE_MAX_LENGTH);
    }

    private static MessageEmbed sectionHeaderIfNew(Game game) {
        String label = phaseLabel(game.getPhaseOfGame());
        String key = sectionKey(game.getRound(), label);
        if (key.equals(game.getStoredValue(SECTION_KEY))) return null;
        game.setStoredValue(SECTION_KEY, key);
        return new EmbedBuilder()
                .setTitle("Round " + game.getRound() + " · " + label)
                .setColor(phaseColour(label))
                .build();
    }

    private static MessageEmbed roundHeaderIfNew(Game game, Player player) {
        String key = FACTION_ROUND_KEY + player.getFaction();
        String round = String.valueOf(game.getRound());
        if (round.equals(game.getStoredValue(key))) return null;
        game.setStoredValue(key, round);
        return new EmbedBuilder()
                .setTitle("Round " + round)
                .setColor(phaseColour(""))
                .build();
    }

    private static MessageEmbed tradeEmbed(Game game, Player p1, Player p2) {
        String label = phaseLabel(game.getPhaseOfGame());
        List<String> items = p1.getTransactionItemsWithPlayer(p2);
        return new EmbedBuilder()
                .setDescription(labelWithEmoji(p1) + TRADE_ARROWS + labelWithEmoji(p2))
                .addField(shortName(p1) + " gives", contribution(game, items, p1), true)
                .addField(shortName(p2) + " gives", contribution(game, items, p2), true)
                .setColor(phaseColour(label))
                .setFooter("Round " + game.getRound() + " · " + label)
                .setTimestamp(Instant.now())
                .build();
    }

    private static String contribution(Game game, List<String> items, Player sender) {
        return fieldValue(TransactionHelper.describePublicContribution(game, items, sender));
    }

    private static String labelWithEmoji(Player player) {
        return player.getFactionEmoji() + " **" + shortName(player) + "**";
    }

    static String shortName(Player player) {
        return player.getFactionModel() == null
                ? StringUtils.capitalize(player.getFaction())
                : player.getFactionModel().getShortName();
    }

    private static String threadName(Player player) {
        return THREAD_PREFIX + shortName(player);
    }

    private static List<MessageEmbed> withHeader(MessageEmbed header, MessageEmbed trade) {
        List<MessageEmbed> embeds = new ArrayList<>();
        if (header != null) embeds.add(header);
        embeds.add(trade);
        return embeds;
    }

    private static void enqueue(Game game, String what, Function<TextChannel, CompletableFuture<Void>> step) {
        List<List<MessageEmbed>> initialPanel = channelOrNull(game) == null ? MegagameTradablesPanel.pages(game) : null;
        chain(
                game,
                what,
                previous -> previous.thenComposeAsync(_ -> channel(game, initialPanel))
                        .thenCompose(step));
    }

    static boolean hasQueuedPosts(Game game) {
        return POST_QUEUES.containsKey(game.getName());
    }

    static void enqueueForExistingChannel(Game game, String what, Function<TextChannel, CompletableFuture<Void>> step) {
        chain(
                game,
                what,
                previous -> previous.thenComposeAsync(_ -> {
                    TextChannel channel = channelOrNull(game);
                    return channel == null ? CompletableFuture.completedFuture(null) : step.apply(channel);
                }));
    }

    private static void chain(Game game, String what, Function<CompletableFuture<Void>, CompletableFuture<Void>> link) {
        String key = game.getName();
        CompletableFuture<Void> tail = POST_QUEUES.compute(
                key,
                (_, previous) -> link.apply(previous == null ? CompletableFuture.completedFuture(null) : previous)
                        .exceptionally(error -> {
                            BotLogger.error(new LogOrigin(game), "Failed to " + what + ".", error);
                            return null;
                        }));
        tail.whenComplete((_, _) -> POST_QUEUES.remove(key, tail));
    }

    private static CompletableFuture<ThreadChannel> threadOrNull(TextChannel channel, String threadName) {
        String name = DiscordThreadUtility.fitThreadName(threadName);
        return findOrCreateThread(channel, name, ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
                .orTimeout(THREAD_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(error -> {
                    BotLogger.error(
                            "Could not find or create trade thread " + name + " in " + channel.getName(), error);
                    return null;
                });
    }

    static CompletableFuture<ThreadChannel> panelThread(TextChannel channel) {
        return findOrCreateThread(channel, PANEL_THREAD, PANEL_ARCHIVE)
                .orTimeout(THREAD_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static CompletableFuture<ThreadChannel> findOrCreateThread(
            TextChannel channel, String name, ThreadChannel.AutoArchiveDuration archiveAfter) {
        ThreadChannel active = channel.getThreadChannels().stream()
                .filter(thread -> thread.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (active != null) return CompletableFuture.completedFuture(active);
        return channel.retrieveArchivedPublicThreadChannels()
                .submit()
                .thenCompose(archived -> archived.stream()
                        .filter(thread -> thread.getName().equals(name))
                        .findFirst()
                        .map(thread ->
                                thread.getManager().setArchived(false).submit().thenApply(_ -> thread))
                        .orElseGet(() -> createQuietThread(channel, name, archiveAfter)));
    }

    private static CompletableFuture<ThreadChannel> createQuietThread(
            TextChannel channel, String name, ThreadChannel.AutoArchiveDuration archiveAfter) {
        return channel.createThreadChannel(name)
                .setAutoArchiveDuration(archiveAfter)
                .submit()
                .thenApply(thread -> {
                    deleteRecentNotice(channel, message -> isCreationNoticeFor(message, thread));
                    return thread;
                });
    }

    private static void deleteRecentNotice(TextChannel channel, Predicate<Message> notice) {
        channel.getHistory()
                .retrievePast(NOTICE_SEARCH)
                .queue(
                        messages -> messages.stream()
                                .filter(notice)
                                .forEach(message -> message.delete().queue(Consumers.nop(), BotLogger::catchRestError)),
                        BotLogger::catchRestError);
    }

    private static boolean isCreationNoticeFor(Message message, ThreadChannel thread) {
        if (message.getType() != MessageType.THREAD_CREATED) return false;
        MessageReference reference = message.getMessageReference();
        return reference != null
                ? reference.getChannelIdLong() == thread.getIdLong()
                : thread.getName().equals(message.getContentRaw());
    }

    private static MessageEmbed linkedToThreads(MessageEmbed trade, ThreadChannel p1Thread, ThreadChannel p2Thread) {
        String links = Stream.of(p1Thread, p2Thread)
                .filter(Objects::nonNull)
                .map(ThreadChannel::getAsMention)
                .collect(Collectors.joining(" · "));
        return links.isEmpty()
                ? trade
                : new EmbedBuilder(trade)
                        .addField("Faction threads", links, false)
                        .build();
    }

    private static MessageEmbed linkedToOverview(MessageEmbed trade, Message posted) {
        return new EmbedBuilder(trade)
                .addField("Overview", "[Jump to this trade](" + posted.getJumpUrl() + ")", false)
                .build();
    }

    private static void sendIfPresent(ThreadChannel thread, List<MessageEmbed> embeds) {
        if (thread != null) send(thread, embeds);
    }

    private static void send(MessageChannel channel, List<MessageEmbed> embeds) {
        channel.sendMessageEmbeds(embeds).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static CompletableFuture<TextChannel> channel(Game game, List<List<MessageEmbed>> initialPanel) {
        TextChannel existing = channelOrNull(game);
        if (existing != null) return CompletableFuture.completedFuture(existing);
        Guild guild = game.getGuild();
        if (guild == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No server found for " + game.getName()));
        }
        TextChannel mainChannel = game.getMainGameChannel();
        Category category = mainChannel == null ? null : mainChannel.getParentCategory();
        Role gameRole = gameRole(game);
        ChannelAction<TextChannel> action = guild.createTextChannel(channelName(game), category);
        action = category != null ? action.syncPermissionOverrides() : withOverridesOf(action, mainChannel);
        if (gameRole != null) {
            action = action.addRolePermissionOverride(gameRole.getIdLong(), PLAYER_ALLOW, PLAYER_DENY);
        }
        return action.submit()
                .thenCompose(channel -> sendIntro(channel, gameRole)
                        .thenCompose(intro -> initialPanel == null
                                ? CompletableFuture.completedFuture(null)
                                : postPanelUnder(channel, intro, initialPanel))
                        .thenApply(_ -> channel));
    }

    private static CompletableFuture<Void> postPanelUnder(
            TextChannel channel, Message intro, List<List<MessageEmbed>> pages) {
        return channel.createThreadChannel(PANEL_THREAD, intro.getIdLong())
                .setAutoArchiveDuration(PANEL_ARCHIVE)
                .submit()
                .thenCompose(thread -> MegagameTradablesPanel.postNew(thread, pages))
                .exceptionally(error -> {
                    BotLogger.error("Could not post the Tradables panel in " + channel.getName(), error);
                    return null;
                });
    }

    private static ChannelAction<TextChannel> withOverridesOf(ChannelAction<TextChannel> action, TextChannel source) {
        if (source == null) return action;
        for (PermissionOverride override : source.getPermissionOverrides()) {
            action = override.isRoleOverride()
                    ? action.addRolePermissionOverride(
                            override.getIdLong(), override.getAllowedRaw(), override.getDeniedRaw())
                    : action.addMemberPermissionOverride(
                            override.getIdLong(), override.getAllowedRaw(), override.getDeniedRaw());
        }
        return action;
    }

    private static CompletableFuture<Message> sendIntro(TextChannel channel, Role gameRole) {
        MessageEmbed intro = new EmbedBuilder()
                .setTitle("Trade overview")
                .setDescription("Every ratified trade appears here, split by round and phase.\n"
                        + "Each faction has its own thread with its trades, split by round.\n"
                        + "The **" + PANEL_THREAD + "** thread under this message shows each faction's public"
                        + " supply, neighbours and debt, kept up to date.")
                .setColor(phaseColour(""))
                .build();
        return gameRole == null
                ? channel.sendMessageEmbeds(intro).submit()
                : channel.sendMessage(gameRole.getAsMention()).setEmbeds(intro).submit();
    }

    static TextChannel channelOrNull(Game game) {
        Guild guild = game.getGuild();
        if (guild == null) return null;
        return guild.getTextChannelsByName(channelName(game), true).stream()
                .findFirst()
                .orElse(null);
    }

    private static Role gameRole(Game game) {
        Guild guild = game.getGuild();
        if (guild == null) return null;
        List<Role> roles = guild.getRolesByName(game.getName(), true);
        return roles.size() == 1 ? roles.getFirst() : null;
    }

    private static String missingRoleNote(Game game, Role gameRole) {
        return gameRole == null
                ? "\nNo single role named `" + game.getName() + "` found, so players cannot see the channel yet."
                : "";
    }
}
