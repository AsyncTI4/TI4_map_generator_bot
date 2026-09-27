package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.testUtils.BaseTi4Test;

class ActionCardReplayStatsServiceTest extends BaseTi4Test {

    private static final Map<String, Integer> COPIES =
            Map.of(GameStats.OVERRULE, 1, "Rise of a Messiah", 1, "Veto", 1, "Sabotage", 4);

    @Test
    void shouldCountEveryPlayAfterTheFirstAsAReplay() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService();
        // Overrule played three times in one game: two replays, but only one game with a replay.
        stats.accumulate(gameWithPlays(GameStats.OVERRULE, GameStats.OVERRULE, GameStats.OVERRULE));
        stats.accumulate(gameWithPlays(GameStats.OVERRULE));
        stats.accumulate(gameWithPlays("Veto"));
        stats.accumulate(gameWithPlays());

        String rendered = render(stats);
        assertThat(rendered).contains("- Overrule: 2 replays (50% of 4 plays), replayed in 25% of games (1/4)\n");
        assertThat(rendered)
                .contains("- **All 1-ofs:** 2 replays (40% of 5 plays), with a 1-of replayed in 25% of games (1/4)\n");
    }

    @Test
    void shouldCountACanceledPlayTowardReplays() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService();
        Game game = new Game();
        game.getGameStats().recordAcPlay("Veto", null);
        game.getGameStats().markLatestPlayCanceled("Veto");
        game.getGameStats().recordAcPlay("Veto", null);
        stats.accumulate(game);

        // The canceled Veto still left the hand, so seeing it again is a replay.
        assertThat(render(stats)).contains("- Veto: 1 replay (50% of 2 plays), replayed in 100% of games (1/1)\n");
    }

    @Test
    void shouldLeaveOutCardsWithMoreThanOneCopy() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService();
        // Several Sabotages in one game are just different copies, not replays.
        stats.accumulate(gameWithPlays("Sabotage", "Sabotage", "Rise of a Messiah"));

        String rendered = render(stats);
        assertThat(rendered).doesNotContain("Sabotage");
        assertThat(rendered)
                .contains("- **All 1-ofs:** 0 replays (0% of 1 play), with a 1-of replayed in 0% of games (0/1)\n");
        assertThat(rendered).contains("_3 other 1-ofs never replayed._\n");
    }

    @Test
    void shouldSaySoWhenNoOneOfWasPlayed() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService();
        stats.accumulate(gameWithPlays("Sabotage"));

        assertThat(render(stats)).contains("No 1-of plays matched the selected filters.\n");
    }

    private static Game gameWithPlays(String... cardNames) {
        Game game = new Game();
        for (String cardName : cardNames) {
            game.getGameStats().recordAcPlay(cardName, null);
        }
        return game;
    }

    private static String render(ActionCardReplayStatsService stats) {
        List<String> blocks = new ArrayList<>();
        stats.appendTo(blocks, COPIES);
        return String.join("", blocks);
    }
}
