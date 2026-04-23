package ti4.service.statistics.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.slashcommands.statistics.GameStatisticsFilterer;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GamesPage;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;

@UtilityClass
class SecretObjectiveWinChanceStatisticsService {

    static void showSecretObjectiveWinChance(SlashCommandInteractionEvent event) {
        int[] playersByScoredAPSecretCount = new int[5];
        int[] winsByScoredAPSecretCount = new int[5];
        int[] playersByScoredSecretCount = new int[5];
        int[] winsByScoredSecretCount = new int[5];
        int[][] playersByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        int[][] winsByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        Map<String, Integer> playersBySecretPhaseCombination = new HashMap<>();
        Map<String, Integer> winsBySecretPhaseCombination = new HashMap<>();
        Map<String, Integer> gamesWithSecretScored = new HashMap<>();
        Map<String, Integer> winsWithSecretScored = new HashMap<>();
        Map<String, Integer> gamesWithSecretInHand = new HashMap<>();
        Map<String, Integer> winsWithSecretInHand = new HashMap<>();
        Map<String, Integer> gamesWithSecretScoredOrInHand = new HashMap<>();
        Map<String, Integer> winsWithSecretScoredOrInHand = new HashMap<>();
        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilterForWonGame(event), game -> {
            if (shouldIgnoreGameForSecretObjectiveStats(game)) {
                return;
            }

            collectSecretObjectiveWinChanceStats(
                    game,
                    playersByScoredAPSecretCount,
                    winsByScoredAPSecretCount,
                    playersByScoredSecretCount,
                    winsByScoredSecretCount,
                    playersByExactScoredSecretCountAndMinimumAPCount,
                    winsByExactScoredSecretCountAndMinimumAPCount,
                    playersBySecretPhaseCombination,
                    winsBySecretPhaseCombination,
                    gamesWithSecretScored,
                    winsWithSecretScored,
                    gamesWithSecretInHand,
                    winsWithSecretInHand,
                    gamesWithSecretScoredOrInHand,
                    winsWithSecretScoredOrInHand);
        });

        StringBuilder sb = new StringBuilder();
        appendWinningPlayerActionPhaseSecretRateSection(sb, winsByScoredAPSecretCount);
        sb.append('\n');
        appendScoredSecretCountWinChanceSection(
                sb,
                playersByScoredSecretCount,
                winsByScoredSecretCount,
                playersByScoredAPSecretCount,
                winsByScoredAPSecretCount);
        sb.append('\n');
        sb.append(buildConditionedActionPhaseWinChanceSection(
                playersByExactScoredSecretCountAndMinimumAPCount, winsByExactScoredSecretCountAndMinimumAPCount));
        sb.append('\n');
        sb.append(buildSecretPhaseCombinationWinChanceSection(
                playersBySecretPhaseCombination, winsBySecretPhaseCombination));

        // Collect all secret names from all three maps
        Collection<String> allSecretNames = new HashSet<>();
        allSecretNames.addAll(gamesWithSecretScored.keySet());
        allSecretNames.addAll(gamesWithSecretInHand.keySet());
        allSecretNames.addAll(gamesWithSecretScoredOrInHand.keySet());

        // Find the maximum combinedGames across all secrets for discard normalization.
        // Secrets discarded more often appear in fewer end-of-game hands; normalizing
        // each secret's scored wins against the max adjusts for this selection bias.
        int maxCombinedGames = gamesWithSecretScoredOrInHand.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        List<SecretWinChanceEntry> secretEntries = allSecretNames.stream()
                .map(secretName -> buildSecretWinChanceEntry(
                        secretName,
                        gamesWithSecretScored,
                        winsWithSecretScored,
                        gamesWithSecretScoredOrInHand,
                        winsWithSecretScoredOrInHand,
                        maxCombinedGames))
                .sorted((a, b) -> {
                    int compareByWhenDrawn = Long.compare(b.whenDrawnEstimatedPercent(), a.whenDrawnEstimatedPercent());
                    if (compareByWhenDrawn != 0) {
                        return compareByWhenDrawn;
                    }

                    int compareByScoredPercent = Long.compare(b.scoredPercent(), a.scoredPercent());
                    if (compareByScoredPercent != 0) {
                        return compareByScoredPercent;
                    }

                    int compareByScoredGames = Integer.compare(b.scoredGames(), a.scoredGames());
                    if (compareByScoredGames != 0) {
                        return compareByScoredGames;
                    }

                    return a.name().compareTo(b.name());
                })
                .toList();

