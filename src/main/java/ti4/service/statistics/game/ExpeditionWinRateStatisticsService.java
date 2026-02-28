package ti4.service.statistics.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Expeditions;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class ExpeditionWinRateStatisticsService {

    private static final String THUNDERS_EDGE = "thundersedge";

    static void showExpeditionWinRates(SlashCommandInteractionEvent event) {
        Map<String, WinRateCount> expeditionFollowStats = new LinkedHashMap<>();
        Map<Integer, WinRateCount> expeditionCountStats = new LinkedHashMap<>();
        WinRateCount lastExpeditionStats = new WinRateCount();
        WinRateCount thundersEdgeControlStats = new WinRateCount();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event).and(Game::isThundersEdge),
                game -> consumeGame(game, expeditionFollowStats, expeditionCountStats, lastExpeditionStats, thundersEdgeControlStats));

        StringBuilder sb = new StringBuilder("__**Thunder's Edge Win Rate Correlations**__\n");

        sb.append("\n**By particular expedition followed**\n");
        expeditionFollowStats.forEach((expedition, counts) -> sb.append("- ")
                .append(expedition)
                .append(": ")
                .append(counts)
                .append("\n"));

        sb.append("\n**By number of expeditions completed**\n");
        expeditionCountStats.entrySet().stream()
                .sorted(Entry.comparingByKey())
                .forEach(entry -> sb.append("- ")
                        .append(entry.getKey())
                        .append(" expeditions: ")
                        .append(entry.getValue())
                        .append("\n"));

        sb.append("\n**Who completed the last expedition (actionCards)**\n");
        sb.append("- ").append(lastExpeditionStats).append("\n");

        sb.append("\n**Who controls Thunder's Edge**\n");
        sb.append("- ").append(thundersEdgeControlStats).append("\n");

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Thunder's Edge expedition win rates", sb.toString());
    }

    private static void consumeGame(
            Game game,
            Map<String, WinRateCount> expeditionFollowStats,
            Map<Integer, WinRateCount> expeditionCountStats,
            WinRateCount lastExpeditionStats,
            WinRateCount thundersEdgeControlStats) {

        Player winner = game.getWinner().orElse(null);
        if (winner == null) {
            return;
        }

        Expeditions expeditions = game.getExpeditions();
        for (Entry<String, String> expedition : expeditions.getExpeditionFactions().entrySet()) {
            String faction = expedition.getValue();
            if (faction == null) {
                continue;
            }
            WinRateCount count = expeditionFollowStats.computeIfAbsent(
                    toExpeditionLabel(expedition.getKey()), key -> new WinRateCount());
            count.total++;
            if (faction.equals(winner.getFaction())) {
                count.wins++;
            }
        }

        for (Player player : game.getRealPlayers()) {
            String faction = player.getFaction();
            if (faction == null) {
                continue;
            }
            int completedExpeditions = (int) expeditions.getExpeditionFactions().values().stream()
                    .filter(faction::equals)
                    .count();
            WinRateCount count = expeditionCountStats.computeIfAbsent(completedExpeditions, key -> new WinRateCount());
            count.total++;
            if (faction.equals(winner.getFaction())) {
                count.wins++;
            }

            if (player.hasPlanet(THUNDERS_EDGE)) {
                thundersEdgeControlStats.total++;
                if (faction.equals(winner.getFaction())) {
                    thundersEdgeControlStats.wins++;
                }
            }
        }

        String actionCardsExpeditionWinner = expeditions.getActionCards();
        if (actionCardsExpeditionWinner != null) {
            lastExpeditionStats.total++;
            if (actionCardsExpeditionWinner.equals(winner.getFaction())) {
                lastExpeditionStats.wins++;
            }
        }
    }

    private static String toExpeditionLabel(String expeditionKey) {
        return switch (expeditionKey) {
            case "techSkip" -> "Tech skip";
            case "tradeGoods" -> "Trade goods";
            case "fiveRes" -> "5 resources";
            case "fiveInf" -> "5 influence";
            case "secret" -> "Secret objective";
            case "actionCards" -> "Action cards";
            default -> expeditionKey;
        };
    }

    private static class WinRateCount {
        int wins;
        int total;

        @Override
        public String toString() {
            if (total == 0) {
                return "No data";
            }
            return wins + "/" + total + " (" + Math.round(wins * 100.0 / total) + "%)";
        }
    }
}
