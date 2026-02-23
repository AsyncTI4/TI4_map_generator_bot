package ti4.spring.api.dashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.ManagedGame;

final class DashboardBadgeEngine {

    private static final int FLEET_LOGISTICS_MIN_TURNS = 60;
    private static final long FLEET_LOGISTICS_LEGENDARY_MAX_AVG_SECONDS = 1_800;
    private static final long FLEET_LOGISTICS_GOLD_MAX_AVG_SECONDS = 3_600;
    private static final long FLEET_LOGISTICS_SILVER_MAX_AVG_SECONDS = 7_200;

    private static final int FROM_THE_BRINK_MIN_CLOSE_WINS = 2;
    private static final int FROM_THE_BRINK_GOLD_CLOSE_WINS = 4;
    private static final int FROM_THE_BRINK_LEGENDARY_CLOSE_WINS = 6;

    private static final int GALACTIC_ENDURANCE_MIN_GAMES = 1;
    private static final int GALACTIC_ENDURANCE_GOLD_GAMES = 2;
    private static final int GALACTIC_ENDURANCE_LEGENDARY_GAMES = 3;
    private static final int LONG_HAUL_GAME_DAYS = 180;

    private static final int SPEAKERS_HAND_MIN_SIGNALS = 8;
    private static final int SPEAKERS_HAND_GOLD_SIGNALS = 15;
    private static final int SPEAKERS_HAND_LEGENDARY_SIGNALS = 25;

    private static final int GALACTIC_METAMORPH_MIN_DISTINCT_WIN_FACTIONS = 10;
    private static final int GALACTIC_METAMORPH_GOLD_DISTINCT_WIN_FACTIONS = 20;
    private static final int GALACTIC_METAMORPH_LEGENDARY_DISTINCT_WIN_FACTIONS = 30;

    private DashboardBadgeEngine() {}

    static PlayerDashboardResponse.PlayerInsights analyze(
            String userId,
            List<ManagedGame> managedGames,
            List<PlayerDashboardResponse.DashboardGame> games,
            PlayerDashboardResponse.TitleSummary titleSummary) {
        PlayerTurnAggregate turnAggregate = getTurnAggregate(userId, managedGames);
        List<PlayerDashboardResponse.FavoredFaction> favoredFactions = getFavoredFactions(games);

        int closeWins = getCloseWins(userId, games);
        int longHaulFinishes = getLongHaulFinishes(games);
        int diplomacySignals = getDiplomacySignals(titleSummary);

        List<PlayerDashboardResponse.BadgeAward> badges = new ArrayList<>();
        addFleetLogisticsBadge(badges, turnAggregate);
        addFromTheBrinkBadge(badges, closeWins);
        addGalacticEnduranceBadge(badges, longHaulFinishes);
        addSpeakersHandBadge(badges, diplomacySignals);
        addGalacticMetamorphBadge(badges, games);

        return new PlayerDashboardResponse.PlayerInsights(
                new PlayerDashboardResponse.PlayerActivity(
                        turnAggregate.totalTurns(),
                        turnAggregate.averageTurnTimeSeconds().isPresent()
                                ? turnAggregate.averageTurnTimeSeconds().getAsLong()
                                : null),
                badges,
                favoredFactions);
    }

    private static PlayerTurnAggregate getTurnAggregate(String userId, List<ManagedGame> managedGames) {
        int totalTurns = 0;
        long totalTurnTimeMs = 0;
        for (ManagedGame managedGame : managedGames) {
            Game game = managedGame.getGame();
            if (game == null) {
                continue;
            }
            for (Player player : game.getRealPlayers()) {
                if (!userId.equals(player.getStatsTrackedUserID())) {
                    continue;
                }
                totalTurns += Math.max(player.getNumberOfTurns(), 0);
                totalTurnTimeMs += Math.max(player.getTotalTurnTime(), 0);
            }
        }

        OptionalLong averageTurnTimeSeconds = OptionalLong.empty();
        if (totalTurns > 0 && totalTurnTimeMs > 0) {
            averageTurnTimeSeconds = OptionalLong.of((totalTurnTimeMs / totalTurns) / 1000);
        }
        return new PlayerTurnAggregate(totalTurns, averageTurnTimeSeconds);
    }

