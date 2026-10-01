package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ActionCardReplayStatsServiceTest extends BaseTi4Test {

    // Four IDs share Sabotage's name, which is what makes it a 4-of.
    private static final Map<String, String> CARD_NAMES_BY_ID = Map.of(
            "overrule",
            GameStats.OVERRULE,
            "rise",
            "Rise of a Messiah",
            "veto",
            "Veto",
            "sabo1",
            GameStats.SABOTAGE,
            "sabo2",
            GameStats.SABOTAGE,
            "sabo3",
            GameStats.SABOTAGE,
            "sabo4",
            GameStats.SABOTAGE);

    @Test
    void shouldCountEveryPlayAfterTheFirstAsAReplay() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService(CARD_NAMES_BY_ID);
        // Overrule played three times in one game: two replays, but only one game with a replay.
        stats.accumulate(gameWithPlays(GameStats.OVERRULE, GameStats.OVERRULE, GameStats.OVERRULE), null);
        stats.accumulate(gameWithPlays(GameStats.OVERRULE), null);
        stats.accumulate(gameWithPlays("Veto"), null);
        stats.accumulate(gameWithPlays(), null);

        String rendered = render(stats);
        assertThat(rendered)
                .contains("- Overrule: 2 replays (50% of 4 plays), replayed in 25% of games (1/4);"
                        + " first plays won - (0/0), replays won - (0/0)\n");
        assertThat(rendered)
                .contains("- **All 1-ofs:** 2 replays (40% of 5 plays), with a 1-of replayed in 25% of games (1/4);"
                        + " first plays won - (0/0), replays won - (0/0)\n");
    }

    @Test
    void shouldCountACanceledPlayTowardReplays() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService(CARD_NAMES_BY_ID);
        Game game = new Game();
        game.getGameStats().recordAcPlay("Veto", null);
        game.getGameStats().markLatestPlayCanceled("Veto");
        game.getGameStats().recordAcPlay("Veto", null);
        stats.accumulate(game, null);

        // The canceled Veto still left the hand, so seeing it again is a replay.
        assertThat(render(stats))
                .contains("- Veto: 1 replay (50% of 2 plays), replayed in 100% of games (1/1);"
                        + " first plays won - (0/0), replays won - (0/0)\n");
    }

    @Test
    void shouldLeaveOutCardsWithMoreThanOneCopy() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService(CARD_NAMES_BY_ID);
        // Several Sabotages in one game are just different copies, not replays.
        stats.accumulate(gameWithPlays("Sabotage", "Sabotage", "Rise of a Messiah"), null);

        String rendered = render(stats);
        assertThat(rendered).doesNotContain("Sabotage");
        assertThat(rendered)
                .contains("- **All 1-ofs:** 0 replays (0% of 1 play), with a 1-of replayed in 0% of games (0/1);"
                        + " first plays won - (0/0), replays won - (0/0)\n");
        assertThat(rendered).contains("_3 other 1-ofs never replayed._\n");
    }

    @Test
    void shouldSaySoWhenNoOneOfWasPlayed() {
        ActionCardReplayStatsService stats = new ActionCardReplayStatsService(CARD_NAMES_BY_ID);
        stats.accumulate(gameWithPlays("Sabotage"), null);

        assertThat(render(stats)).contains("No 1-of plays matched the selected filters.\n");
    }

    @Test
    void shouldSplitWinsBetweenFirstPlaysAndReplays() {
        Game game = new Game();
        Player winner = game.addPlayer("winner", "winner");
        Player loser = game.addPlayer("loser", "loser");
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, loser);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, winner);
        // A canceled replay never resolved, so like the Impact Score it isn't weighed against the win.
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, winner);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        game.getGameStats().recordAcPlay("Veto", winner);

        ActionCardReplayStatsService stats = new ActionCardReplayStatsService(CARD_NAMES_BY_ID);
        stats.accumulate(game, winner);

        String rendered = render(stats);
        assertThat(rendered)
                .contains("- Overrule: 2 replays (66.67% of 3 plays), replayed in 100% of games (1/1);"
                        + " first plays won 0% (0/1), replays won 100% (1/1)\n");
        assertThat(rendered)
                .contains("- **All 1-ofs:** 2 replays (50% of 4 plays), with a 1-of replayed in 100% of games (1/1);"
                        + " first plays won 50% (1/2), replays won 100% (1/1)\n");
    }

    @Test
    void shouldFlagAGameWhoseDiscardLostMostOfTheOneOfsItPlayed() {
        Map<String, String> cardNamesById = new HashMap<>();
        Map<String, Integer> wholeDiscard = new HashMap<>();
        Map<String, Integer> halfTheDiscard = new HashMap<>();
        Game kept = new Game();
        Game reshuffled = new Game();
        for (int i = 0; i < 10; i++) {
            cardNamesById.put("card" + i, "Card " + i);
            kept.getGameStats().recordAcPlay("Card " + i, null);
            reshuffled.getGameStats().recordAcPlay("Card " + i, null);
            wholeDiscard.put("card" + i, i);
            if (i < 5) {
                halfTheDiscard.put("card" + i, i);
            }
        }
        // Drawn again after the reshuffle, so its replay is one of those held by reshuffled games.
        reshuffled.getGameStats().recordAcPlay("Card 0", null);
        kept.setDiscardActionCards(wholeDiscard);
        reshuffled.setDiscardActionCards(halfTheDiscard);
        // Nine different 1-ofs is too few to tell, whatever the discard holds.
        Game tooShort = new Game();
        for (int i = 0; i < 9; i++) {
            tooShort.getGameStats().recordAcPlay("Card " + i, null);
        }

        ActionCardReplayStatsService stats = new ActionCardReplayStatsService(cardNamesById);
        stats.accumulate(kept, null);
        stats.accumulate(reshuffled, null);
        stats.accumulate(tooShort, null);

        assertThat(render(stats))
                .contains("- **Games that likely reshuffled:** 1 of 2 checked (50%), holding 1 replay\n");
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
        stats.appendTo(blocks);
        return String.join("", blocks);
    }
}
