package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class FactionPerformanceStatisticsService {

    static void showFactionPerformance(SlashCommandInteractionEvent event) {
        Map<String, Double> actualWins = new HashMap<>();
        Map<String, Double> expectedWins = new HashMap<>();
        Map<String, Integer> gameCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> calculate(game, actualWins, expectedWins, gameCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Performance (vs expected win rate):\n");
        Mapper.getFactionsValues().stream()
                .map(faction -> {
                    double factionWins = actualWins.getOrDefault(faction.getAlias(), 0.0);
                    double factionExpectedWins = expectedWins.getOrDefault(faction.getAlias(), 0.0);
                    double performance = factionExpectedWins == 0 ? 0 : ((factionWins / factionExpectedWins) - 1) * 100;
                    return Map.entry(faction, performance);
                })
                .filter(entry -> gameCount.containsKey(entry.getKey().getAlias()))
                .sorted(Map.Entry.<FactionModel, Double>comparingByValue().reversed())
                .forEach(entry -> sb.append("`")
                        .append(StringUtils.leftPad(String.format("%.2f", entry.getValue()), 6))
                        .append("%` (")
                        .append(gameCount.getOrDefault(entry.getKey().getAlias(), 0))
                        .append(" games) ")
                        .append(entry.getKey().getFactionEmoji())
                        .append(" ")
                        .append(entry.getKey().getFactionNameWithSourceEmoji())
                        .append("\n"));

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Faction Performance", sb.toString());
    }

    private static void calculate(
            Game game,
            Map<String, Double> actualWins,
            Map<String, Double> expectedWins,
            Map<String, Integer> gameCount) {
        if (game.getWinner().isEmpty()) {
            return;
        }
        int playerCount = game.getRealAndEliminatedPlayers().size();
        double expectedWinPerFaction = 1.0 / playerCount;

        game.getWinners().forEach(winner -> {
            String winningFaction = winner.getFaction();
            FactionStatisticsHelper.incrementFactionsDoubleValue(actualWins, winningFaction);
        });
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String faction = player.getFaction();
            FactionStatisticsHelper.incrementFactionsIntValue(gameCount, faction);
            FactionStatisticsHelper.incrementFactionsDoubleValue(expectedWins, faction, expectedWinPerFaction);
        }
    }
}