    private static List<PlayerDashboardResponse.FavoredFaction> getFavoredFactions(
            List<PlayerDashboardResponse.DashboardGame> games) {
        class FactionStats {
            int gamesPlayed;
            int wins;
        }

        Map<String, FactionStats> factionStats = games.stream()
                .map(PlayerDashboardResponse.DashboardGame::yourSeat)
                .filter(Objects::nonNull)
                .filter(seat -> seat.faction() != null && !seat.faction().isBlank())
                .collect(Collectors.toMap(
                        PlayerDashboardResponse.YourSeat::faction,
                        seat -> {
                            FactionStats stats = new FactionStats();
                            stats.gamesPlayed = 1;
                            stats.wins = seat.isWinner() ? 1 : 0;
                            return stats;
                        },
                        (left, right) -> {
                            left.gamesPlayed += right.gamesPlayed;
                            left.wins += right.wins;
                            return left;
                        }));

        return factionStats.entrySet().stream()
                .map(entry -> {
                    FactionStats stats = entry.getValue();
                    Double winPercent = stats.gamesPlayed == 0 ? null : (stats.wins * 100.0) / stats.gamesPlayed;
                    return new PlayerDashboardResponse.FavoredFaction(
                            entry.getKey(), stats.gamesPlayed, stats.wins, winPercent);
                })
                .sorted(Comparator.comparingInt(PlayerDashboardResponse.FavoredFaction::gamesPlayed)
                        .reversed()
                        .thenComparing(
                                fh -> fh.winPercent() == null ? -1.0 : fh.winPercent(), Comparator.reverseOrder())
                        .thenComparing(PlayerDashboardResponse.FavoredFaction::faction))
                .limit(5)
                .toList();
    }

    private static int getCloseWins(String userId, List<PlayerDashboardResponse.DashboardGame> games) {
        int closeWins = 0;
        for (PlayerDashboardResponse.DashboardGame game : games) {
            PlayerDashboardResponse.YourSeat yourSeat = game.yourSeat();
            if (yourSeat == null || !game.isFinished() || !yourSeat.isWinner()) {
                continue;
            }
            int highestOpponent = game.participants().stream()
                    .filter(participant -> !userId.equals(participant.userId()))
                    .mapToInt(PlayerDashboardResponse.GameParticipant::score)
                    .max()
                    .orElse(0);
            if (yourSeat.score() >= game.vpTarget() && highestOpponent >= game.vpTarget() - 1) {
                closeWins++;
            }
        }
        return closeWins;
    }

    private static int getLongHaulFinishes(List<PlayerDashboardResponse.DashboardGame> games) {
        int count = 0;
        for (PlayerDashboardResponse.DashboardGame game : games) {
            if (!game.isFinished() || game.endedAtEpochMs() == null) {
                continue;
            }
            long durationDays = Math.max(0, (game.endedAtEpochMs() - game.createdAtEpochMs()) / 86_400_000L);
            if (durationDays >= LONG_HAUL_GAME_DAYS) {
                count++;
            }
        }
        return count;
    }

    private static int getDiplomacySignals(PlayerDashboardResponse.TitleSummary titleSummary) {
        if (titleSummary == null) {
            return 0;
        }
        return titleSummary.items().stream()
                .map(PlayerDashboardResponse.TitleItem::title)
                .filter(Objects::nonNull)
                .map(title -> title.toLowerCase(Locale.ROOT))
                .filter(lower -> lower.contains("diplomat")
                        || lower.contains("speaker")
                        || lower.contains("minister")
                        || lower.contains("peace")
                        || lower.contains("trade")
                        || lower.contains("envoy"))
                .mapToInt(title -> 1)
                .sum();
    }

    private static void addFleetLogisticsBadge(
            List<PlayerDashboardResponse.BadgeAward> badges, PlayerTurnAggregate turnAggregate) {
        if (turnAggregate.totalTurns() < FLEET_LOGISTICS_MIN_TURNS
                || turnAggregate.averageTurnTimeSeconds().isEmpty()) {
            return;
        }
        long avgSeconds = turnAggregate.averageTurnTimeSeconds().getAsLong();
        String tier = null;
        if (avgSeconds <= FLEET_LOGISTICS_LEGENDARY_MAX_AVG_SECONDS) {
            tier = "LEGENDARY";
        } else if (avgSeconds <= FLEET_LOGISTICS_GOLD_MAX_AVG_SECONDS) {
            tier = "GOLD";
        } else if (avgSeconds <= FLEET_LOGISTICS_SILVER_MAX_AVG_SECONDS) {
            tier = "SILVER";
        }
        if (tier == null) {
            return;
        }
        badges.add(new PlayerDashboardResponse.BadgeAward(
                "fleet_logistics",
                "Fleet Logistics",
                tier,
                "Maintains a rapid operational tempo across many turns.",
                new PlayerDashboardResponse.BadgeMetric("Average turn time", avgSeconds, "seconds"),
                List.of(new PlayerDashboardResponse.BadgeRequirement(
                        "Tracked turns", turnAggregate.totalTurns(), FLEET_LOGISTICS_MIN_TURNS, "turns", true)),
                "Average turn speed across all tracked turns.",
                "Legendary <30m, Gold <1h, Silver <2h"));
    }

