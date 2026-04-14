package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
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
        int[] playersByScoredAgendaSecretCount = new int[5];
        int[] winsByScoredAgendaSecretCount = new int[5];
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
                        playersByScoredAgendaSecretCount,
                        winsByScoredAgendaSecretCount,
                        gamesWithSecretScored,
                        winsWithSecretScored,
                        gamesWithSecretInHand,
                        winsWithSecretInHand,
                        gamesWithSecretScoredOrInHand,
                        winsWithSecretScoredOrInHand));

        StringBuilder sb = new StringBuilder();
        appendScoredSecretCountWinChanceSection(
                sb, "Action", playersByScoredAPSecretCount, winsByScoredAPSecretCount, true);
        sb.append('\n');
        appendScoredSecretCountWinChanceSection(
                sb, "Agenda", playersByScoredAgendaSecretCount, winsByScoredAgendaSecretCount, false);

        // Sort by highest SCORED win percent
        Map<String, Long> scoredWinPercent = new HashMap<>();
        for (Map.Entry<String, Integer> entry : gamesWithSecretScored.entrySet()) {
            String name = entry.getKey();
            int games = entry.getValue();
            int wins = winsWithSecretScored.getOrDefault(name, 0);
            scoredWinPercent.put(name, games == 0 ? 0L : Math.round(100.0 * wins / games));
        }

        // Collect all secret names from all three maps
        Set<String> allSecretNames = new HashSet<>();
        allSecretNames.addAll(gamesWithSecretScored.keySet());
        allSecretNames.addAll(gamesWithSecretInHand.keySet());
        allSecretNames.addAll(gamesWithSecretScoredOrInHand.keySet());

        // Order by highest scored win percent descending, with deterministic tie-breakers
        List<String> orderedNames = allSecretNames.stream()
                .sorted((a, b) -> {
                    int compareByPercent =
                            Long.compare(scoredWinPercent.getOrDefault(b, 0L), scoredWinPercent.getOrDefault(a, 0L));
                    if (compareByPercent != 0) {
                        return compareByPercent;
                    }

                    int compareByScoredGames = Integer.compare(
                            gamesWithSecretScored.getOrDefault(b, 0), gamesWithSecretScored.getOrDefault(a, 0));
                    if (compareByScoredGames != 0) {
                        return compareByScoredGames;
                    }

                    return a.compareTo(b);
                })
                .toList();

        // Find the maximum combinedGames across all secrets for discard normalization.
        // Secrets discarded more often appear in fewer end-of-game hands; normalizing
        // each secret's scored wins against the max adjusts for this selection bias.
        int maxCombinedGames = gamesWithSecretScoredOrInHand.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        StringBuilder secretObjectiveSb = new StringBuilder(sb);
        secretObjectiveSb.append("\n__**Win Chance with Secret**__\n");
        for (String secretName : orderedNames) {
            int scoredGames = gamesWithSecretScored.getOrDefault(secretName, 0);
            int scoredWins = winsWithSecretScored.getOrDefault(secretName, 0);
            long scoredPct = scoredGames == 0 ? 0 : Math.round(100.0 * scoredWins / scoredGames);

            int inHandGames = gamesWithSecretInHand.getOrDefault(secretName, 0);
            int inHandWins = winsWithSecretInHand.getOrDefault(secretName, 0);
            long inHandPct = inHandGames == 0 ? 0 : Math.round(100.0 * inHandWins / inHandGames);

            int combinedGames = gamesWithSecretScoredOrInHand.getOrDefault(secretName, 0);
            int combinedWins = winsWithSecretScoredOrInHand.getOrDefault(secretName, 0);
            long combinedPct = combinedGames == 0 ? 0 : Math.round(100.0 * combinedWins / combinedGames);

            // Estimated win % when drawn: use max combinedGames as the denominator
            // so secrets that are discarded more often get a lower win % reflecting
            // the discarded (unseen) population. Uses both scored and in-hand wins.
            long whenDrawnPct = (combinedWins == 0 || maxCombinedGames == 0)
                    ? 0
                    : Math.round(100.0 * combinedWins / maxCombinedGames);

            // Estimated discard rate: how often this secret was neither scored nor
            // held at end-of-game compared to the most-seen secret.
            long estimatedDiscardPct = (maxCombinedGames == 0)
                    ? 0
                    : Math.round(100.0 * (maxCombinedGames - combinedGames) / maxCombinedGames);

            secretObjectiveSb
                    .append("**")
                    .append(secretName)
                    .append("**\n")
                    .append(" Scored: `")
                    .append(StringUtils.leftPad(scoredPct + "%", 4))
                    .append("` (")
                    .append(scoredWins)
                    .append("/")
                    .append(scoredGames)
                    .append(") · In Hand: `")
                    .append(StringUtils.leftPad(inHandPct + "%", 4))
                    .append("` (")
                    .append(inHandWins)
                    .append("/")
                    .append(inHandGames)
                    .append(") · Combined: `")
                    .append(StringUtils.leftPad(combinedPct + "%", 4))
                    .append("` (")
                    .append(combinedWins)
                    .append("/")
                    .append(combinedGames)
                    .append(") · When Drawn (Estimated): `")
                    .append(StringUtils.leftPad(whenDrawnPct + "%", 4))
                    .append("` (")
                    .append(combinedWins)
                    .append("/")
                    .append(maxCombinedGames)
                    .append(") · Estimated Discard Rate: `")
                    .append(StringUtils.leftPad(estimatedDiscardPct + "%", 4))
                    .append("`\n");
        }
        MessageHelper.sendMessageToThread(
                event.getChannel(), "Secret Objective Win Chance", secretObjectiveSb.toString());
    }

    private static void appendScoredSecretCountWinChanceSection(
            StringBuilder sb,
            String phaseName,
            int[] playersByScoredSecretCount,
            int[] winsByScoredSecretCount,
            boolean includeCumulativeCounts) {
        sb.append("__**Scored ")
                .append(phaseName)
                .append(" Phase Secret Count Win Chance**__\n")
                .append("_(Includes only scored ")
                .append(phaseName.toLowerCase())
                .append(" phase secrets.)_\n\n");

        for (int count = 0; count <= 4; count++) {
            int players = playersByScoredSecretCount[count];
            int wins = winsByScoredSecretCount[count];
            long percent = players == 0 ? 0 : Math.round(100.0 * wins / players);
            sb.append('`')
                    .append(count)
                    .append(' ')
                    .append(phaseName)
                    .append(" secrets` ")
                    .append('`')
                    .append(StringUtils.leftPad(percent + "%", 4))
                    .append("` (")
                    .append(wins)
                    .append("/")
                    .append(players)
                    .append(")\n");
        }

        if (includeCumulativeCounts) {
            for (int count = 1; count <= 3; count++) {
                int players = 0;
                int wins = 0;
                for (int i = count; i <= 4; i++) {
                    players += playersByScoredSecretCount[i];
                    wins += winsByScoredSecretCount[i];
                }
                long percent = players == 0 ? 0 : Math.round(100.0 * wins / players);
                sb.append('`')
                        .append(count)
                        .append("+ ")
                        .append(phaseName)
                        .append(" secrets` ")
                        .append('`')
                        .append(StringUtils.leftPad(percent + "%", 4))
                        .append("` (")
                        .append(wins)
                        .append("/")
                        .append(players)
                        .append(")\n");
            }
        }
    }

    private static void collectSecretObjectiveWinChanceStats(
            Game game,
            int[] playersByScoredAPSecretCount,
            int[] winsByScoredAPSecretCount,
            int[] playersByScoredAgendaSecretCount,
            int[] winsByScoredAgendaSecretCount,
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

            int actionPhaseSecretCount = countScoredSecretsByPhase(player, "action");
            int actionBucket = Math.min(4, actionPhaseSecretCount);
            playersByScoredAPSecretCount[actionBucket]++;

            int agendaPhaseSecretCount = countScoredSecretsByPhase(player, "agenda");
            int agendaBucket = Math.min(4, agendaPhaseSecretCount);
            playersByScoredAgendaSecretCount[agendaBucket]++;

            if (isWinner) {
                winsByScoredAPSecretCount[actionBucket]++;
                winsByScoredAgendaSecretCount[agendaBucket]++;
            }

            Set<String> scoredSecrets = player.getSecretsScored().keySet().stream()
                    .filter(secretId -> Mapper.getSecretObjective(secretId) != null)
                    .collect(Collectors.toSet());
            Set<String> inHandSecrets = player.getSecretsUnscored().keySet().stream()
                    .filter(secretId -> Mapper.getSecretObjective(secretId) != null)
                    .collect(Collectors.toSet());

            for (String secretId : scoredSecrets) {
                String secretName = Mapper.getSecretObjective(secretId).getName();
                gamesWithSecretScored.merge(secretName, 1, Integer::sum);
                gamesWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                if (isWinner) {
                    winsWithSecretScored.merge(secretName, 1, Integer::sum);
                    winsWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                }
            }

            for (String secretId : inHandSecrets) {
                String secretName = Mapper.getSecretObjective(secretId).getName();
                gamesWithSecretInHand.merge(secretName, 1, Integer::sum);
                gamesWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                if (isWinner) {
                    winsWithSecretInHand.merge(secretName, 1, Integer::sum);
                    winsWithSecretScoredOrInHand.merge(secretName, 1, Integer::sum);
                }
            }
        }
    }

    private static int countScoredSecretsByPhase(Player player, String phase) {
        int count = 0;
        for (String secretId : player.getSecretsScored().keySet()) {
            if (isSecretInPhase(secretId, phase)) count++;
        }
        return count;
    }

    private static boolean isSecretInPhase(String secretId, String phase) {
        SecretObjectiveModel so = Mapper.getSecretObjective(secretId);
        return so != null && phase.equalsIgnoreCase(so.getPhase());
    }
}
