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
        StringBuilder title = new StringBuilder("%sp, %svp, %s, %s pace"
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
        StringBuilder log = new StringBuilder(" Matchmaking formed a %s-player game — %s VP · %s · %s"
                .formatted(game.playerCount(), game.victoryPointGoal(), game.expansion(), game.pace()));
        for (MatchmakingQueueMember member : game.members()) {
            log.append("\n  • ").append(describePlayer(playerMatchmakingData.get(member), game.expansion()));
        }
        BotLogger.info(log.toString());
    }

    private static String titleRestrictions(MatchedGame game) {
        List<String> restrictions = game.restrictions();
        List<String> labels = new ArrayList<>();
        if (restrictions.contains(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION)) {
            labels.add("similar timezone");
        }
        if (restrictions.contains(MatchmakingOptions.TIGL_OPTION)) {
            labels.add("TIGL (" + game.tiglRank() + ")");
        }
        if (restrictions.contains(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION)) {
            labels.add("Floaters");
        }
        if (restrictions.contains(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION)) {
            labels.add("Warriors");
        }
        return String.join(", ", labels);
    }

    private static String describePlayer(PlayerMatchmakingData player, String expansion) {
        String name = JdaService.getUsername(player.userId());
        StringBuilder details = new StringBuilder(name != null ? name : "Unknown")
                .append(" (")
                .append(player.userId())
                .append(") — rating ")
                .append("%.1f".formatted(player.rating().getMean()))
                .append(" · ")
                .append(player.completedGames())
                .append(" games · waited ")
                .append(formatDuration(player.queueWait()));
        if (MatchmakingOptions.wantsTigl(player.restrictions())) {
            String rank =
                    MatchmakingOptions.usesFracturedRank(expansion) ? player.tiglFracturedRank() : player.tiglRank();
            details.append(" · TIGL ").append(rank);
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
