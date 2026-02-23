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
            PlayerInsights insights,
            PlayerAggregates aggregates) {}

    public record PlayerInsights(
            PlayerActivity activity,
            List<BadgeAward> badges,
            List<FavoredFaction> favoredFactions) {}

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

    public record FavoredFaction(String faction, int gamesPlayed, int wins, Double winPercent) {}

    public record TitleSummary(int totalCount, List<TitleItem> items) {}

    public record TitleItem(String title, int count, List<String> gameIds) {}

    public record DiceLuckSummary(double actualHits, double expectedHits, Double ratio) {}

    public record DashboardSummary(
            int gamesPlayed, int activeGames, int finishedGames, int abandonedGames, int wins, Double winPercent) {}

    public record PlayerAggregates(
            boolean ready,
            String completedGamesHash,
            int completedGameCount,
            int eligibleGameCount,
            int aggregatesVersion,
            Long computedAtEpochMs,
            List<String> completedGameIds,
            TechStats techStats,
            FactionWinStats factionWinStats,
            StrategyCardStats strategyCardStats,
            CombatProfile combatProfile,
            EconomyProfile economyProfile,
            FactionTechSynergy factionTechSynergy,
            SpeakerImpact speakerImpact,
            AggressionProfile aggressionProfile) {}

    public record TechStats(java.util.Map<String, TechStat> byTech) {}

    public record FactionWinStats(java.util.Map<String, Integer> byFaction) {}

    public record StrategyCardStats(java.util.Map<Integer, StrategyCardStat> bySc, StrategyCardStatsMeta meta) {}

    public record StrategyCardStat(int totalPicks, int gamesPicked, int winsInGamesPicked, double winRateWhenPicked) {}

    public record StrategyCardStatsMeta(int completedGamesConsidered, int gamesWithRoundStats) {}

    public record Coverage(int completedGamesConsidered, int gamesWithRoundStats) {}

    public record CombatProfile(CombatTotals totals, CombatAverages averagesPerCompletedGame, Coverage coverage) {}

    public record CombatTotals(
            int combatsInitiated, int tacticalsWithCombat, int planetsTaken, int planetsStolen, int diceRolled) {}

    public record CombatAverages(
            double combatsInitiated,
            double tacticalsWithCombat,
            double planetsTaken,
            double planetsStolen,
            double diceRolled) {}

    public record EconomyProfile(double totalExpensesSum, double avgTotalExpenses, int completedGamesConsidered) {}

    public record FactionTechSynergy(java.util.Map<String, FactionSynergyStat> byFaction) {}

    public record FactionSynergyStat(
            int games, int wins, int nonWins, java.util.Map<String, FactionTechSynergyStat> byTech) {}

    public record FactionTechSynergyStat(
            int gamesWithTech, int winsWithTech, int nonWinsWithTech, double winRateWhenTech) {}

    public record SpeakerImpact(SpeakerBucket speaker, SpeakerBucket nonSpeaker, double deltaWinRate) {}

    public record SpeakerBucket(int games, int wins, double winRate) {}

    public record AggressionProfile(
            AggressionWeights weights,
            java.util.Map<String, Double> byGame,
            AggressionSummary summary,
            Coverage coverage) {}

    public record AggressionWeights(double combatsInitiated, double planetsStolen, double tacticalsWithCombat) {}

    public record AggressionSummary(
            double avgScore, double medianScore, double maxScore, double minScore, String mostAggressiveGameId) {}

    public record TechStat(int gamesWithTech, double percentInEligibleGames) {}

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
