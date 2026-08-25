package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.service.statistics.ActionCardStatsService.PlayToWinCorrelationCount;
import ti4.testUtils.BaseTi4Test;

class ActionCardStatsServiceTest extends BaseTi4Test {

    @Test
    void shouldUseTheClassMaxPlaysAsExpectedDraws() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 100, "Direct Hit", 60, "Rise of a Messiah", 20),
                Map.of("Sabotage", 4, "Direct Hit", 4, "Rise of a Messiah", 1));

        assertThat(expectedDraws)
                .containsEntry("Sabotage", 100)
                .containsEntry("Direct Hit", 100)
                .containsEntry("Rise of a Messiah", 20);
    }

    @Test
    void shouldGiveEveryClassMemberTheFullClassMax() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 20, "Overrule", 30), Map.of("Sabotage", 4, "Overrule", 4));

        assertThat(expectedDraws).containsEntry("Sabotage", 30).containsEntry("Overrule", 30);
    }

    @Test
    void shouldSkipCardsNotInTheDeck() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 10, "Mystery Card", 5), Map.of("Sabotage", 4));

        assertThat(expectedDraws).containsOnlyKeys("Sabotage");
    }

    @Test
    void shouldSkipClassesWithNoPlays() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 10), Map.of("Sabotage", 4, "Rise of a Messiah", 1));

        // The 1-of class has zero plays, so no expected draws can be derived for it.
        assertThat(expectedDraws).containsOnlyKeys("Sabotage");
    }

    @Test
    void shouldIncludeUnplayedCardsWhoseClassHasPlays() {
        Map<String, Integer> expectedDraws = ActionCardStatsService.computeExpectedDraws(
                Map.of("Sabotage", 10), Map.of("Sabotage", 4, "Direct Hit", 4));

        // Direct Hit was never played, but its class max still approximates its draws.
        assertThat(expectedDraws).containsEntry("Direct Hit", 10);
    }

    @Test
    void shouldCountCancelsThatTheMigrationCouldNotAttributeToAPlayer() {
        Game game = new Game();
        Player winner = new Player("winner", "", game);
        // The legacy-save migration reconstructs a cancel it cannot attribute as a canceled play
        // with no player - the shape that used to be dropped before it was counted.
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, winner);

        Map<String, PlayToWinCorrelationCount> counts = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(game, winner, counts);

        PlayToWinCorrelationCount overrule = counts.get(GameStats.OVERRULE);
        assertThat(overrule.getPlaysIncludingCanceled()).isEqualTo(2);
        assertThat(overrule.getCanceled()).isEqualTo(1);
        // The unattributed cancel must not leak into anything win-attributed.
        assertThat(overrule.getTotal()).isEqualTo(1);
        assertThat(overrule.getWins()).isEqualTo(1);
    }

    @Test
    void shouldStillIgnoreUncancelledPlaysWithNoPlayer() {
        Game game = new Game();
        Player winner = new Player("winner", "", game);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);

        Map<String, PlayToWinCorrelationCount> counts = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(game, winner, counts);

        // An uncancelled play still feeds the win rate, so one with no player stays out entirely.
        assertThat(counts).isEmpty();
    }

    @Test
    void shouldLiftOnlyThePlayCountsAndOmegaWhenARecoveredCancelIsCounted() {
        // The same card either side of the fix: identical wins and uncancelled plays, but the
        // second run also counts one migration-recovered cancel. Expected draws are pinned here to
        // isolate the change; in the real report they are derived from the play counts, so they
        // move too.
        String withoutRecoveredCancel = renderOverrule(playToWinCount(1, 1, 1));
        String withRecoveredCancel = renderOverrule(playToWinCount(2, 1, 1));

        assertThat(withoutRecoveredCancel)
                .isEqualTo("- Overrule: 1 wins, 1 plays (100.0% win rate), 1 uncancelled plays (100.0% win rate),"
                        + " 10.0 Impact Score (wins vs ~draws), 10.0 Impact Score Ω (+0.2 win per cancel)\n");
        assertThat(withRecoveredCancel)
                .isEqualTo("- Overrule: 1 wins, 2 plays (50.0% win rate), 1 uncancelled plays (100.0% win rate),"
                        + " 10.0 Impact Score (wins vs ~draws), 12.0 Impact Score Ω (+0.2 win per cancel)\n");
    }

    private static String renderOverrule(PlayToWinCorrelationCount count) {
        StringBuilder message = new StringBuilder();
        ActionCardStatsService.appendPlayToWinCorrelationStats(
                message, Map.of(GameStats.OVERRULE, count), Map.of(GameStats.OVERRULE, 10));
        return message.toString();
    }

    private static PlayToWinCorrelationCount playToWinCount(int playsIncludingCanceled, int uncancelled, int wins) {
        PlayToWinCorrelationCount count = new PlayToWinCorrelationCount();
        for (int i = 0; i < playsIncludingCanceled; i++) {
            count.incrementPlaysIncludingCanceled();
        }
        for (int i = 0; i < uncancelled; i++) {
            count.incrementTotal();
        }
        for (int i = 0; i < wins; i++) {
            count.incrementWins();
        }
        return count;
    }
}
