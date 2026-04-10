package ti4.service.statistics.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;

@UtilityClass
class SecretObjectiveWinChanceStatisticsService {

    static void showSecretObjectiveWinChance(SlashCommandInteractionEvent event) {
        int[] actionPhaseGamesByCount = new int[5];
        int[] actionPhaseWinsByCount = new int[5];
        Map<String, Integer> gamesWithSecret = new HashMap<>();
        Map<String, Integer> winsWithSecret = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> collectSecretObjectiveWinChanceStats(
                        game, actionPhaseGamesByCount, actionPhaseWinsByCount, gamesWithSecret, winsWithSecret));

        StringBuilder sb = new StringBuilder("Action phase secret count vs win chance:\n");
        sb.append("_(Includes scored secrets and unscored secrets still in hand.)_\n\n");

        for (int count = 0; count <= 4; count++) {
            int games = actionPhaseGamesByCount[count];
            int wins = actionPhaseWinsByCount[count];
            long percent = games == 0 ? 0 : Math.round(100.0 * wins / games);
            sb.append('`')
                    .append(count)
                    .append(" AP secrets` ")
                    .append(StringUtils.leftPad(String.valueOf(percent), 3))
                    .append("% (")
                    .append(wins)
                    .append("/")
                    .append(games)
                    .append(")\n");
        }

        for (int count = 1; count <= 3; count++) {
            int games = 0;
            int wins = 0;
            for (int i = count; i <= 4; i++) {
                games += actionPhaseGamesByCount[i];
                wins += actionPhaseWinsByCount[i];
            }
            long percent = games == 0 ? 0 : Math.round(100.0 * wins / games);
            sb.append('`')
                    .append(count)
                    .append(" or more AP secrets` ")
                    .append(StringUtils.leftPad(String.valueOf(percent), 3))
                    .append("% (")
                    .append(wins)
                    .append("/")
                    .append(games)
                    .append(")\n");
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Action Phase Secrets Win Chance", sb.toString());

        StringBuilder secretObjectiveSb = new StringBuilder("Win chance with secret scored or in hand:\n");
        Map<String, Integer> orderedSecrets = gamesWithSecret.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        for (Map.Entry<String, Integer> entry : orderedSecrets.entrySet()) {
            String secretName = entry.getKey();
            int games = entry.getValue();
            int wins = winsWithSecret.getOrDefault(secretName, 0);
            long percent = games == 0 ? 0 : Math.round(100.0 * wins / games);
            secretObjectiveSb
                    .append(secretName)
                    .append(": ")
                    .append(percent)
                    .append("% (")
                    .append(wins)
                    .append("/")
                    .append(games)
                    .append(")\n");
        }
        MessageHelper.sendMessageToThread(
                event.getChannel(), "Win chance with secret scored or in hand", secretObjectiveSb.toString());
    }

    private static void collectSecretObjectiveWinChanceStats(
            Game game,
            int[] actionPhaseGamesByCount,
            int[] actionPhaseWinsByCount,
            Map<String, Integer> gamesWithSecret,
            Map<String, Integer> winsWithSecret) {
        Optional<Player> winner = game.getWinner();
        if (winner.isEmpty()) return;

        for (Player player : game.getRealAndEliminatedPlayers()) {
            int actionPhaseSecretCount = countActionPhaseSecrets(player);
            int bucket = Math.min(4, actionPhaseSecretCount);
            actionPhaseGamesByCount[bucket]++;
            if (player == winner.get()) {
                actionPhaseWinsByCount[bucket]++;
            }

            Set<String> allSecrets = player.getSecretsScored().keySet().stream()
                    .filter(secretId -> Mapper.getSecretObjective(secretId) != null)
                    .collect(Collectors.toSet());
            allSecrets.addAll(player.getSecretsUnscored().keySet().stream()
                    .filter(secretId -> Mapper.getSecretObjective(secretId) != null)
                    .collect(Collectors.toSet()));
            for (String secretId : allSecrets) {
                String secretName = Mapper.getSecretObjective(secretId).getName();
                gamesWithSecret.put(secretName, gamesWithSecret.getOrDefault(secretName, 0) + 1);
                if (player == winner.get()) {
                    winsWithSecret.put(secretName, winsWithSecret.getOrDefault(secretName, 0) + 1);
                }
            }
        }
    }

    private static int countActionPhaseSecrets(Player player) {
        int count = 0;
        for (String secretId : player.getSecretsScored().keySet()) {
            if (isActionPhaseSecret(secretId)) count++;
        }
        for (String secretId : player.getSecretsUnscored().keySet()) {
            if (isActionPhaseSecret(secretId)) count++;
        }
        return count;
    }

    private static boolean isActionPhaseSecret(String secretId) {
        SecretObjectiveModel so = Mapper.getSecretObjective(secretId);
        return so != null && "action".equalsIgnoreCase(so.getPhase());
    }
}
