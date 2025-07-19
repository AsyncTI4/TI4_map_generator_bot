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
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@UtilityClass
class FactionPerformanceStatisticsService {

    public static void showFactionPerformance(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        Map<String, Integer> factionPlayerTotals = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> calculate(game, factionWinCount, factionGameCount, factionPlayerTotals)
        );

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Performance (vs expected win rate):\n");
        Mapper.getFactionsValues().stream()
            .map(faction -> {
                int wins = factionWinCount.getOrDefault(faction.getAlias(), 0);
                int games = factionGameCount.getOrDefault(faction.getAlias(), 0);
                int totalPlayers = factionPlayerTotals.getOrDefault(faction.getAlias(), 0);
                double performance = games == 0 ? 0 : ((wins / (double) games) * (totalPlayers / (double) games) - 1) * 100;
                return Map.entry(faction, performance);
            })
            .filter(entry -> factionGameCount.containsKey(entry.getKey().getAlias()))
            .sorted(Map.Entry.<FactionModel, Double>comparingByValue().reversed())
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(String.format("%.2f", entry.getValue()), 6))
                .append("%` (")
                .append(factionGameCount.getOrDefault(entry.getKey().getAlias(), 0))
                .append(" games) ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Faction Performance", sb.toString());
    }

    private static void calculate(Game game, Map<String, Integer> winCount, Map<String, Integer> gameCount,
                                   Map<String, Integer> playerTotals) {
        int playerCount = game.getRealAndEliminatedPlayers().size();
        game.getWinner().ifPresent(winner -> winCount.merge(winner.getFaction(), 1, Integer::sum));
        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            String faction = player.getFaction();
            gameCount.merge(faction, 1, Integer::sum);
            playerTotals.merge(faction, playerCount, Integer::sum);
        }
    }
}
