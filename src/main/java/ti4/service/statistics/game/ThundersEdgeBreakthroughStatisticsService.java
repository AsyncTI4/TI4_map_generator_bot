package ti4.service.statistics.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticsPipeline;

@UtilityClass
public class ThundersEdgeBreakthroughStatisticsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> sendBreakthroughStatistics(event));
    }

    private static void sendBreakthroughStatistics(SlashCommandInteractionEvent event) {
        AtomicInteger thunderEdgeGames = new AtomicInteger();
        AtomicInteger gamesWithBreakthroughs = new AtomicInteger();
        AtomicInteger playersWithBreakthroughs = new AtomicInteger();
        Map<String, FactionBreakthroughStats> factionBreakthroughs = new HashMap<>();

        GamesPage.consumeAllGames(game -> {
            if (!game.isThundersEdge()) {
                return;
            }
            thunderEdgeGames.incrementAndGet();
            boolean breakthroughUnlockedInGame = false;
            List<Player> winners = game.getWinners();
            boolean gameCompleted = !winners.isEmpty();
            for (Player player : game.getRealPlayers()) {
                FactionBreakthroughStats factionStats = factionBreakthroughs
                        .computeIfAbsent(player.getFaction(), faction -> new FactionBreakthroughStats());
                factionStats.incrementGames();

                if (player.isBreakthroughUnlocked()) {
                    playersWithBreakthroughs.incrementAndGet();
                    breakthroughUnlockedInGame = true;
                    factionStats.incrementGamesWithUnlock();
                    if (gameCompleted) {
                        factionStats.incrementCompletedGamesWithUnlock();
                        if (winners.contains(player)) {
                            factionStats.incrementWinsWithUnlock();
                        }
                    }
                }
            }
            if (breakthroughUnlockedInGame) {
                gamesWithBreakthroughs.incrementAndGet();
            }
        });

        if (thunderEdgeGames.get() == 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No Thunder's Edge games found.");
            return;
        }

        double percent = (gamesWithBreakthroughs.get() * 100.0) / thunderEdgeGames.get();
        String message = String.format(
                "Thunder's Edge Breakthrough unlocks: %d players (%.1f%% of games) across %d games.",
                playersWithBreakthroughs.get(),
                percent,
                thunderEdgeGames.get());
        StringBuilder detailedMessage = new StringBuilder(message).append("\n\nFaction breakdown:\n");

        List<Map.Entry<String, FactionBreakthroughStats>> sortedFactions = new ArrayList<>(factionBreakthroughs.entrySet());
        sortedFactions.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, FactionBreakthroughStats> entry : sortedFactions) {
            String faction = entry.getKey();
            FactionBreakthroughStats stats = entry.getValue();
            if (stats.games == 0) {
                continue;
            }

            double unlockPercent = (stats.gamesWithUnlock * 100.0) / stats.games;
            double winPercent = stats.completedGamesWithUnlock == 0
                    ? 0
                    : (stats.winsWithUnlock * 100.0) / stats.completedGamesWithUnlock;

            detailedMessage.append(String.format(
                    "%s: %d unlocks (%.1f%% of their games), %.1f%% wins in completed games with an unlock.%n",
                    faction,
                    stats.gamesWithUnlock,
                    unlockPercent,
                    winPercent));
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), detailedMessage.toString());
    }

    private static class FactionBreakthroughStats {
        private int games;
        private int gamesWithUnlock;
        private int completedGamesWithUnlock;
        private int winsWithUnlock;

        void incrementGames() {
            games++;
        }

        void incrementGamesWithUnlock() {
            gamesWithUnlock++;
        }

        void incrementCompletedGamesWithUnlock() {
            completedGamesWithUnlock++;
        }

        void incrementWinsWithUnlock() {
            winsWithUnlock++;
        }
    }
}
