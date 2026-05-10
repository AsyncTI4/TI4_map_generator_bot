package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class EndingRoundPhaseStatisticsService {
    private static final String UNKNOWN_PHASE_CODE = "UP";

    static void showEndingRoundPhaseStatistics(SlashCommandInteractionEvent event) {
        Map<String, Integer> endingRoundAndPhaseCount = new HashMap<>();
        Map<String, FactionWinningRoundStats> statsByFaction = new HashMap<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> collectEndingRoundStats(game, endingRoundAndPhaseCount, statsByFaction),
                ExecutionLockType.READ);

        StringBuilder sb = new StringBuilder("__**Game Endings by Round and Phase:**__\n");
        if (endingRoundAndPhaseCount.isEmpty()) {
            sb.append("No ended games found for the selected filters.\n");
        } else {
            sb.append(buildEndingRoundPhaseReport(endingRoundAndPhaseCount));
            sb.append("\n__**Faction Average Winning End Round:**__\n");
            sb.append(buildFactionWinningRoundReport(statsByFaction));
        }

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Game Ending Rounds", sb.toString());
    }

    static void collectEndingRoundStats(
            Game game,
            Map<String, Integer> endingRoundAndPhaseCount,
            Map<String, FactionWinningRoundStats> statsByFaction) {
        if (!game.isHasEnded()) {
            return;
        }

        String phaseCode = normalizePhaseCode(game.getPhaseOfGame());
        endingRoundAndPhaseCount.merge(formatFullRoundPhaseLabel(game.getRound(), phaseCode), 1, Integer::sum);
        for (Player winner : game.getWinners()) {
            statsByFaction
                    .computeIfAbsent(winner.getFaction(), _ -> new FactionWinningRoundStats())
                    .addWin(game.getRound(), phaseCode);
        }
    }

    static String buildEndingRoundPhaseReport(Map<String, Integer> endingRoundAndPhaseCount) {
        int totalEndedGames = endingRoundAndPhaseCount.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (totalEndedGames == 0) {
            return "No ended games found for the selected filters.\n";
        }

        AtomicInteger index = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        endingRoundAndPhaseCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append(index.incrementAndGet())
                        .append(". `")
                        .append(entry.getValue())
                        .append(" (")
                        .append(Math.round(100 * entry.getValue() / (double) totalEndedGames))
                        .append("%)` ")
                        .append(entry.getKey())
                        .append('\n'));
        return sb.toString();
    }

    static String buildFactionWinningRoundReport(Map<String, FactionWinningRoundStats> statsByFaction) {
        if (statsByFaction.isEmpty()) {
            return "No ended games found for the selected filters.\n";
        }

        StringBuilder sb = new StringBuilder();
        statsByFaction.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    FactionModel factionModel = Mapper.getFaction(entry.getKey());
                    String factionName =
                            factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : entry.getKey();
                    sb.append(FactionStatisticsHelper.getFactionEmoji(entry.getKey()))
                            .append(' ')
                            .append(factionName)
                            .append(": ")
                            .append(String.format(
                                    Locale.ROOT, "%.1f", entry.getValue().getAverageRound()))
                            .append(" avg (")
                            .append(entry.getValue().formatRoundPhaseCounts())
                            .append(")\n");
                });
        return sb.toString();
    }

    static String formatFullRoundPhaseLabel(int round, String phaseCode) {
        return "Round " + round + " - "
                + switch (phaseCode) {
                    case "AP" -> "action";
                    case "SP" -> "status";
                    case "AgP" -> "agenda";
                    default -> "unknown";
                };
    }

    static String normalizePhaseCode(String phase) {
        if (phase == null || phase.isBlank()) {
            return UNKNOWN_PHASE_CODE;
        }
        String normalizedPhase = phase.toLowerCase(Locale.ROOT);
        if (normalizedPhase.contains("status")) {
            return "SP";
        }
        if (normalizedPhase.contains("agenda")) {
            return "AgP";
        }
        if (normalizedPhase.contains("action")) {
            return "AP";
        }
        return UNKNOWN_PHASE_CODE;
    }

    private static int getPhaseSortOrder(String phaseCode) {
        return switch (phaseCode) {
            case "AP" -> 0;
            case "SP" -> 1;
            case "AgP" -> 2;
            default -> 3;
        };
    }

    static final class FactionWinningRoundStats {
        private int totalWins;
        private int totalRoundSum;
        private final Map<Integer, Map<String, Integer>> winsByRoundAndPhase = new HashMap<>();

        void addWin(int round, String phaseCode) {
            totalWins++;
            totalRoundSum += round;
            winsByRoundAndPhase.computeIfAbsent(round, _ -> new HashMap<>()).merge(phaseCode, 1, Integer::sum);
        }

        double getAverageRound() {
            return totalWins == 0 ? 0 : totalRoundSum / (double) totalWins;
        }

        String formatRoundPhaseCounts() {
            return winsByRoundAndPhase.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .flatMap(roundEntry -> roundEntry.getValue().entrySet().stream()
                            .sorted((left, right) -> Integer.compare(
                                    getPhaseSortOrder(left.getKey()), getPhaseSortOrder(right.getKey())))
                            .map(phaseEntry ->
                                    "R" + roundEntry.getKey() + phaseEntry.getKey() + ": " + phaseEntry.getValue()))
                    .collect(Collectors.joining(", "));
        }
    }
}
