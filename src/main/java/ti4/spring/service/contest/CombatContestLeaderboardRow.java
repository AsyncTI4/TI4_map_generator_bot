package ti4.spring.service.contest;

public interface CombatContestLeaderboardRow {

    String getDiscordUserId();

    String getDiscordUserName();

    Integer getTotalPoints();

    Long getPredictionCount();

    Long getCorrectPredictions();
}
