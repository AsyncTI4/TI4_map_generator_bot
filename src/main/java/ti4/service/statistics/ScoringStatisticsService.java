package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.WinningPathBreakdown;
import ti4.service.statistics.game.WinningPathHelper;

@UtilityClass
public class ScoringStatisticsService {

    private static final int TOP_PATH_COUNT = 5;
    private static final String STANDARD_GAMES =
            "6-player, 10-victory-point, non-homebrew, non-Galactic-Event, non-Scenario";
    private static final String THUNDERS_EDGE_GAMES = "Thunder's Edge games";
    private static final String PROPHECY_OF_KINGS_GAMES = "Prophecy of Kings games without Thunder's Edge";

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
        boolean pokOnly = event.getOption(Constants.POK_ONLY, false, OptionMapping::getAsBoolean);
        boolean pokCompare = event.getOption(Constants.POK_COMPARE, false, OptionMapping::getAsBoolean);
        if (pokOnly && pokCompare) {
            MessageHelper.sendMessageToEventChannel(event, "Choose either `pok_only` or `pok_compare`, not both.");
            return;
        }
        ScoringView view = pokOnly ? ScoringView.POK_ONLY : pokCompare ? ScoringView.POK_COMPARE : ScoringView.TE;
        StatisticsPipeline.queue(event, () -> showScoring(event, view));
    }

    private static void showScoring(SlashCommandInteractionEvent event, ScoringView view) {
        List<WinningPathBreakdown> thundersEdgeWinners = new ArrayList<>();
        List<WinningPathBreakdown> prophecyOfKingsWinners = new ArrayList<>();
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter(),
                game -> {
                    if (game.isThundersEdge()) {
                        addWinner(game, thundersEdgeWinners);
                    } else if (game.isProphecyOfKings()) {
                        addWinner(game, prophecyOfKingsWinners);
                    }
                },
                ExecutionLockType.READ);

        String report =
                switch (view) {
                    case TE -> buildReport(thundersEdgeWinners, Optional.empty(), THUNDERS_EDGE_GAMES);
                    case POK_ONLY -> buildReport(prophecyOfKingsWinners, Optional.empty(), PROPHECY_OF_KINGS_GAMES);
                    case POK_COMPARE ->
                        buildReport(
                                thundersEdgeWinners,
                                Optional.of(new Sample(prophecyOfKingsWinners)),
                                THUNDERS_EDGE_GAMES);
                };
        MessageHelper.sendMessageToThread(event.getChannel(), "Scoring statistics", report);
    }

    private static void addWinner(Game game, List<WinningPathBreakdown> winners) {
        game.getWinner().ifPresent(winner -> winners.add(WinningPathHelper.breakDownWinningPath(game, winner)));
    }

    static String buildReport(
            List<WinningPathBreakdown> winningPaths, Optional<Sample> baseline, String gamesAnalyzed) {
        StringBuilder report = new StringBuilder("## __**Scoring statistics**__\n");
        report.append("_")
                .append(STANDARD_GAMES)
                .append(' ')
                .append(gamesAnalyzed)
                .append(" with winners._\n");
        baseline.ifPresent(sample -> report.append("_Parentheses show the percent change from the ")
                .append(sample.winners().size())
                .append(" winners of ")
                .append(PROPHECY_OF_KINGS_GAMES)
                .append("._\n"));
        if (winningPaths.isEmpty()) {
            return report.append("No games matched.\n").toString();
        }
        report.append("Winners analyzed: ").append(winningPaths.size()).append('\n');

        Sample sample = new Sample(winningPaths);
        List<Map.Entry<WinningPathBreakdown, Long>> topPaths = topPaths(winningPaths);
        appendTopPaths(report, topPaths, sample, baseline);
        appendScoreTypeBreakdowns(report, scoreTypesInPaths(topPaths), sample, baseline);
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
            StringBuilder report,
            List<Map.Entry<WinningPathBreakdown, Long>> topPaths,
            Sample sample,
            Optional<Sample> baseline) {
        report.append("\n### Most common winning paths\n");
        int rank = 1;
        for (Map.Entry<WinningPathBreakdown, Long> path : topPaths) {
            Predicate<WinningPathBreakdown> isThisPath = path.getKey()::equals;
            report.append(rank++)
                    .append(". `")
                    .append(path.getValue())
                    .append(" (")
                    .append(ActionCardStatsService.formatPercent(sample.share(isThisPath)))
                    .append(")`")
                    .append(percentChange(sample, baseline, isThisPath))
                    .append(' ')
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
            StringBuilder report, List<ScoreType> scoreTypes, Sample sample, Optional<Sample> baseline) {
        report.append("\n### Share of winners by points scored\n")
                .append("_Out of all ")
                .append(sample.winners().size())
                .append(" winners, for each type of point in the paths above._\n");
        scoreTypes.forEach(scoreType -> appendScoreTypeBreakdown(report, scoreType, sample, baseline));
    }

    private static void appendScoreTypeBreakdown(
            StringBuilder report, ScoreType scoreType, Sample sample, Optional<Sample> baseline) {
        report.append("\n**").append(scoreType.heading()).append("**\n");
        List<Integer> scoredCounts = sample.winners().stream()
                .map(scoreType::countIn)
                .filter(count -> count > 0)
                .distinct()
                .sorted()
                .toList();
        if (scoredCounts.isEmpty()) {
            report.append("- No winner scored any\n");
            return;
        }
        scoredCounts.forEach(count -> appendShareLine(
                report, scoreType.label(count), path -> scoreType.countIn(path) == count, sample, baseline));
        for (int count = 1; count < scoredCounts.getLast(); count++) {
            int minimumCount = count;
            appendShareLine(
                    report,
                    scoreType.atLeastLabel(count),
                    path -> scoreType.countIn(path) >= minimumCount,
                    sample,
                    baseline);
        }
    }

    private static void appendShareLine(
            StringBuilder report,
            String label,
            Predicate<WinningPathBreakdown> matcher,
            Sample sample,
            Optional<Sample> baseline) {
        report.append("- ")
                .append(label)
                .append(": ")
                .append(ActionCardStatsService.formatPercent(sample.share(matcher)))
                .append(percentChange(sample, baseline, matcher))
                .append('\n');
    }

    private static String percentChange(
            Sample sample, Optional<Sample> baseline, Predicate<WinningPathBreakdown> matcher) {
        if (baseline.isEmpty()) {
            return "";
        }
        double baselineShare = baseline.get().share(matcher);
        if (baseline.get().winners().isEmpty()) {
            return " (n/a)";
        }
        double share = sample.share(matcher);
        if (baselineShare == 0) {
            return share == 0 ? " (0%)" : " (new)";
        }
        return String.format(Locale.ROOT, " (%+.1f%%)", (share - baselineShare) / baselineShare * 100);
    }

    record Sample(List<WinningPathBreakdown> winners) {

        double share(Predicate<WinningPathBreakdown> matcher) {
            return winners.isEmpty() ? 0 : winners.stream().filter(matcher).count() / (double) winners.size();
        }
    }

    private enum ScoringView {
        TE,
        POK_ONLY,
        POK_COMPARE
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
