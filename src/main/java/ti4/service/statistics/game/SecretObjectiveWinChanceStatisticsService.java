package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
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

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> collectSecretObjectiveWinChanceStats(
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
                        winsWithSecretScoredOrInHand));

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
        Set<String> allSecretNames = new HashSet<>();
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
                        "_(What is a player's win chance with X total scored secrets and at least Y action phase secrets?)_\n\n");

        for (int totalSecretCount = 1; totalSecretCount <= 4; totalSecretCount++) {
            sb.append("**")
                    .append(totalSecretCount)
                    .append(" total scored secret")
                    .append(totalSecretCount == 1 ? "" : "s")
                    .append("**\n");
            for (int minimumAPCount = 1; minimumAPCount <= totalSecretCount; minimumAPCount++) {
                String label = minimumAPCount == totalSecretCount ? minimumAPCount + " AP" : minimumAPCount + "+ AP";
                appendSecretCountWinChanceLine(
                        sb,
                        label,
                        playersByExactScoredSecretCountAndMinimumAPCount[totalSecretCount][minimumAPCount],
                        winsByExactScoredSecretCountAndMinimumAPCount[totalSecretCount][minimumAPCount]);
            }
            if (totalSecretCount < 4) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    static String buildSecretPhaseCombinationWinChanceSection(
            Map<String, Integer> playersBySecretPhaseCombination, Map<String, Integer> winsBySecretPhaseCombination) {
        StringBuilder sb = new StringBuilder("__**Scored + Unscored Secret Phase Combination Win Chance**__\n")
                .append("_(What is a player's win chance for each end-of-game action/status secret mix?)_\n\n");

        playersBySecretPhaseCombination.entrySet().stream()
                .sorted((left, right) -> {
                    int leftActionCount = getActionSecretCount(left.getKey());
                    int rightActionCount = getActionSecretCount(right.getKey());
                    int compareByTotalSecretCount = Integer.compare(
                            leftActionCount + getStatusSecretCount(left.getKey()),
                            rightActionCount + getStatusSecretCount(right.getKey()));
                    if (compareByTotalSecretCount != 0) {
                        return compareByTotalSecretCount;
                    }
                    return Integer.compare(leftActionCount, rightActionCount);
                })
                .forEach(entry -> appendSecretCountWinChanceLine(
                        sb,
                        formatSecretPhaseCombinationLabel(
                                getActionSecretCount(entry.getKey()), getStatusSecretCount(entry.getKey())),
                        entry.getValue(),
                        winsBySecretPhaseCombination.getOrDefault(entry.getKey(), 0)));

        return sb.toString();
    }

    private static String getSecretPhaseCombinationKey(int actionCount, int statusCount) {
        return actionCount + "|" + statusCount;
    }

    private static int getActionSecretCount(String combinationKey) {
        return Integer.parseInt(StringUtils.substringBefore(combinationKey, "|"));
    }

    private static int getStatusSecretCount(String combinationKey) {
        return Integer.parseInt(StringUtils.substringAfter(combinationKey, "|"));
    }

    static String formatSecretPhaseCombinationLabel(int actionCount, int statusCount) {
        if (actionCount == 0 && statusCount == 0) {
            return "0 secrets";
        }
        if (actionCount == 0) {
            return statusCount + " " + formatStatusSecretLabel(statusCount);
        }
        if (statusCount == 0) {
            return actionCount + " action" + (actionCount == 1 ? "" : "s");
        }
        return actionCount + " action" + (actionCount == 1 ? "" : "s")
                + " and "
                + statusCount
                + " "
                + formatStatusSecretLabel(statusCount);
    }

    private static String formatStatusSecretLabel(int statusCount) {
        return statusCount == 1 ? "status" : "statuses";
    }

    private static void incrementMinimumActionPhaseSecretCounts(
            int[][] countsByExactScoredSecretCountAndMinimumAPCount, int totalScoredSecretCount, int actionPhaseSecretCount) {
        if (totalScoredSecretCount > 4) {
            return;
        }
        for (int minimumAPCount = 1; minimumAPCount <= totalScoredSecretCount; minimumAPCount++) {
            if (actionPhaseSecretCount >= minimumAPCount) {
                countsByExactScoredSecretCountAndMinimumAPCount[totalScoredSecretCount][minimumAPCount]++;
            }
        }
    }

    private static SecretPhaseCounts countSecretPhases(Iterable<String> secretIds) {
        int actionPhaseSecretCount = 0;
        int statusPhaseSecretCount = 0;
        for (String secretId : secretIds) {
            SecretObjectiveModel secretObjective = Mapper.getSecretObjective(secretId);
            if (secretObjective == null) {
                continue;
            }
            if ("action".equalsIgnoreCase(secretObjective.getPhase())) {
                actionPhaseSecretCount++;
            } else {
                statusPhaseSecretCount++;
            }
        }
        return new SecretPhaseCounts(actionPhaseSecretCount, statusPhaseSecretCount);
    }

    private static Set<String> getSecretNames(Iterable<String> secretIds) {
        Set<String> secretNames = new HashSet<>();
        for (String secretId : secretIds) {
            SecretObjectiveModel secretObjective = Mapper.getSecretObjective(secretId);
            if (secretObjective != null) {
                secretNames.add(secretObjective.getName());
            }
        }
        return secretNames;
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

        for (Player player : game.getRealAndEliminatedPlayers()) {
            boolean isWinner = player == winner.get();

            SecretPhaseCounts scoredSecretPhaseCounts = countSecretPhases(player.getSecretsScored().keySet());
            int actionPhaseSecretCount = scoredSecretPhaseCounts.actionCount();
            int statusPhaseSecretCount = scoredSecretPhaseCounts.statusCount();
            int totalScoredSecretCount = player.getSecretsScored().size();
            Set<String> scoredSecrets = getSecretNames(player.getSecretsScored().keySet());

            int actionBucket = Math.min(4, actionPhaseSecretCount);
            playersByScoredAPSecretCount[actionBucket]++;

            int totalScoredBucket = Math.min(4, totalScoredSecretCount);
            playersByScoredSecretCount[totalScoredBucket]++;
            incrementMinimumActionPhaseSecretCounts(
                    playersByExactScoredSecretCountAndMinimumAPCount, totalScoredSecretCount, actionPhaseSecretCount);

            if (isWinner) {
                winsByScoredAPSecretCount[actionBucket]++;
                winsByScoredSecretCount[totalScoredBucket]++;
                incrementMinimumActionPhaseSecretCounts(
                        winsByExactScoredSecretCountAndMinimumAPCount, totalScoredSecretCount, actionPhaseSecretCount);
            }

            Map<String, Integer> unscoredSecrets = player.getSecretsUnscored();
            Set<String> inHandSecrets = getSecretNames(unscoredSecrets.keySet());
            SecretPhaseCounts unscoredSecretPhaseCounts = countSecretPhases(unscoredSecrets.keySet());
            actionPhaseSecretCount += unscoredSecretPhaseCounts.actionCount();
            statusPhaseSecretCount += unscoredSecretPhaseCounts.statusCount();

            String secretPhaseCombinationKey =
                    getSecretPhaseCombinationKey(actionPhaseSecretCount, statusPhaseSecretCount);
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

    private record SecretPhaseCounts(int actionCount, int statusCount) {}
}
