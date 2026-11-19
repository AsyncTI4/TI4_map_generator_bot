package ti4.service.megagame;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.HydromorphicHelper;
import ti4.image.playerarea.NeighbourMap;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.ColorModel;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class MegagameTradablesPanel {

    static final String CONTENT_PREFIX = "**Tradables**";
    private static final String SEPARATOR = " · ";
    private static final int MAX_MESSAGE_EMBED_CHARS = 5500;
    private static final int HISTORY_TO_SEARCH = 25;
    private static final long REFRESH_DELAY_SECONDS = 30;
    private static final long NEIGHBOUR_TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final Color FALLBACK_COLOUR = new Color(149, 165, 166);
    private static final ScheduledExecutorService REFRESHER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("megagame-tradables-panel").factory());
    private static final Map<String, List<List<MessageEmbed>>> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_BUILT = new ConcurrentHashMap<>();
    private static final Map<String, NeighbourSnapshot> NEIGHBOURS = new ConcurrentHashMap<>();

    private record NeighbourSnapshot(
            long builtAt, int round, String phase, Map<String, String> neighbours, Map<String, String> partners) {}

    public static void onSave(Game game) {
        if (!MegagameTradeOverviewService.isActive(game) || game.isHasEnded()) {
            forget(game.getName());
            return;
        }
        try {
            if (MegagameTradeOverviewService.channelOrNull(game) == null
                    && !MegagameTradeOverviewService.hasQueuedPosts(game)) {
                forget(game.getName());
                return;
            }
            List<List<MessageEmbed>> pages = pages(game);
            String fingerprint = fingerprint(pages);
            if (fingerprint.equals(LAST_BUILT.put(game.getName(), fingerprint))) return;
            if (PENDING.put(game.getName(), pages) == null) {
                REFRESHER.schedule(() -> flush(game), REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to queue the megagame tradables panel.", e);
        }
    }

    public static void refreshNow(Game game) {
        List<List<MessageEmbed>> pages = pages(game);
        LAST_BUILT.put(game.getName(), fingerprint(pages));
        PENDING.remove(game.getName());
        MegagameTradeOverviewService.enqueueForExistingChannel(
                game, "refresh the tradables panel", channel -> replacePanel(channel, pages));
    }

    static CompletableFuture<Void> postNew(ThreadChannel thread, List<List<MessageEmbed>> pages) {
        return sendPages(thread, pages);
    }

    private static void forget(String gameName) {
        if (gameName == null) return;
        PENDING.remove(gameName);
        LAST_BUILT.remove(gameName);
        NEIGHBOURS.remove(gameName);
    }

    static List<List<MessageEmbed>> pages(Game game) {
        NeighbourSnapshot neighbours = neighbours(game);
        List<MessageEmbed> embeds = game.getRealPlayers().stream()
                .map(player -> factionEmbed(game, player, neighbours))
                .toList();
        return paginate(embeds);
    }

    static List<List<MessageEmbed>> paginate(List<MessageEmbed> embeds) {
        List<List<MessageEmbed>> pages = new ArrayList<>();
        List<MessageEmbed> page = new ArrayList<>();
        int pageChars = 0;
        for (MessageEmbed embed : embeds) {
            boolean full =
                    page.size() == Message.MAX_EMBED_COUNT || pageChars + embed.getLength() > MAX_MESSAGE_EMBED_CHARS;
            if (full && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
                pageChars = 0;
            }
            page.add(embed);
            pageChars += embed.getLength();
        }
        if (!page.isEmpty()) pages.add(page);
        return pages;
    }

    private static MessageEmbed factionEmbed(Game game, Player player, NeighbourSnapshot neighbours) {
        List<String> lines = new ArrayList<>();
        lines.add(player.getFactionEmoji() + " **" + MegagameTradeOverviewService.shortName(player) + "**");
        lines.add(supplyLine(game, player));
        lines.add(neighbourLine(neighbours, player));
        debtLine("Holds", heldDebt(game, player)).ifPresent(lines::add);
        debtLine("Owes", owedDebt(game, player)).ifPresent(lines::add);
        return new EmbedBuilder()
                .setDescription(String.join("\n", lines))
                .setColor(colour(player))
                .build();
    }

    private static String supplyLine(Game game, Player player) {
        StringBuilder cards = new StringBuilder();
        cards.append(CardEmojis.SecretObjective).append(player.getSo());
        cards.append(' ').append(CardEmojis.getACEmoji(game)).append(player.getAcCount());
        if (HydromorphicHelper.shouldShowInPlayerArea(player)) {
            cards.append(' ')
                    .append(CardEmojis.HydromorphicCard)
                    .append(player.getHydromorphicCards().size());
        }
        if (!game.isTwilightsFallMode()) {
            cards.append(' ').append(CardEmojis.PN).append(player.getPnCount());
        }
        List<String> parts = new ArrayList<>();
        parts.add(cards.toString());
        parts.add(MiscEmojis.getTGorNomadCoinEmoji(game) + player.getTg());
        parts.add(MiscEmojis.comm + "" + player.getCommodities() + "/" + player.getCommoditiesTotal());
        String fragments = fragments(player);
        if (!fragments.isEmpty()) parts.add(fragments);
        return String.join(SEPARATOR, parts);
    }

    private static String fragments(Player player) {
        List<String> parts = new ArrayList<>();
        if (player.getCrf() > 0) parts.add(ExploreEmojis.CFrag + "" + player.getCrf());
        if (player.getIrf() > 0) parts.add(ExploreEmojis.IFrag + "" + player.getIrf());
        if (player.getHrf() > 0) parts.add(ExploreEmojis.HFrag + "" + player.getHrf());
        if (player.getUrf() > 0) parts.add(ExploreEmojis.UFrag + "" + player.getUrf());
        return String.join(" ", parts);
    }

    private static String neighbourLine(NeighbourSnapshot snapshot, Player player) {
        String neighbours = snapshot.neighbours().getOrDefault(player.getFaction(), "");
        String partners = snapshot.partners().getOrDefault(player.getFaction(), "");
        String line = "Neighbours: " + (neighbours.isEmpty() ? "none" : neighbours);
        return partners.isEmpty() ? line : line + " (" + partners + ")";
    }

    private static Map<String, Integer> heldDebt(Game game, Player holder) {
        Map<String, Integer> held = new LinkedHashMap<>();
        holder.getAllDebtTokens()
                .forEach((pool, tokens) -> tokens.forEach((colour, count) -> {
                    if (count > 0) {
                        held.merge(poolPrefix(pool) + debtorLabel(game, colour), count, Integer::sum);
                    }
                }));
        return held;
    }

    private static Map<String, Integer> owedDebt(Game game, Player debtor) {
        String debtorColourId = Mapper.getColorID(debtor.getColor());
        Map<String, Integer> owed = new LinkedHashMap<>();
        for (Player holder : game.getRealPlayers()) {
            if (holder == debtor) continue;
            holder.getAllDebtTokens()
                    .forEach((pool, tokens) -> tokens.forEach((colour, count) -> {
                        if (count > 0 && debtorColourId != null && debtorColourId.equals(Mapper.getColorID(colour))) {
                            owed.merge(poolPrefix(pool) + holder.getFactionEmoji(), count, Integer::sum);
                        }
                    }));
        }
        return owed;
    }

    private static String poolPrefix(String pool) {
        return Constants.DEBT_DEFAULT_POOL.equalsIgnoreCase(pool) ? "" : StringUtils.capitalize(pool) + ": ";
    }

    private static String debtorLabel(Game game, String colour) {
        return game.getPlayerByColorID(Mapper.getColorID(colour))
                .map(Player::getFactionEmoji)
                .orElseGet(() -> ColorEmojis.getColorEmojiWithName(colour));
    }

    private static Optional<String> debtLine(String label, Map<String, Integer> debts) {
        if (debts.isEmpty()) return Optional.empty();
        return Optional.of(label + ": "
                + debts.entrySet().stream()
                        .map(debt -> debt.getKey() + "×" + debt.getValue())
                        .collect(Collectors.joining(" ")));
    }

    private static Color colour(Player player) {
        ColorModel model = Mapper.getColor(player.getColor());
        return model == null || model.getPrimaryColor() == null ? FALLBACK_COLOUR : model.getPrimaryColor();
    }

    private static NeighbourSnapshot neighbours(Game game) {
        NeighbourSnapshot cached = NEIGHBOURS.get(game.getName());
        long now = System.currentTimeMillis();
        if (cached != null
                && now - cached.builtAt() < NEIGHBOUR_TTL_MILLIS
                && cached.round() == game.getRound()
                && Objects.equals(cached.phase(), game.getPhaseOfGame())) {
            return cached;
        }
        NeighbourMap map = new NeighbourMap(game);
        Map<String, String> neighbours = new LinkedHashMap<>();
        Map<String, String> partners = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) {
            Set<Player> direct = map.withEquivalents(player);
            neighbours.put(player.getFaction(), emojis(direct));
            partners.put(player.getFaction(), partnerText(game, player, direct, map.guildShipPartners(player)));
        }
        NeighbourSnapshot snapshot =
                new NeighbourSnapshot(now, game.getRound(), game.getPhaseOfGame(), neighbours, partners);
        NEIGHBOURS.put(game.getName(), snapshot);
        return snapshot;
    }

    private static String partnerText(Game game, Player player, Set<Player> neighbours, Set<Player> partners) {
        if (partners.isEmpty()) return "";
        long others = game.getRealPlayers().stream()
                .filter(other -> other != player && !neighbours.contains(other))
                .count();
        return partners.size() == others ? "can transact with everyone" : emojis(partners);
    }

    private static String emojis(Set<Player> players) {
        return players.stream()
                .filter(player -> player.getFaction() != null)
                .map(Player::getFactionEmoji)
                .collect(Collectors.joining(""));
    }

    private static String fingerprint(List<List<MessageEmbed>> pages) {
        return pages.stream()
                .flatMap(List::stream)
                .map(embed -> embed.getDescription() + "|" + embed.getColorRaw())
                .collect(Collectors.joining("\n"));
    }

    private static void flush(Game game) {
        List<List<MessageEmbed>> pages = PENDING.remove(game.getName());
        if (pages == null) return;
        MegagameTradeOverviewService.enqueueForExistingChannel(
                game, "refresh the tradables panel", channel -> replacePanel(channel, pages));
    }

    private static CompletableFuture<Void> replacePanel(TextChannel channel, List<List<MessageEmbed>> pages) {
        return MegagameTradeOverviewService.panelThread(channel).thenCompose(thread -> replacePanel(thread, pages));
    }

    private static CompletableFuture<Void> replacePanel(ThreadChannel thread, List<List<MessageEmbed>> pages) {
        return MessageHistory.getHistoryFromBeginning(thread)
                .limit(HISTORY_TO_SEARCH)
                .submit()
                .thenCompose(history -> {
                    List<Message> existing = panelMessages(thread, history.getRetrievedHistory());
                    return existing.isEmpty() ? sendPages(thread, pages) : editPages(thread, existing, pages);
                });
    }

    private static List<Message> panelMessages(ThreadChannel thread, List<Message> history) {
        long selfId = thread.getJDA().getSelfUser().getIdLong();
        return history.stream()
                .filter(message -> message.getAuthor().getIdLong() == selfId)
                .filter(message -> message.getContentRaw().startsWith(CONTENT_PREFIX))
                .sorted(Comparator.comparingLong(Message::getIdLong))
                .toList();
    }

    private static CompletableFuture<Void> editPages(
            ThreadChannel thread, List<Message> existing, List<List<MessageEmbed>> pages) {
        List<CompletableFuture<?>> edits = new ArrayList<>();
        for (int i = 0; i < Math.max(existing.size(), pages.size()); i++) {
            if (i >= pages.size()) {
                edits.add(existing.get(i).delete().submit());
            } else if (i >= existing.size()) {
                edits.add(sendPage(thread, i, pages));
            } else {
                edits.add(existing.get(i)
                        .editMessage(content(i, pages.size()))
                        .setEmbeds(pages.get(i))
                        .submit());
            }
        }
        return CompletableFuture.allOf(edits.toArray(CompletableFuture[]::new));
    }

    private static CompletableFuture<Void> sendPages(ThreadChannel thread, List<List<MessageEmbed>> pages) {
        List<CompletableFuture<Message>> sends = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            sends.add(sendPage(thread, i, pages));
        }
        return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new));
    }

    private static CompletableFuture<Message> sendPage(ThreadChannel thread, int page, List<List<MessageEmbed>> pages) {
        return thread.sendMessage(content(page, pages.size()))
                .setEmbeds(pages.get(page))
                .submit();
    }

    static String content(int page, int pageCount) {
        String pageLabel = pageCount > 1 ? " (" + (page + 1) + "/" + pageCount + ")" : "";
        return CONTENT_PREFIX + pageLabel + "\n-# Public info only, as shown on the map. Updated <t:"
                + System.currentTimeMillis() / 1000 + ":R>";
    }
}
