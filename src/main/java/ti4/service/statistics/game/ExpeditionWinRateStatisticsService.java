package ti4.service.statistics.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
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

    private static final Set<String> FIRMAMENT_AND_OBSIDIAN_FACTION_NAMES = Set.of("obsidian", "firmament");
    private static final String THUNDERS_EDGE = "thundersedge";

    static void showExpeditionWinRates(SlashCommandInteractionEvent event) {
        Map<String, Map<String, WinRateCount>> factionExpeditionStats = new LinkedHashMap<>();
        Map<String, WinRateCount> expeditionFollowStats = new LinkedHashMap<>();
        Map<Integer, WinRateCount> expeditionCountStats = new LinkedHashMap<>();
        WinRateCount lastExpeditionStats = new WinRateCount();
        WinRateCount thundersEdgeControlStats = new WinRateCount();
        int[] gamesThatDidNotFinishExpeditionsVersusDid = {0, 0};

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event).and(Game::isThundersEdge),
                game -> consumeGame(
                        game,
                        factionExpeditionStats,
                        expeditionFollowStats,
                        expeditionCountStats,
                        lastExpeditionStats,
                        thundersEdgeControlStats,
                        gamesThatDidNotFinishExpeditionsVersusDid));

        StringBuilder sb = new StringBuilder("__**Thunder's Edge Win Rate Correlations**__\n");

        sb.append("\n**By expedition followed**\n");
        expeditionFollowStats.forEach((expedition, counts) ->
                sb.append("- ").append(expedition).append(": ").append(counts).append("\n"));

        sb.append("\n**By number of expeditions completed**\n");
        expeditionCountStats.entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry -> sb.append("- ")
                .append(entry.getKey())
                .append(" expeditions: ")
                .append(entry.getValue())
                .append("\n"));

        sb.append("\n**Who completed the last expedition**\n");
        sb.append("- ").append(lastExpeditionStats).append("\n");

        sb.append("\n**Who controls Thunder's Edge**\n");
        sb.append("- ").append(thundersEdgeControlStats).append("\n");

        sb.append("\n**Expedition win rate by faction**\n");
        combineObsidianFirmament(factionExpeditionStats);
        factionExpeditionStats.forEach((faction, expeditionMap) -> {
            sb.append("- **").append(faction).append("**:\n");
            expeditionMap.forEach((expedition, stats) -> sb.append("  - ")
                    .append(expedition)
                    .append(": ")
                    .append(stats)
                    .append("\n"));
        });

        sb.append("\n**Expedition completion rate**\n");
        int uncompletedExpeditions = gamesThatDidNotFinishExpeditionsVersusDid[0];
        int totalGames = gamesThatDidNotFinishExpeditionsVersusDid[1];
        int completedExpeditions = totalGames - uncompletedExpeditions;
        long percent = totalGames > 0 ? Math.round(completedExpeditions * 100.0 / totalGames) : 0;
        sb.append("- ")
                .append(completedExpeditions)
                .append("/")
                .append(totalGames)
                .append(" (")
                .append(percent)
                .append("%)\n");

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Thunder's Edge expedition win rates", sb.toString());
    }

    private static void combineObsidianFirmament(Map<String, Map<String, WinRateCount>> factionExpeditionStats) {
        Map<String, WinRateCount> obsidianStats = factionExpeditionStats.remove("obsidian");
        Map<String, WinRateCount> firmamentStats = factionExpeditionStats.remove("firmament");

        Map<String, WinRateCount> combined = new LinkedHashMap<>();

        Consumer<Map<String, WinRateCount>> mergeIntoCombined = (sourceMap) -> {
            if (sourceMap == null) return;
            sourceMap.forEach((expedition, stats) -> {
                WinRateCount combinedStats = combined.computeIfAbsent(expedition, k -> new WinRateCount());
                combinedStats.wins += stats.wins;
                combinedStats.total += stats.total;
            });
        };

        mergeIntoCombined.accept(obsidianStats);
        mergeIntoCombined.accept(firmamentStats);

        factionExpeditionStats.put("obsidian & firmament", combined);
    }

    private static void consumeGame(
            Game game,
            Map<String, Map<String, WinRateCount>> factionExpeditionStats,
            Map<String, WinRateCount> expeditionFollowStats,
            Map<Integer, WinRateCount> expeditionCountStats,
            WinRateCount lastExpeditionStats,
            WinRateCount thundersEdgeControlStats,
            int[] gamesThatDidNotFinishExpeditionsVersusDid) {

        Player winner = game.getWinner().orElse(null);
        if (winner == null) {
            return;
        }

        // Get the win rate based on which expedition was followed
        Expeditions expeditions = game.getExpeditions();
        for (Entry<String, String> expedition :
                expeditions.getExpeditionFactions().entrySet()) {
            String faction = expedition.getValue();
            if (faction == null) {
                continue;
            }
            WinRateCount count = expeditionFollowStats.computeIfAbsent(
                    toExpeditionLabel(expedition.getKey()), key -> new WinRateCount());
            count.total++;
            if (checkFactionsEqual(faction, winner.getFaction())) {
                count.wins++;
            }
        }

        String lastExpeditionFaction = expeditions.getLastExpeditionFaction();
        if (lastExpeditionFaction == null) {
            gamesThatDidNotFinishExpeditionsVersusDid[0] += 1;
        }
        gamesThatDidNotFinishExpeditionsVersusDid[1] += 1;

        // Get win rate of number of expeditions followed, controlling TE at the end of the game,
        // and faction expeditions
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String faction = player.getFaction();
            boolean isWinner = checkFactionsEqual(faction, winner.getFaction());
            if (player.hasPlanet(THUNDERS_EDGE)) {
                thundersEdgeControlStats.total++;
                if (isWinner) {
                    thundersEdgeControlStats.wins++;
                }
            }

            List<String> completedExpeditions = expeditions.getExpeditionFactions().entrySet().stream()
                    .filter(entry -> checkFactionsEqual(faction, entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();

            WinRateCount count =
                    expeditionCountStats.computeIfAbsent(completedExpeditions.size(), key -> new WinRateCount());
            count.total++;
            if (isWinner) {
                count.wins++;
            }

            completedExpeditions.forEach(expedition -> {
                Map<String, WinRateCount> expeditionWinRates =
                        factionExpeditionStats.computeIfAbsent(faction, k -> new LinkedHashMap<>());
                WinRateCount count2 =
                        expeditionWinRates.computeIfAbsent(toExpeditionLabel(expedition), k -> new WinRateCount());
                count2.total++;
                if (isWinner) {
                    count2.wins++;
                }
            });

            if (checkFactionsEqual(faction, lastExpeditionFaction)) {
                lastExpeditionStats.total++;
                if (isWinner) {
                    lastExpeditionStats.wins++;
                }
            }
        }
    }

    private static boolean checkFactionsEqual(String faction1, String faction2) {
        if (faction1 == null || faction2 == null) return false;
        if (faction1.equals(faction2)) return true;

        Set<String> factions = Set.of(faction1, faction2);
        return FIRMAMENT_AND_OBSIDIAN_FACTION_NAMES.equals(factions);
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
