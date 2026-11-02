package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.helpers.StringHelper;

@UtilityClass
class GameRatingDescriber {

    record GameRatings(
            String gameName,
            Map<String, String> usernameByUserId,
            Map<String, MatchmakingRating> ratingByUserId,
            long averageDisplayRating,
            long playerBaseAverageDisplayRating,
            SkillTier skillTier,
            double matchQuality) {}

    static String describe(GameRatings gameRatings) {
        long unratedCount = gameRatings.usernameByUserId().keySet().stream()
                .filter(userId -> !gameRatings.ratingByUserId().containsKey(userId))
                .count();
        StringBuilder description = new StringBuilder()
                .append("__**Matchmaking ratings for ")
                .append(gameRatings.gameName())
                .append("**__\n")
                .append(playerTable(gameRatings))
                .append("Average MMR: `")
                .append(gameRatings.averageDisplayRating())
                .append('`');
        if (unratedCount > 0) {
            description
                    .append(" (")
                    .append(StringHelper.pluralize((int) unratedCount, "unrated player"))
                    .append(" counted at the player-base average of `")
                    .append(gameRatings.playerBaseAverageDisplayRating())
                    .append("`)");
        }
        description
                .append("\nSkill tier: ")
                .append(describeSkillTier(gameRatings.skillTier()))
                .append("\nMatch quality: `")
                .append("%.1f%%".formatted(gameRatings.matchQuality() * 100))
                .append('`');
        return description.toString();
    }

    private static String playerTable(GameRatings gameRatings) {
        int nameWidth = Math.max(
                "Player".length(),
                gameRatings.usernameByUserId().values().stream()
                        .mapToInt(String::length)
                        .max()
                        .orElse(0));
        String rowFormat = "%-" + nameWidth + "s  %7s  %10s";
        List<String> rows = new ArrayList<>();
        rows.add(rowFormat.formatted("Player", "MMR", "Calibrated"));
        gameRatings.usernameByUserId().forEach((userId, username) -> {
            MatchmakingRating rating = gameRatings.ratingByUserId().get(userId);
            if (rating == null) {
                rows.add(rowFormat.formatted(username, "unrated", "-"));
                return;
            }
            rows.add(rowFormat.formatted(
                    username,
                    MatchmakingRatingEventService.toDisplayRating(rating.rating()),
                    "%.0f%%".formatted(rating.calibrationPercent())));
        });
        return "```\n" + String.join("\n", rows) + "\n```\n";
    }

    private static String describeSkillTier(SkillTier skillTier) {
        if (skillTier == null) {
            return "none (fewer than half the players are rated)";
        }
        return skillTier.getDisplayName() + " (" + skillTier.getLabel() + ")";
    }
}
