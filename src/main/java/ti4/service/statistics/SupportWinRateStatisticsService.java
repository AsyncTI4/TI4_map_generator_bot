package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@UtilityClass
public class SupportWinRateStatisticsService {

    private static final String SUPPORT_SUFFIX = "_sftt";
    private static final int MINIMUM_FACTION_PLAYERS = 25;
    private static final int MINIMUM_PLAYERS_ON_EACH_SIDE_OF_THE_SPLIT = 10;

    private static final Comparator<Entry<String, FactionSupportStats>> BY_SUPPORT_GAP_DESC =
            Comparator.comparingDouble((Entry<String, FactionSupportStats> entry) ->
                            entry.getValue().supportGap())
                    .reversed()
                    .thenComparing(Entry::getKey);

    private enum SupportFate {
        KEPT("Kept it"),
        SCORED_BY_ANOTHER("Given away and played for the point"),
        IN_ANOTHER_HAND("Given away but never played"),
        OFF_THE_TABLE("No longer anywhere in the game");

        private final String label;

        SupportFate(String label) {
            this.label = label;
        }
    }

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showSupportWinRates(event));
    }

    private static void showSupportWinRates(SlashCommandInteractionEvent event) {
        SupportStats stats = new SupportStats();
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter(),
                game -> accumulateGame(game, stats),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(event.getChannel(), "Support for the Throne win rates", buildReport(stats));
    }

    static List<String> buildReport(List<Game> games) {
        SupportStats stats = new SupportStats();
        games.forEach(game -> accumulateGame(game, stats));
        return buildReport(stats);
    }

    private static void accumulateGame(Game game, SupportStats stats) {
        Player winner = game.getWinner().orElse(null);
        if (winner == null) {
            return;
        }
        List<Player> seats = game.getRealAndEliminatedPlayers().stream()
                .filter(player -> StringUtils.isNotBlank(player.getFaction()))
                .toList();
        if (seats.isEmpty()) {
            return;
        }
        stats.games++;

        Map<String, Player> supportOwners = supportOwnersInTheGame(game);
        Map<String, Player> playAreaHolders = supportsIn(seats, Player::getPromissoryNotesInPlayArea);
        Map<String, Player> handHolders =
                supportsIn(seats, player -> player.getPromissoryNotes().keySet());
        Map<String, Set<String>> supportsHeldByFaction = supportsHeldByFaction(seats, supportOwners);
        Set<String> swappingFactions = swappingFactions(supportsHeldByFaction);

        stats.supportsPlayed +=
                supportsHeldByFaction.values().stream().mapToInt(Set::size).sum();
        stats.swaps += swappingFactions.size() / 2;
        stats.gamesBySwapCount.merge(swappingFactions.size() / 2, 1, Integer::sum);

        for (Player player : seats) {
            String faction = player.getFaction();
            boolean isWinner = faction.equals(winner.getFaction());
            int supportsHeld = supportsHeldByFaction.get(faction).size();

            stats.players++;
            stats.supportsHeldAtTheirRealCounts += supportsHeld;
            stats.playersBySupportsHeld
                    .computeIfAbsent(supportsHeld, _ -> new WinRateCount())
                    .record(isWinner);

            stats.overall.record(supportsHeld > 0, isWinner);
            for (String factionKey : FactionStatisticsHelper.getStatisticsFactionKeys(faction)) {
                stats.byFaction
                        .computeIfAbsent(factionKey, _ -> new FactionSupportStats())
                        .record(supportsHeld > 0, isWinner);
            }

            String ownSupport = ownSupport(player);
            if (ownSupport == null) {
                stats.playersWithoutASupport++;
                continue;
            }
            SupportFate fate = fateOf(ownSupport, player, playAreaHolders, handHolders);
            stats.playersByFate.computeIfAbsent(fate, _ -> new WinRateCount()).record(isWinner);
            if (fate == SupportFate.SCORED_BY_ANOTHER) {
                (swappingFactions.contains(faction) ? stats.inASwap : stats.gaveAwayOutsideASwap).record(isWinner);
            }
        }
    }

    private static Map<String, Player> supportOwnersInTheGame(Game game) {
        Map<String, Player> supportOwners = new HashMap<>();
        for (Player player : game.getPlayers().values()) {
            player.getPromissoryNotesOwned().stream()
                    .filter(SupportWinRateStatisticsService::isSupport)
                    .forEach(support -> supportOwners.put(support, player));
        }
        return supportOwners;
    }

    private static Map<String, Player> supportsIn(
            List<Player> seats, Function<Player, Collection<String>> promissoryNotes) {
        Map<String, Player> holders = new HashMap<>();
        for (Player player : seats) {
            promissoryNotes.apply(player).stream()
                    .filter(SupportWinRateStatisticsService::isSupport)
                    .forEach(support -> holders.put(support, player));
        }
        return holders;
    }

    private static Map<String, Set<String>> supportsHeldByFaction(
            List<Player> seats, Map<String, Player> supportOwners) {
        Map<String, Set<String>> supportsHeldByFaction = new HashMap<>();
        for (Player player : seats) {
            Set<String> givers = new HashSet<>();
            for (String support : player.getPromissoryNotesInPlayArea()) {
                if (!isSupport(support)) {
                    continue;
                }
                Player owner = supportOwners.get(support);
                if (owner != null && !isSameSeat(owner, player)) {
                    givers.add(owner.getFaction());
                }
            }
            supportsHeldByFaction.put(player.getFaction(), givers);
        }
        return supportsHeldByFaction;
    }

    private static Set<String> swappingFactions(Map<String, Set<String>> supportsHeldByFaction) {
        Set<String> swappers = new HashSet<>();
        for (Entry<String, Set<String>> holder : supportsHeldByFaction.entrySet()) {
            for (String giver : holder.getValue()) {
                if (supportsHeldByFaction.getOrDefault(giver, Set.of()).contains(holder.getKey())) {
                    swappers.add(holder.getKey());
                }
            }
        }
        return swappers;
    }

    private static boolean isSupport(String promissoryNoteId) {
        return promissoryNoteId != null && promissoryNoteId.endsWith(SUPPORT_SUFFIX);
    }

    private static String ownSupport(Player player) {
        return player.getPromissoryNotesOwned().stream()
                .filter(SupportWinRateStatisticsService::isSupport)
                .findFirst()
                .orElse(null);
    }

    private static SupportFate fateOf(
            String ownSupport, Player owner, Map<String, Player> playAreaHolders, Map<String, Player> handHolders) {
        Player playAreaHolder = playAreaHolders.get(ownSupport);
        if (playAreaHolder != null) {
            return isSameSeat(playAreaHolder, owner) ? SupportFate.KEPT : SupportFate.SCORED_BY_ANOTHER;
        }
        Player handHolder = handHolders.get(ownSupport);
        if (handHolder != null) {
            return isSameSeat(handHolder, owner) ? SupportFate.KEPT : SupportFate.IN_ANOTHER_HAND;
        }
        return SupportFate.OFF_THE_TABLE;
    }

    private static boolean isSameSeat(Player one, Player other) {
        return one.getFaction() != null && one.getFaction().equals(other.getFaction());
    }

    private static List<String> buildReport(SupportStats stats) {
        List<String> blocks = new ArrayList<>();

        StringBuilder header = new StringBuilder("## __**Support for the Throne Win Rates**__\n");
        header.append("_Where every Support for the Throne sat at the end of the game. A player's own support in"
                + " their own play area is nobody's point, so it never counts as one held._\n");
        header.append("_6-player, 10-victory-point, non-homebrew, non-Galactic-Event, non-Scenario games with"
                + " winners._\n");
        if (stats.players == 0) {
            header.append("\nNo games matched.\n");
            blocks.add(header.toString());
            return blocks;
        }
        header.append("Games analyzed: ")
                .append(stats.games)
                .append(" | Players analyzed: ")
                .append(stats.players)
                .append('\n');
        blocks.add(header.toString());

        appendSupportsHeldSection(blocks, stats);
        appendOwnSupportSection(blocks, stats);
        appendFactionSection(blocks, stats);
        appendSwapSection(blocks, stats);

        return blocks;
    }

    private static void appendSupportsHeldSection(List<String> blocks, SupportStats stats) {
        blocks.add("### Win rate by supports held\n"
                + "_Another player's Support for the Throne in your play area at the end of the game, one victory"
                + " point each._\n"
                + "_Each row reads: win rate (wins/players; share of players who held that many)._\n");

        StringBuilder sb = new StringBuilder("- **All players**: ");
        sb.append(String.format("%.2f", stats.averageSupportsHeld())).append(" supports held on average, from ");
        ActionCardStatsService.appendCount(sb, stats.players, "player");
        sb.append('\n');
        stats.playersBySupportsHeld.forEach((supportsHeld, count) -> {
            sb.append("  - ")
                    .append(supportsHeld)
                    .append(supportsHeld == 1 ? " support: " : " supports: ")
                    .append(ActionCardStatsService.formatPercent(count.getWinRate()))
                    .append(" (")
                    .append(count.getWins())
                    .append('/')
                    .append(count.getPlayers())
                    .append("; ")
                    .append(ActionCardStatsService.formatPercent(count.getPlayers() / (double) stats.players))
                    .append(")\n");
        });
        blocks.add(sb.toString());
    }

    private static void appendOwnSupportSection(List<String> blocks, SupportStats stats) {
        StringBuilder heading = new StringBuilder("### Win rate by what became of your own support\n");
        heading.append("_The card the player started with, wherever it ended up._\n");
        if (stats.playersWithoutASupport > 0) {
            heading.append('_')
                    .append(stats.playersWithoutASupport)
                    .append(" player(s) owned no Support for the Throne at all and are left out of this section._\n");
        }
        blocks.add(heading.toString());

        int playersWithASupport = stats.playersByFate.values().stream()
                .mapToInt(WinRateCount::getPlayers)
                .sum();
        if (playersWithASupport == 0) {
            blocks.add("- No player owned a Support for the Throne.\n");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (SupportFate fate : SupportFate.values()) {
            WinRateCount count = stats.playersByFate.get(fate);
            if (count == null) {
                continue;
            }
            sb.append("- ")
                    .append(fate.label)
                    .append(": ")
                    .append(ActionCardStatsService.formatPercent(count.getWinRate()))
                    .append(" win rate (")
                    .append(count.getWins())
                    .append('/')
                    .append(count.getPlayers())
                    .append("; ")
                    .append(ActionCardStatsService.formatPercent(count.getPlayers() / (double) playersWithASupport))
                    .append(" of players)\n");
        }
        blocks.add(sb.toString());
    }

    private static void appendFactionSection(List<String> blocks, SupportStats stats) {
        blocks.add("### Win rate holding another player's support, by faction\n"
                + "_Factions with at least " + MINIMUM_FACTION_PLAYERS + " players in the sample and at least "
                + MINIMUM_PLAYERS_ON_EACH_SIDE_OF_THE_SPLIT
                + " on each side of the split. Sorted by the size of the gap._\n");

        blocks.add(renderFactionLine("**All factions**", stats.overall));

        List<Entry<String, FactionSupportStats>> ranked = stats.byFaction.entrySet().stream()
                .filter(entry -> entry.getValue().isWellSampled())
                .sorted(BY_SUPPORT_GAP_DESC)
                .toList();
        if (ranked.isEmpty()) {
            blocks.add("- No faction had enough players on both sides of the split.\n");
            return;
        }
        ranked.forEach(entry -> blocks.add(renderFactionLine(factionLabel(entry.getKey()), entry.getValue())));
    }

    private static String renderFactionLine(String label, FactionSupportStats group) {
        return new StringBuilder("- ")
                .append(label)
                .append(": ")
                .append(formatPercentagePointGap(group.supportGap()))
                .append(" - ")
                .append(ActionCardStatsService.formatPercent(group.withASupport.getWinRate()))
                .append(" (")
                .append(group.withASupport.getWins())
                .append('/')
                .append(group.withASupport.getPlayers())
                .append(") holding one, ")
                .append(ActionCardStatsService.formatPercent(group.withoutASupport.getWinRate()))
                .append(" (")
                .append(group.withoutASupport.getWins())
                .append('/')
                .append(group.withoutASupport.getPlayers())
                .append(") holding none\n")
                .toString();
    }

    private static void appendSwapSection(List<String> blocks, SupportStats stats) {
        blocks.add("### Support swaps\n"
                + "_Two players who each ended the game holding the other's Support for the Throne._\n");

        int gamesWithASwap = stats.gamesBySwapCount.entrySet().stream()
                .filter(entry -> entry.getKey() > 0)
                .mapToInt(Entry::getValue)
                .sum();
        StringBuilder sb = new StringBuilder("- Games with at least one swap: ");
        sb.append(gamesWithASwap)
                .append('/')
                .append(stats.games)
                .append(" (")
                .append(ActionCardStatsService.formatPercent(gamesWithASwap / (double) stats.games))
                .append(")\n");
        sb.append("- Swaps per game: ")
                .append(String.format("%.2f", stats.swaps / (double) stats.games))
                .append(" on average\n");
        stats.gamesBySwapCount.forEach((swaps, games) -> sb.append("  - ")
                .append(swaps)
                .append(swaps == 1 ? " swap: " : " swaps: ")
                .append(games)
                .append(" game(s) (")
                .append(ActionCardStatsService.formatPercent(games / (double) stats.games))
                .append(")\n"));
        appendReciprocalSupports(sb, stats);
        appendSwapWinRates(sb, stats);
        blocks.add(sb.toString());
    }

    private static void appendReciprocalSupports(StringBuilder sb, SupportStats stats) {
        sb.append("- Supports played into a swap: ");
        if (stats.supportsPlayed == 0) {
            sb.append("no supports were played at all\n");
            return;
        }
        sb.append(2 * stats.swaps)
                .append('/')
                .append(stats.supportsPlayed)
                .append(" (")
                .append(ActionCardStatsService.formatPercent(2.0 * stats.swaps / stats.supportsPlayed))
                .append(" of the supports played)\n");
    }

    private static void appendSwapWinRates(StringBuilder sb, SupportStats stats) {
        if (stats.inASwap.getPlayers() == 0 && stats.gaveAwayOutsideASwap.getPlayers() == 0) {
            return;
        }
        sb.append("- Win rate after giving a support away: ")
                .append(ActionCardStatsService.formatPercent(stats.inASwap.getWinRate()))
                .append(" (")
                .append(stats.inASwap.getWins())
                .append('/')
                .append(stats.inASwap.getPlayers())
                .append(") in a swap, ")
                .append(ActionCardStatsService.formatPercent(stats.gaveAwayOutsideASwap.getWinRate()))
                .append(" (")
                .append(stats.gaveAwayOutsideASwap.getWins())
                .append('/')
                .append(stats.gaveAwayOutsideASwap.getPlayers())
                .append(") outside one\n");
    }

    private static String formatPercentagePointGap(double gap) {
        return String.format("%+.1f pts", gap * 100);
    }

    private static String factionLabel(String faction) {
        FactionModel factionModel = Mapper.getFaction(faction);
        String factionName = factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : faction;
        return FactionStatisticsHelper.getFactionEmoji(faction) + " **" + factionName + "**";
    }

    private static class SupportStats {
        final NavigableMap<Integer, WinRateCount> playersBySupportsHeld = new TreeMap<>();

        final Map<SupportFate, WinRateCount> playersByFate = new HashMap<>();

        final FactionSupportStats overall = new FactionSupportStats();

        final Map<String, FactionSupportStats> byFaction = new HashMap<>();

        final NavigableMap<Integer, Integer> gamesBySwapCount = new TreeMap<>();

        final WinRateCount inASwap = new WinRateCount();

        final WinRateCount gaveAwayOutsideASwap = new WinRateCount();

        int games;
        int players;
        int playersWithoutASupport;
        int supportsHeldAtTheirRealCounts;
        int supportsPlayed;
        int swaps;

        double averageSupportsHeld() {
            return players == 0 ? 0 : (double) supportsHeldAtTheirRealCounts / players;
        }
    }

    private static class FactionSupportStats {
        final WinRateCount withASupport = new WinRateCount();

        final WinRateCount withoutASupport = new WinRateCount();

        void record(boolean heldASupport, boolean isWinner) {
            (heldASupport ? withASupport : withoutASupport).record(isWinner);
        }

        boolean isWellSampled() {
            return withASupport.getPlayers() + withoutASupport.getPlayers() >= MINIMUM_FACTION_PLAYERS
                    && withASupport.getPlayers() >= MINIMUM_PLAYERS_ON_EACH_SIDE_OF_THE_SPLIT
                    && withoutASupport.getPlayers() >= MINIMUM_PLAYERS_ON_EACH_SIDE_OF_THE_SPLIT;
        }

        double supportGap() {
            return withASupport.getWinRate() - withoutASupport.getWinRate();
        }
    }

    @Getter
    private static class WinRateCount {
        private int players;
        private int wins;

        void record(boolean isWinner) {
            players++;
            if (isWinner) {
                wins++;
            }
        }

        double getWinRate() {
            return players == 0 ? 0 : (double) wins / players;
        }
    }
}
