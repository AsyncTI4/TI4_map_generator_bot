package ti4.spring.api.dashboard;

import java.util.List;

public record PlayerDashboardResponse(PlayerProfile profile, DashboardSummary summary, List<DashboardGame> games) {

    public record PlayerProfile(
            String userId,
            String userName,
            String tiglCurrentRank,
            String tiglLatestRankAtGameStart,
            TitleSummary titles,
            DiceLuckSummary diceLuck,
            PlayerInsights insights) {}

    public record PlayerInsights(
            PlayerActivity activity,
            List<BadgeAward> badges,
            ImperialDoctrine imperialDoctrine,
            List<FavoredHouse> favoredHouses) {}

    public record PlayerActivity(int totalTurns, Long averageTurnTimeSeconds) {}

    public record BadgeAward(
            String key,
            String name,
            String tier,
            String description,
            BadgeMetric primaryMetric,
            List<BadgeRequirement> requirements,
            String summary,
            String tierRuleText) {}

    public record BadgeMetric(String label, double value, String unit) {}

    public record BadgeRequirement(String label, double current, double target, String unit, boolean met) {}

    public record ImperialDoctrine(String archetype, List<String> traits) {}

    public record FavoredHouse(String faction, int gamesPlayed, int wins, Double winPercent) {}

    public record TitleSummary(int totalCount, List<TitleItem> items) {}

    public record TitleItem(String title, int count, List<String> gameIds) {}

    public record DiceLuckSummary(double actualHits, double expectedHits, Double ratio) {}

    public record DashboardSummary(
            int gamesPlayed, int activeGames, int finishedGames, int abandonedGames, int wins, Double winPercent) {}

    public record DashboardGame(
            String gameId,
            String title,
            String status,
            boolean isActive,
            boolean isFinished,
            boolean isAbandoned,
            long createdAtEpochMs,
            long lastUpdatedEpochMs,
            Long endedAtEpochMs,
            int vpTarget,
            int round,
            boolean isTiglGame,
            String tiglMinimumRankAtStart,
            GamePacks packs,
            List<String> gameModes,
            GalacticEvents galacticEvents,
            YourSeat yourSeat,
            List<String> participatingFactionIds,
            List<String> participatingPlayerNames,
            List<GameParticipant> participants,
            String actionsJumpUrl,
            String tableTalkJumpUrl) {}

    public record GamePacks(
            boolean baseGame,
            boolean prophecyOfKings,
            boolean discordantStars,
            boolean thundersEdge,
            boolean thundersEdgeDemo,
            boolean twilightsFall,
            boolean absol,
            boolean miltyMod,
            boolean franken,
            boolean votc) {}

    public record GalacticEvents(String eventDeckId, List<GalacticEvent> inEffect) {}

    public record GalacticEvent(String eventId, String name, Integer instanceId) {}

    public record YourSeat(
            String userId,
            String userName,
            String faction,
            String color,
            int score,
            boolean eliminated,
            boolean passed,
            boolean isWinner,
            boolean isActivePlayer,
            String tiglRankAtGameStart,
            DiceLuckSummary diceLuck) {}

    public record GameParticipant(
            String userId,
            String userName,
            String faction,
            String color,
            int score,
            boolean eliminated,
            boolean isWinner) {}
}