        StringBuilder secretObjectiveSb = new StringBuilder(sb);
        secretObjectiveSb.append("\n__**Win Chance with Secret**__\n");
        for (SecretWinChanceEntry secretEntry : secretEntries) {
            secretObjectiveSb
                    .append("**")
                    .append(secretEntry.name())
                    .append("**\n")
                    .append(" · Scored: `")
                    .append(StringUtils.leftPad(secretEntry.scoredPercent() + "%", 4))
                    .append("` (")
                    .append(secretEntry.scoredWins())
                    .append("/")
                    .append(secretEntry.scoredGames())
                    .append(")\n")
                    .append(" · When Drawn (Estimated): `")
                    .append(StringUtils.leftPad(secretEntry.whenDrawnEstimatedPercent() + "%", 4))
                    .append("`\n")
                    .append(" · Discard Rate (Estimated): `")
                    .append(StringUtils.leftPad(secretEntry.discardRateEstimatedPercent() + "%", 4))
                    .append("`\n");
        }
        MessageHelper.sendMessageToThread(
                event.getChannel(), "Secret Objective Win Chance", secretObjectiveSb.toString());
    }

    private static void appendWinningPlayerActionPhaseSecretRateSection(
            StringBuilder sb, int[] winsByScoredAPSecretCount) {
        int totalWinners = 0;
        for (int wins : winsByScoredAPSecretCount) {
            totalWinners += wins;
        }

        sb.append("__**Winning Player Action Phase Secret Rate**__\n")
                .append("_(What percent of game winners scored X action phase secrets?)_\n\n");

        for (int count = 0; count <= 4; count++) {
            int winners = winsByScoredAPSecretCount[count];
            long percent = totalWinners == 0 ? 0 : Math.round(100.0 * winners / totalWinners);
            sb.append('`')
                    .append(count)
                    .append(" AP secrets` ")
                    .append('`')
                    .append(StringUtils.leftPad(percent + "%", 4))
                    .append("` (")
                    .append(winners)
                    .append("/")
                    .append(totalWinners)
                    .append(")\n");
        }

        for (int count = 1; count <= 3; count++) {
            int winners = 0;
            for (int i = count; i <= 4; i++) {
                winners += winsByScoredAPSecretCount[i];
            }
            long percent = totalWinners == 0 ? 0 : Math.round(100.0 * winners / totalWinners);
            sb.append('`')
                    .append(count)
                    .append("+ AP secrets` ")
                    .append('`')
                    .append(StringUtils.leftPad(percent + "%", 4))
                    .append("` (")
                    .append(winners)
                    .append("/")
                    .append(totalWinners)
                    .append(")\n");
        }
    }

    private static void appendScoredSecretCountWinChanceSection(
            StringBuilder sb,
            int[] playersByScoredSecretCount,
            int[] winsByScoredSecretCount,
            int[] playersByScoredAPSecretCount,
            int[] winsByScoredAPSecretCount) {
        sb.append("__**Scored Secret Count Win Chance**__\n")
                .append("_(What is a player's win chance if they've scored X secrets?)_\n\n");

        for (int count = 0; count <= 4; count++) {
            appendSecretCountWinChanceLine(
                    sb, count + " secrets", playersByScoredSecretCount[count], winsByScoredSecretCount[count]);
        }

        for (int count = 1; count <= 3; count++) {
            int players = 0;
            int wins = 0;
            for (int i = count; i <= 4; i++) {
                players += playersByScoredSecretCount[i];
                wins += winsByScoredSecretCount[i];
            }
            appendSecretCountWinChanceLine(sb, count + "+ secrets", players, wins);
        }

        for (int count = 0; count <= 4; count++) {
            appendSecretCountWinChanceLine(
                    sb, count + " AP secrets", playersByScoredAPSecretCount[count], winsByScoredAPSecretCount[count]);
        }
    }

    private static void appendSecretCountWinChanceLine(StringBuilder sb, String label, int players, int wins) {
        long percent = players == 0 ? 0 : Math.round(100.0 * wins / players);
        sb.append('`')
                .append(label)
                .append("` ")
                .append('`')
                .append(StringUtils.leftPad(percent + "%", 4))
                .append("` (")
                .append(wins)
                .append("/")
                .append(players)
                .append(")\n");
    }

    static String buildConditionedActionPhaseWinChanceSection(
            int[][] playersByExactScoredSecretCountAndMinimumAPCount,
            int[][] winsByExactScoredSecretCountAndMinimumAPCount) {
        StringBuilder sb = new StringBuilder("__**Action Phase Secret Win Chance by Total Scored Secrets**__\n")
                .append(
                        "_(What is a player's win chance with X total scored secrets and exactly Y action phase secrets?)_\n\n");

        for (int totalSecretCount = 1; totalSecretCount <= 4; totalSecretCount++) {
            sb.append("**")
                    .append(totalSecretCount)
                    .append(" total scored secret")
                    .append(totalSecretCount == 1 ? "" : "s")
                    .append("**\n");
            for (int exactAPCount = 0; exactAPCount <= totalSecretCount; exactAPCount++) {
                appendSecretCountWinChanceLine(
                        sb,
                        exactAPCount + " AP",
                        playersByExactScoredSecretCountAndMinimumAPCount[totalSecretCount][exactAPCount],
                        winsByExactScoredSecretCountAndMinimumAPCount[totalSecretCount][exactAPCount]);
            }
            if (totalSecretCount < 4) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    static String buildSecretPhaseCombinationWinChanceSection(
            Map<String, Integer> playersBySecretPhaseCombination, Map<String, Integer> winsBySecretPhaseCombination) {
        StringBuilder sb = new StringBuilder("__**Scored + Unscored Secret Combination Win Chance**__\n")
                .append("_(What is a player's win chance for each end-of-game action/status/agenda secret mix?)_\n\n");

        playersBySecretPhaseCombination.entrySet().stream()
                .sorted((left, right) -> {
                    int leftActionCount = getActionSecretCount(left.getKey());
                    int rightActionCount = getActionSecretCount(right.getKey());
                    int leftStatusCount = getStatusSecretCount(left.getKey());
                    int rightStatusCount = getStatusSecretCount(right.getKey());
                    int compareByTotalSecretCount = Integer.compare(
                            leftActionCount + leftStatusCount + getAgendaSecretCount(left.getKey()),
                            rightActionCount + rightStatusCount + getAgendaSecretCount(right.getKey()));
                    if (compareByTotalSecretCount != 0) {
                        return compareByTotalSecretCount;
                    }
                    int compareByActionCount = Integer.compare(leftActionCount, rightActionCount);
                    if (compareByActionCount != 0) {
                        return compareByActionCount;
                    }
                    int compareByStatusCount = Integer.compare(leftStatusCount, rightStatusCount);
                    if (compareByStatusCount != 0) {
                        return compareByStatusCount;
                    }
                    return Integer.compare(getAgendaSecretCount(left.getKey()), getAgendaSecretCount(right.getKey()));
                })
                .forEach(entry -> appendSecretCountWinChanceLine(
                        sb,
                        formatSecretPhaseCombinationLabel(
                                getActionSecretCount(entry.getKey()),
                                getStatusSecretCount(entry.getKey()),
                                getAgendaSecretCount(entry.getKey())),
                        entry.getValue(),
                        winsBySecretPhaseCombination.getOrDefault(entry.getKey(), 0)));

        return sb.toString();
    }

    private static String getSecretPhaseCombinationKey(int actionCount, int statusCount, int agendaCount) {
        return actionCount + "|" + statusCount + "|" + agendaCount;
    }

    private static int getActionSecretCount(String combinationKey) {
        return Integer.parseInt(StringUtils.substringBefore(combinationKey, "|"));
    }

    private static int getStatusSecretCount(String combinationKey) {
        return Integer.parseInt(StringUtils.substringBefore(StringUtils.substringAfter(combinationKey, "|"), "|"));
    }

    private static int getAgendaSecretCount(String combinationKey) {
        return Integer.parseInt(StringUtils.substringAfterLast(combinationKey, "|"));
    }

    private static String formatSecretPhaseCombinationLabel(int actionCount, int statusCount, int agendaCount) {
        if (actionCount == 0 && statusCount == 0 && agendaCount == 0) {
            return "0 secrets";
        }
        List<String> segments = new java.util.ArrayList<>();
        if (actionCount > 0) {
            segments.add(actionCount + " action" + (actionCount == 1 ? "" : "s"));
        }
        if (statusCount > 0) {
            segments.add(statusCount + " " + formatStatusSecretLabel(statusCount));
        }
        if (agendaCount > 0) {
            segments.add(agendaCount + " " + formatAgendaSecretLabel(agendaCount));
        }
        return String.join(" and ", segments);
    }

    private static String formatStatusSecretLabel(int statusCount) {
        return statusCount == 1 ? "status" : "statuses";
    }

    private static String formatAgendaSecretLabel(int agendaCount) {
        return agendaCount == 1 ? "agenda" : "agendas";
    }

    private static void incrementExactActionPhaseSecretCount(
            int[][] countsByExactScoredSecretCountAndMinimumAPCount,
            int totalScoredSecretCount,
            int actionPhaseSecretCount) {
        if (totalScoredSecretCount > 4) {
            return;
        }
        if (actionPhaseSecretCount > totalScoredSecretCount) {
            return;
        }
        countsByExactScoredSecretCountAndMinimumAPCount[totalScoredSecretCount][actionPhaseSecretCount]++;
    }

    private static SecretPhaseCounts countSecretPhases(Iterable<String> secretIds, Set<String> convertedSecretIds) {
        int actionPhaseSecretCount = 0;
        int statusPhaseSecretCount = 0;
        int agendaPhaseSecretCount = 0;
        for (String secretId : secretIds) {
            SecretObjectiveModel secretObjective = getTrackableSecretObjective(secretId, convertedSecretIds);
            if (secretObjective == null) {
                continue;
            }
            String phase = secretObjective.getPhase();
            if ("action".equalsIgnoreCase(phase)) {
                actionPhaseSecretCount++;
            } else if ("status".equalsIgnoreCase(phase)) {
                statusPhaseSecretCount++;
            } else if ("agenda".equalsIgnoreCase(phase)) {
                agendaPhaseSecretCount++;
            }
        }
        return new SecretPhaseCounts(actionPhaseSecretCount, statusPhaseSecretCount, agendaPhaseSecretCount);
    }

    private static Set<String> getSecretNames(Iterable<String> secretIds, Set<String> convertedSecretIds) {
        Set<String> secretNames = new HashSet<>();
        for (String secretId : secretIds) {
            SecretObjectiveModel secretObjective = getTrackableSecretObjective(secretId, convertedSecretIds);
            if (secretObjective != null) {
                secretNames.add(secretObjective.getName());
            }
        }
        return secretNames;
    }

    private static int countRealSecretObjectives(Iterable<String> secretIds, Set<String> convertedSecretIds) {
        int count = 0;
        for (String secretId : secretIds) {
            if (getTrackableSecretObjective(secretId, convertedSecretIds) != null) {
                count++;
            }
        }
        return count;
    }

    private static SecretObjectiveModel getTrackableSecretObjective(String secretId, Set<String> convertedSecretIds) {
        if (convertedSecretIds.contains(secretId)) {
            return null;
        }
        return Mapper.getSecretObjective(secretId);
    }

    static boolean shouldIgnoreGameForSecretObjectiveStats(Game game) {
        Set<String> convertedSecretIds = new HashSet<>(game.getSoToPoList());
        for (Player player : game.getRealAndEliminatedPlayers()) {
            int totalRealSecrets = countRealSecretObjectives(
                            player.getSecretsScored().keySet(), convertedSecretIds)
                    + countRealSecretObjectives(player.getSecretsUnscored().keySet(), convertedSecretIds);
            if (totalRealSecrets > 4) {
                return true;
            }
        }
        return false;
    }

    private static SecretWinChanceEntry buildSecretWinChanceEntry(
            String secretName,
            Map<String, Integer> gamesWithSecretScored,
            Map<String, Integer> winsWithSecretScored,
            Map<String, Integer> gamesWithSecretScoredOrInHand,
            Map<String, Integer> winsWithSecretScoredOrInHand,
            int maxCombinedGames) {
        int scoredGames = gamesWithSecretScored.getOrDefault(secretName, 0);
        int scoredWins = winsWithSecretScored.getOrDefault(secretName, 0);
        long scoredPercent = scoredGames == 0 ? 0 : Math.round(100.0 * scoredWins / scoredGames);

        int combinedGames = gamesWithSecretScoredOrInHand.getOrDefault(secretName, 0);
        int combinedWins = winsWithSecretScoredOrInHand.getOrDefault(secretName, 0);
        long whenDrawnEstimatedPercent =
                (combinedWins == 0 || maxCombinedGames == 0) ? 0 : Math.round(100.0 * combinedWins / maxCombinedGames);
        long discardRateEstimatedPercent =
                (maxCombinedGames == 0) ? 0 : Math.round(100.0 * (maxCombinedGames - combinedGames) / maxCombinedGames);

        return new SecretWinChanceEntry(
                secretName,
                scoredGames,
                scoredWins,
                scoredPercent,
                combinedWins,
                whenDrawnEstimatedPercent,
                discardRateEstimatedPercent);
    }

    static void collectSecretObjectiveWinChanceStats(
            Game game,
            int[] playersByScoredAPSecretCount,
            int[] winsByScoredAPSecretCount,
            int[] playersByScoredSecretCount,
            int[] winsByScoredSecretCount,
            int[][] playersByExactScoredSecretCountAndMinimumAPCount,
            int[][] winsByExactScoredSecretCountAndMinimumAPCount,
            Map<String, Integer> playersBySecretPhaseCombination,
            Map<String, Integer> winsBySecretPhaseCombination,
            Map<String, Integer> gamesWithSecretScored,
            Map<String, Integer> winsWithSecretScored,
            Map<String, Integer> gamesWithSecretInHand,
            Map<String, Integer> winsWithSecretInHand,
            Map<String, Integer> gamesWithSecretScoredOrInHand,
            Map<String, Integer> winsWithSecretScoredOrInHand) {
        Optional<Player> winner = game.getWinner();
        if (winner.isEmpty()) return;

        Set<String> convertedSecretIds = new HashSet<>(game.getSoToPoList());

        for (Player player : game.getRealAndEliminatedPlayers()) {
            boolean isWinner = player == winner.get();

            SecretPhaseCounts scoredSecretPhaseCounts =
                    countSecretPhases(player.getSecretsScored().keySet(), convertedSecretIds);
            int actionPhaseSecretCount = scoredSecretPhaseCounts.actionCount();
            int statusPhaseSecretCount = scoredSecretPhaseCounts.statusCount();
            int agendaPhaseSecretCount = scoredSecretPhaseCounts.agendaCount();
            int totalScoredSecretCount =
                    countRealSecretObjectives(player.getSecretsScored().keySet(), convertedSecretIds);
            Set<String> scoredSecrets = getSecretNames(player.getSecretsScored().keySet(), convertedSecretIds);

            int actionBucket = Math.min(4, actionPhaseSecretCount);
            playersByScoredAPSecretCount[actionBucket]++;

            int totalScoredBucket = Math.min(4, totalScoredSecretCount);
            playersByScoredSecretCount[totalScoredBucket]++;
            incrementExactActionPhaseSecretCount(
                    playersByExactScoredSecretCountAndMinimumAPCount, totalScoredSecretCount, actionPhaseSecretCount);

            if (isWinner) {
                winsByScoredAPSecretCount[actionBucket]++;
                winsByScoredSecretCount[totalScoredBucket]++;
                incrementExactActionPhaseSecretCount(
                        winsByExactScoredSecretCountAndMinimumAPCount, totalScoredSecretCount, actionPhaseSecretCount);
            }

            Map<String, Integer> unscoredSecrets = player.getSecretsUnscored();
            Set<String> inHandSecrets = getSecretNames(unscoredSecrets.keySet(), convertedSecretIds);
            SecretPhaseCounts unscoredSecretPhaseCounts =
                    countSecretPhases(unscoredSecrets.keySet(), convertedSecretIds);
            actionPhaseSecretCount += unscoredSecretPhaseCounts.actionCount();
            statusPhaseSecretCount += unscoredSecretPhaseCounts.statusCount();
            agendaPhaseSecretCount += unscoredSecretPhaseCounts.agendaCount();

            String secretPhaseCombinationKey = getSecretPhaseCombinationKey(
                    actionPhaseSecretCount, statusPhaseSecretCount, agendaPhaseSecretCount);
            playersBySecretPhaseCombination.merge(secretPhaseCombinationKey, 1, Integer::sum);
            if (isWinner) {
                winsBySecretPhaseCombination.merge(secretPhaseCombinationKey, 1, Integer::sum);
            }

            for (String secretName : scoredSecrets) {
                gamesWithSecretScored.merge(secretName, 1, Integer::sum);
                gamesWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                if (isWinner) {
                    winsWithSecretScored.merge(secretName, 1, Integer::sum);
                    winsWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                }
            }

            for (String secretName : inHandSecrets) {
                gamesWithSecretInHand.merge(secretName, 1, Integer::sum);
                gamesWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                if (isWinner) {
                    winsWithSecretInHand.merge(secretName, 1, Integer::sum);
                    winsWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                }
            }
        }
    }

    private record SecretWinChanceEntry(
            String name,
            int scoredGames,
            int scoredWins,
            long scoredPercent,
            int combinedWins,
            long whenDrawnEstimatedPercent,
            long discardRateEstimatedPercent) {}

    private record SecretPhaseCounts(int actionCount, int statusCount, int agendaCount) {}
}
