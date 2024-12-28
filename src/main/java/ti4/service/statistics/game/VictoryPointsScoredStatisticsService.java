package ti4.service.statistics.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class VictoryPointsScoredStatisticsService {

    static void listScoredVictoryPoints(SlashCommandInteractionEvent event) {
        Map<String, Integer> secrets = new HashMap<>();
        Map<String, Integer> publics = new HashMap<>();
        Map<String, Integer> relics = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> listScoredVictoryPoints(game, secrets, publics, relics)
        );

        Map<String, Integer> topThousand = secrets.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        int index = 1;
        StringBuilder sb = new StringBuilder("List of times a particular secret objective has been scored.\n");
        for (String ket : topThousand.keySet()) {
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(ket).append(": ");
            sb.append(topThousand.get(ket));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Secret Objective Score Counts", sb.toString());

        topThousand = publics.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        index = 1;
        sb = new StringBuilder("List of times a particular public objective has been revealed \n");
        for (String ket : topThousand.keySet()) {
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(ket).append(": ");
            sb.append(topThousand.get(ket));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Public Objectives Revealed", sb.toString());

        topThousand = relics.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        index = 1;
        sb = new StringBuilder("List of times a particular relic has been drawn \n");
        for (String ket : topThousand.keySet()) {
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(ket).append(": ");
            sb.append(topThousand.get(ket));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Relics Drawn Count", sb.toString());
    }

    private static void listScoredVictoryPoints(Game game, Map<String, Integer> secrets, Map<String, Integer> publics, Map<String, Integer> relics) {
        for (Player player : game.getRealPlayers()) {
            for (String so : player.getSecretsScored().keySet()) {
                if (Mapper.getSecretObjective(so) != null) {
                    String secret = Mapper.getSecretObjective(so).getName();
                    if (secrets.containsKey(secret)) {
                        secrets.put(secret, secrets.get(secret) + 1);
                    } else {
                        secrets.put(secret, 1);
                    }
                }
            }
        }
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
