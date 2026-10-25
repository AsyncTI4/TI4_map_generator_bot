package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.WinningPathBreakdown;
import ti4.service.statistics.game.WinningPathHelper;

@UtilityClass
public class ScoringStatisticsService {

    private static final int TOP_PATH_COUNT = 5;

    private static final Comparator<Map.Entry<WinningPathBreakdown, Long>> MOST_COMMON_FIRST =
            Map.Entry.<WinningPathBreakdown, Long>comparingByValue()
                    .reversed()
                    .thenComparing(entry -> WinningPathHelper.describe(entry.getKey()));

    private static final ScoreType STAGE_1S =
            new ScoreType("Stage 1 objectives", WinningPathBreakdown::stage1s, "stage 1", "stage 1s");
    private static final ScoreType STAGE_2S =
            new ScoreType("Stage 2 objectives", WinningPathBreakdown::stage2s, "stage 2", "stage 2s");
    private static final ScoreType SECRETS =
            new ScoreType("Secret objectives", WinningPathBreakdown::secrets, "secret objective", "secret objectives");
    private static final ScoreType SUPPORTS = new ScoreType(
            "Support for the Throne",
            WinningPathBreakdown::supports,
            "_Support for the Throne_",
            "_Supports for the Thrones_");
    private static final List<ScoreType> CORE_SCORE_TYPES = List.of(STAGE_1S, STAGE_2S, SECRETS, SUPPORTS);

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showScoring(event));
    }

    private static void showScoring(SlashCommandInteractionEvent event) {
        List<WinningPathBreakdown> winningPaths = new ArrayList<>();
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter(),
                game -> game.getWinner()
                        .ifPresent(winner -> winningPaths.add(WinningPathHelper.breakDownWinningPath(game, winner))),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(event.getChannel(), "Scoring statistics", buildReport(winningPaths));
    }

    static String buildReport(List<WinningPathBreakdown> winningPaths) {
        StringBuilder report = new StringBuilder("## __**Scoring statistics**__\n");
        report.append("_6-player, 10-victory-point, non-homebrew, non-Galactic-Event, non-Scenario games with"
                + " winners._\n");
        if (winningPaths.isEmpty()) {
            return report.append("No games matched.\n").toString();
        }
        report.append("Winners analyzed: ").append(winningPaths.size()).append('\n');

        List<Map.Entry<WinningPathBreakdown, Long>> topPaths = topPaths(winningPaths);
        appendTopPaths(report, topPaths, winningPaths.size());
        appendScoreTypeBreakdowns(report, scoreTypesInPaths(topPaths), winningPaths);
        return report.toString();
    }

    private static List<Map.Entry<WinningPathBreakdown, Long>> topPaths(List<WinningPathBreakdown> winningPaths) {
        return winningPaths.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(MOST_COMMON_FIRST)
                .limit(TOP_PATH_COUNT)
                .toList();
    }

    private static void appendTopPaths(
            StringBuilder report, List<Map.Entry<WinningPathBreakdown, Long>> topPaths, int winners) {
        report.append("\n### Most common winning paths\n");
        int rank = 1;
        for (Map.Entry<WinningPathBreakdown, Long> path : topPaths) {
            report.append(rank++)
                    .append(". `")
                    .append(path.getValue())
                    .append(" (")
                    .append(ActionCardStatsService.formatPercent(path.getValue() / (double) winners))
                    .append(")` ")
                    .append(WinningPathHelper.describe(path.getKey()))
                    .append('\n');
        }
    }

    private static List<ScoreType> scoreTypesInPaths(List<Map.Entry<WinningPathBreakdown, Long>> topPaths) {
        List<WinningPathBreakdown> paths =
                topPaths.stream().map(Map.Entry::getKey).toList();
        List<ScoreType> scoreTypes = new ArrayList<>(CORE_SCORE_TYPES.stream()
                .filter(type -> paths.stream().anyMatch(path -> type.countIn(path) > 0))
                .toList());
        Set<String> otherSources = new LinkedHashSet<>();
        paths.forEach(path -> otherSources.addAll(path.otherPoints().keySet()));
        otherSources.stream().sorted().map(ScoreType::otherSource).forEach(scoreTypes::add);
        return scoreTypes;
    }

    private static void appendScoreTypeBreakdowns(
            StringBuilder report, List<ScoreType> scoreTypes, List<WinningPathBreakdown> winningPaths) {
        report.append("\n### Share of winners by points scored\n")
                .append("_Out of all ")
                .append(winningPaths.size())
                .append(" winners, for each type of point in the paths above._\n");
        scoreTypes.forEach(scoreType -> appendScoreTypeBreakdown(report, scoreType, winningPaths));
    }

    private static void appendScoreTypeBreakdown(
            StringBuilder report, ScoreType scoreType, List<WinningPathBreakdown> winningPaths) {
        NavigableMap<Integer, Long> winnersByCount = winningPaths.stream()
                .map(scoreType::countIn)
                .filter(count -> count > 0)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        report.append("\n**").append(scoreType.heading()).append("**\n");
        if (winnersByCount.isEmpty()) {
            report.append("- No winner scored any\n");
            return;
        }
        winnersByCount.forEach(
                (count, winners) -> appendShareLine(report, scoreType.label(count), winners, winningPaths.size()));
        for (int count = 1; count <= winnersByCount.lastKey(); count++) {
            int minimumCount = count;
            long winnersWithAtLeast = winningPaths.stream()
                    .filter(path -> scoreType.countIn(path) >= minimumCount)
                    .count();
            appendShareLine(report, scoreType.atLeastLabel(count), winnersWithAtLeast, winningPaths.size());
        }
    }

    private static void appendShareLine(StringBuilder report, String label, long winners, int totalWinners) {
        report.append("- ")
                .append(label)
                .append(": ")
                .append(ActionCardStatsService.formatPercent(winners / (double) totalWinners))
                .append('\n');
    }

    private record ScoreType(
            String heading, ToIntFunction<WinningPathBreakdown> counter, String singular, String plural) {

        static ScoreType otherSource(String source) {
            return new ScoreType(source, path -> path.pointsFrom(source), source, source);
        }

        int countIn(WinningPathBreakdown path) {
            return counter.applyAsInt(path);
        }

        String label(int count) {
            return count + " " + (count == 1 ? singular : plural);
        }

        String atLeastLabel(int count) {
            return ">= " + count + " " + plural;
        }
    }
}