    private static void addFromTheBrinkBadge(List<PlayerDashboardResponse.BadgeAward> badges, int closeWins) {
        if (closeWins < FROM_THE_BRINK_MIN_CLOSE_WINS) {
            return;
        }
        String tier = closeWins >= FROM_THE_BRINK_LEGENDARY_CLOSE_WINS
                ? "LEGENDARY"
                : closeWins >= FROM_THE_BRINK_GOLD_CLOSE_WINS ? "GOLD" : "SILVER";
        badges.add(new PlayerDashboardResponse.BadgeAward(
                "from_the_brink",
                "From the Brink",
                tier,
                "Closes out razor-thin endgames when the table is at match point.",
                new PlayerDashboardResponse.BadgeMetric("Clutch wins", closeWins, "count"),
                List.of(new PlayerDashboardResponse.BadgeRequirement(
                        "Clutch wins", closeWins, FROM_THE_BRINK_MIN_CLOSE_WINS, "count", true)),
                "Wins where at least one opponent was also near the finish line.",
                "Legendary 6+, Gold 4+, Silver 2+"));
    }

    private static void addGalacticEnduranceBadge(
            List<PlayerDashboardResponse.BadgeAward> badges, int longHaulFinishes) {
        if (longHaulFinishes < GALACTIC_ENDURANCE_MIN_GAMES) {
            return;
        }
        String tier = longHaulFinishes >= GALACTIC_ENDURANCE_LEGENDARY_GAMES
                ? "LEGENDARY"
                : longHaulFinishes >= GALACTIC_ENDURANCE_GOLD_GAMES ? "GOLD" : "SILVER";
        badges.add(new PlayerDashboardResponse.BadgeAward(
                "galactic_endurance",
                "Galactic Endurance",
                tier,
                "Finishes marathon campaigns that run at least 180 days.",
                new PlayerDashboardResponse.BadgeMetric("Marathon finishes", longHaulFinishes, "count"),
                List.of(new PlayerDashboardResponse.BadgeRequirement(
                        "180+ day finished games", longHaulFinishes, GALACTIC_ENDURANCE_MIN_GAMES, "games", true)),
                "Counts completed games lasting 180 days or longer.",
                "Legendary 3+, Gold 2+, Silver 1+"));
    }

    private static void addSpeakersHandBadge(List<PlayerDashboardResponse.BadgeAward> badges, int diplomacySignals) {
        if (diplomacySignals < SPEAKERS_HAND_MIN_SIGNALS) {
            return;
        }
        String tier = diplomacySignals >= SPEAKERS_HAND_LEGENDARY_SIGNALS
                ? "LEGENDARY"
                : diplomacySignals >= SPEAKERS_HAND_GOLD_SIGNALS ? "GOLD" : "SILVER";
        badges.add(new PlayerDashboardResponse.BadgeAward(
                "speakers_hand",
                "Speaker's Hand",
                tier,
                "Earns repeated diplomacy-oriented titles and table influence signals.",
                new PlayerDashboardResponse.BadgeMetric("Diplomacy signals", diplomacySignals, "count"),
                List.of(new PlayerDashboardResponse.BadgeRequirement(
                        "Diplomacy signals", diplomacySignals, SPEAKERS_HAND_MIN_SIGNALS, "count", true)),
                "Recognizes sustained council-and-trade identity from title history.",
                "Legendary 25+, Gold 15+, Silver 8+"));
    }

    private static void addGalacticMetamorphBadge(
            List<PlayerDashboardResponse.BadgeAward> badges, List<PlayerDashboardResponse.DashboardGame> games) {
        long distinctWinFactions = games.stream()
                .filter(PlayerDashboardResponse.DashboardGame::isFinished)
                .map(PlayerDashboardResponse.DashboardGame::yourSeat)
                .filter(Objects::nonNull)
                .filter(PlayerDashboardResponse.YourSeat::isWinner)
                .map(PlayerDashboardResponse.YourSeat::faction)
                .filter(Objects::nonNull)
                .filter(faction -> !faction.isBlank())
                .distinct()
                .count();

        if (distinctWinFactions < GALACTIC_METAMORPH_MIN_DISTINCT_WIN_FACTIONS) {
            return;
        }

        String tier = distinctWinFactions >= GALACTIC_METAMORPH_LEGENDARY_DISTINCT_WIN_FACTIONS
                ? "LEGENDARY"
                : distinctWinFactions >= GALACTIC_METAMORPH_GOLD_DISTINCT_WIN_FACTIONS ? "GOLD" : "SILVER";

        badges.add(new PlayerDashboardResponse.BadgeAward(
                "galactic_metamorph",
                "Galactic Metamorph",
                tier,
                "Wins with multiple factions, adapting identity to the table.",
                new PlayerDashboardResponse.BadgeMetric("Distinct winning factions", distinctWinFactions, "count"),
                List.of(new PlayerDashboardResponse.BadgeRequirement(
                        "Distinct factions won with",
                        distinctWinFactions,
                        GALACTIC_METAMORPH_MIN_DISTINCT_WIN_FACTIONS,
                        "factions",
                        true)),
                "Measures how many different factions you've won games with.",
                "Legendary 30+, Gold 20+, Silver 10+"));
    }

    private record PlayerTurnAggregate(int totalTurns, OptionalLong averageTurnTimeSeconds) {}

}
