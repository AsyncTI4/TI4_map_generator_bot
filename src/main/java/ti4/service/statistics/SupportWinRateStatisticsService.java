package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    private static final String SUPPORTS_PURGED_KEY = "removeSupports";
    private static final int MINIMUM_FACTION_PLAYERS = 25;
    private static final int MINIMUM_PLAYERS_ON_EACH_SIDE_OF_THE_SPLIT = 10;

    private static final Comparator<Entry<String, FactionSupportStats>> BY_SUPPORT_GAP_DESC =
            Comparator.comparingDouble((Entry<String, FactionSupportStats> entry) ->
                            entry.getValue().supportGap())
                    .reversed()
                    .thenComparing(Entry::getKey);

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

        Set<String> supportsInPlayAreas = supportsInPlayAreas(seats);
        if (supportsAreOutOfPlay(game, supportsInPlayAreas)) {
            stats.gamesWithoutSupportsInPlay++;
            return;
        }
        stats.games++;

        Map<String, Player> supportOwners = supportOwnersInTheGame(game);
        Map<String, Set<String>> supportsHeldByFaction = supportsHeldByFaction(seats, supportOwners);
        Set<String> swappingFactions = swappingFactions(supportsHeldByFaction);
        int swaps = swappingFactions.size() / 2;

        stats.supportsPlayed +=
                supportsHeldByFaction.values().stream().mapToInt(Set::size).sum();
        stats.swaps += swaps;
        stats.gamesBySwapCount.merge(swaps, 1, Integer::sum);

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
                continue;
            }
            if (supportsInPlayAreas.contains(ownSupport)) {
                stats.gaveAway.record(isWinner);
                (swappingFactions.contains(faction) ? stats.inASwap : stats.gaveAwayOutsideASwap).record(isWinner);
            } else {
                stats.keptIt.record(isWinner);
            }
        }
    }

    private static boolean supportsAreOutOfPlay(Game game, Set<String> supportsInPlayAreas) {
        return supportsInPlayAreas.isEmpty() || "true".equalsIgnoreCase(game.getStoredValue(SUPPORTS_PURGED_KEY));
    }

    private static Set<String> supportsInPlayAreas(List<Player> seats) {
        return seats.stream()
                .flatMap(player -> player.getPromissoryNotesInPlayArea().stream())
                .filter(SupportWinRateStatisticsService::isSupport)
                .collect(Collectors.toCollection(HashSet::new));
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
                if (owner != null) {
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

    private static List<String> buildReport(SupportStats stats) {
        List<String> blocks = new ArrayList<>();

        StringBuilder header = new StringBuilder("## __**Support for the Throne Win Rates**__\n");
        header.append("_6-player, 10-victory-point, non-homebrew, non-Galactic-Event, non-Scenario games with"
                + " winners._\n");
        if (stats.players == 0) {
            header.append("\nNo games matched.\n");
            appendGamesWithoutSupportsInPlay(header, stats);
            blocks.add(header.toString());
            return blocks;
        }
        header.append("Games analyzed: ")
                .append(stats.games)
                .append(" | Players analyzed: ")
                .append(stats.players)
                .append('\n');
        appendGamesWithoutSupportsInPlay(header, stats);
        blocks.add(header.toString());

        appendSupportsHeldSection(blocks, stats);
        appendSupportLocationSection(blocks, stats);
        appendSwapSection(blocks, stats);
        appendFactionSection(blocks, stats);

        return blocks;
    }

    private static void appendGamesWithoutSupportsInPlay(StringBuilder header, SupportStats stats) {
        if (stats.gamesWithoutSupportsInPlay == 0) {
            return;
        }
        header.append("Dropped ")
                .append(stats.gamesWithoutSupportsInPlay)
                .append(" game(s) that purged Support for the Throne or never played one.\n");
    }

    private static void appendSupportsHeldSection(List<String> blocks, SupportStats stats) {
        blocks.add("### Win rate by supports held\n"
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

    private static void appendSupportLocationSection(List<String> blocks, SupportStats stats) {
        blocks.add("### Win rate by support location\n");

        int playersWithASupport = stats.keptIt.getPlayers() + stats.gaveAway.getPlayers();
        if (playersWithASupport == 0) {
            blocks.add("- No player owned a Support for the Throne.\n");
            return;
        }
        StringBuilder sb = new StringBuilder();
        appendLocationLine(sb, "Kept it", stats.keptIt, playersWithASupport);
        appendLocationLine(sb, "Gave away", stats.gaveAway, playersWithASupport);
        blocks.add(sb.toString());
    }

    private static void appendLocationLine(
            StringBuilder sb, String label, WinRateCount count, int playersWithASupport) {
        sb.append("- ")
                .append(label)
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

    private static void appendSwapSection(List<String> blocks, SupportStats stats) {
        blocks.add("### Support swaps\n"
                + "_Two players who each ended the game holding the other's Support for the Throne._\n");

        StringBuilder sb = new StringBuilder("- Swaps per game: ");
        sb.append(String.format("%.2f", stats.swaps / (double) stats.games)).append(" on average\n");
        stats.gamesBySwapCount.forEach((swaps, games) ->
                appendSwapCountLine(sb, swaps + (swaps == 1 ? " swap" : " swaps"), games, stats.games));
        int gamesWithASwap = stats.gamesBySwapCount.entrySet().stream()
                .filter(entry -> entry.getKey() > 0)
                .mapToInt(Entry::getValue)
                .sum();
        appendSwapCountLine(sb, "1+ swaps", gamesWithASwap, stats.games);
        appendSwapRate(sb, stats);
        appendSwapWinRates(sb, stats);
        blocks.add(sb.toString());
    }

    private static void appendSwapCountLine(StringBuilder sb, String label, int games, int totalGames) {
        sb.append("  - ")
                .append(label)
                .append(": ")
                .append(games)
                .append(" game(s) (")
                .append(ActionCardStatsService.formatPercent(games / (double) totalGames))
                .append(")\n");
    }

    private static void appendSwapRate(StringBuilder sb, SupportStats stats) {
        sb.append("- Swap rate: ");
        if (stats.supportsPlayed == 0) {
            sb.append("no supports were played at all\n");
            return;
        }
        sb.append(ActionCardStatsService.formatPercent(2.0 * stats.swaps / stats.supportsPlayed))
                .append(" (")
                .append(2 * stats.swaps)
                .append('/')
                .append(stats.supportsPlayed)
                .append(" of the supports played)\n");
    }

    private static void appendSwapWinRates(StringBuilder sb, SupportStats stats) {
        if (stats.inASwap.getPlayers() == 0 && stats.gaveAwayOutsideASwap.getPlayers() == 0) {
            return;
        }
        sb.append("- Win rate after giving a support away:\n");
        appendSwapWinRateLine(sb, "Swap", stats.inASwap);
        appendSwapWinRateLine(sb, "No swap", stats.gaveAwayOutsideASwap);
    }

    private static void appendSwapWinRateLine(StringBuilder sb, String label, WinRateCount count) {
        sb.append("  - ")
                .append(label)
                .append(": ")
                .append(ActionCardStatsService.formatPercent(count.getWinRate()))
                .append(" (")
                .append(count.getWins())
                .append('/')
                .append(count.getPlayers())
                .append(")\n");
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

        final WinRateCount keptIt = new WinRateCount();

        final WinRateCount gaveAway = new WinRateCount();

        final FactionSupportStats overall = new FactionSupportStats();

        final Map<String, FactionSupportStats> byFaction = new HashMap<>();

        final NavigableMap<Integer, Integer> gamesBySwapCount = new TreeMap<>();

        final WinRateCount inASwap = new WinRateCount();

        final WinRateCount gaveAwayOutsideASwap = new WinRateCount();

        int games;
        int gamesWithoutSupportsInPlay;
        int players;
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
