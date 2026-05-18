package ti4.service.statistics.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class VictoryPointsScoredStatisticsService {

    static void listScoredVictoryPoints(SlashCommandInteractionEvent event) {
        Map<String, Integer> secrets = new HashMap<>();
        Map<String, Integer> publics = new HashMap<>();
        Map<String, Integer> relics = new HashMap<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> collectScoredSecrets(game, secrets),
                ExecutionLockType.READ);

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> collectScoredVictoryPoints(game, publics, relics),
                ExecutionLockType.READ);

        Map<String, Integer> topThousand = secrets.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(3000)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        int index = 1;
        StringBuilder sb = new StringBuilder("List of times a particular secret objective has been scored.\n");
        for (Map.Entry<String, Integer> entry : topThousand.entrySet()) {
            sb.append('`').append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(entry.getKey()).append(": ");
            sb.append(entry.getValue());
            sb.append('\n');
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Secret Objective Score Counts", sb.toString());

        topThousand = publics.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(3000)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        index = 1;
        sb = new StringBuilder("List of times a particular public objective has been revealed \n");
        for (Map.Entry<String, Integer> entry : topThousand.entrySet()) {
            sb.append('`').append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(entry.getKey()).append(": ");
            sb.append(entry.getValue());
            sb.append('\n');
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Public Objectives Revealed", sb.toString());

        topThousand = relics.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(3000)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        index = 1;
        sb = new StringBuilder("List of times a particular relic has been drawn \n");
        for (Map.Entry<String, Integer> entry : topThousand.entrySet()) {
            sb.append('`').append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(entry.getKey()).append(": ");
            sb.append(entry.getValue());
            sb.append('\n');
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Relics Drawn Count", sb.toString());
    }

    static void collectScoredSecrets(Game game, Map<String, Integer> secrets) {
        if (SecretObjectiveWinChanceStatisticsService.shouldIgnoreGameForSecretObjectiveStats(game)) {
            return;
        }

        Set<String> convertedSecretIds = new HashSet<>(game.getSoToPoList());
        for (Player player : game.getRealAndEliminatedPlayers()) {
            for (String so : player.getSecretsScored().keySet()) {
                var secretObjective =
                        SecretObjectiveWinChanceStatisticsService.getTrackableSecretObjective(so, convertedSecretIds);
                if (secretObjective != null) {
                    secrets.merge(secretObjective.getName(), 1, Integer::sum);
                }
            }
        }
    }

    private static void collectScoredVictoryPoints(
            Game game, Map<String, Integer> publics, Map<String, Integer> relics) {
        for (String po : game.getRevealedPublicObjectives().keySet()) {
            if (Mapper.getPublicObjective(po) != null) {
                String publicO = Mapper.getPublicObjective(po).getName();
                if (publics.containsKey(publicO)) {
                    publics.put(publicO, publics.get(publicO) + 1);
                } else {
                    publics.put(publicO, 1);
                }
            }
        }

        List<String> relicsNames = Mapper.getDecks().get(game.getRelicDeckID()).getNewShuffledDeck();
        for (String relic : relicsNames) {
            if (!game.getAllRelics().contains(relic)) {
                String relicName = Mapper.getRelic(relic).getName();
                if (relics.containsKey(relicName)) {
                    relics.put(relicName, relics.get(relicName) + 1);
                } else {
                    relics.put(relicName, 1);
                }
            }
        }
    }
}
