package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.logging.BotLogger;

@UtilityClass
class MatchDescriber {

    static String threadTitle(MatchedGame game) {
        StringBuilder title = new StringBuilder();
        if (!game.tiglRanks().isEmpty()) {
            title.append(String.join("/", game.tiglRanks())).append(": ");
        }
        title.append("%sp, %svp, %s, %s pace"
                .formatted(
                        game.playerCount(),
                        game.victoryPointGoal(),
                        MatchmakingOptions.shortExpansionName(game.expansion()),
                        game.pace().toLowerCase(Locale.ROOT)));
        String restrictions = titleRestrictions(game);
        if (!restrictions.isEmpty()) {
            title.append(", ").append(restrictions);
        }
        return title.toString();
    }

    static String setupBody(MatchedGame game) {
        String restrictionsText = game.restrictions().isEmpty() ? "None" : String.join(", ", game.restrictions());
        return "The players were matched on the following game setup:\n"
                + "- **Player count:** " + game.playerCount() + "\n"
                + "- **Victory point goal:** " + game.victoryPointGoal() + "\n"
                + "- **Expansion:** " + game.expansion() + "\n"
                + "- **Pace:** " + game.pace() + "\n"
                + "- **Restrictions:** " + restrictionsText;
    }

    static void logFormedMatch(
            MatchedGame game, Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        double matchQuality = MatchQualityCalculator.matchQuality(game.members(), playerMatchmakingData);
        StringBuilder log = new StringBuilder(" Matchmade: ")
                .append("\n``` • ")
                .append(threadTitle(game))
                .append(" — match quality %.1f%%".formatted(matchQuality * 100));
        for (MatchmakingQueueMember member : game.members()) {
            log.append("\n  • ").append(describePlayer(playerMatchmakingData.get(member)));
        }
        log.append("```");
        BotLogger.info(log.toString());
    }

    private static String titleRestrictions(MatchedGame game) {
        List<String> restrictions = game.restrictions();
        List<String> labels = new ArrayList<>();
        if (restrictions.contains(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION)) {
            labels.add("similar timezone");
        }
        return String.join(", ", labels);
    }

    private static String describePlayer(PlayerMatchmakingData player) {
        String name = JdaService.getUsername(player.userId());
        StringBuilder details = new StringBuilder(name != null ? name : "Unknown")
                .append(" — rating ")
                .append("%.1f".formatted(player.rating().getMean()))
                .append(" · ")
                .append(player.completedGames())
                .append(" games · waited ")
                .append(formatDuration(player.queueWait()));
        if (player.tigl()) {
            details.append(" · TIGL ").append(String.join("/", player.tiglRanks()));
        }
        if (!player.restrictions().isEmpty()) {
            details.append(" · ").append(String.join(", ", player.restrictions()));
        }
        return details.toString();
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
