package ti4.spring.service.statistics.matchmaking;

public record MatchmakingGameQuality(MatchmakingSkillLevel skillRating, MatchmakingSkillLevel skillDifference) {

    static MatchmakingGameQuality unknown() {
        return new MatchmakingGameQuality(MatchmakingSkillLevel.UNKNOWN, MatchmakingSkillLevel.UNKNOWN);
    }
}
