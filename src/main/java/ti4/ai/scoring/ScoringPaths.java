package ti4.ai.scoring;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.brain.SearchMemo;
import ti4.ai.eval.DecidingSeat;
import ti4.game.Game;
import ti4.game.Player;
import ti4.website.model.WebObjectives;
import ti4.website.model.WebScoreBreakdown;
import ti4.website.model.WebScoreBreakdown.EntryState;
import ti4.website.model.WebScoreBreakdown.EntryType;
import ti4.website.model.WebScoreBreakdown.ScoreBreakdownEntry;

@UtilityClass
public class ScoringPaths {

    private static final String OBJECTIVES_MEMO = "paths.objectives";
    private static final String BREAKDOWN_MEMO = "paths.breakdown";

    public static int readyPoints(Game game, Player player) {
        if (AiExperiments.enabled(DecidingSeat.orSelf(game, player), AiExperiments.Experiment.READY_PUBLIC_POINTS)) {
            int otherPoints = entries(game, player).stream()
                    .filter(entry -> entry.getState() == EntryState.QUALIFIES)
                    .filter(entry -> entry.getType() != EntryType.PO_1 && entry.getType() != EntryType.PO_2)
                    .mapToInt(ScoringPaths::points)
                    .sum();
            int heroScores = player.getLeader("winnuhero")
                            .filter(hero -> hero.isActive() && !hero.isExhausted())
                            .isPresent()
                    ? 1
                    : 0;
            return PublicScoringPlan.points(game, player, ScoringReserve.scoringChances(game, player) + heroScores)
                    + otherPoints;
        }
        return entries(game, player).stream()
                .filter(entry -> entry.getState() == EntryState.QUALIFIES)
                .mapToInt(ScoringPaths::points)
                .sum();
    }

    public static boolean imperialAwaitsMecatol(Game game, Player player) {
        return entries(game, player).stream()
                .anyMatch(entry -> entry.getType() == EntryType.IMPERIAL && entry.getState() == EntryState.POTENTIAL);
    }

    private static int points(ScoreBreakdownEntry entry) {
        return entry.getPointValue() == null ? 0 : entry.getPointValue();
    }

    private static List<ScoreBreakdownEntry> entries(Game game, Player player) {
        WebScoreBreakdown breakdown = SearchMemo.remember(
                () -> WebScoreBreakdown.fromPlayer(player, game, objectives(game)), BREAKDOWN_MEMO, player);
        return breakdown.getEntries();
    }

    private static WebObjectives objectives(Game game) {
        return SearchMemo.remember(() -> WebObjectives.fromGame(game), OBJECTIVES_MEMO, game);
    }
}
