package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ActionCardPlayerStatsServiceTest extends BaseTi4Test {

    @Test
    void shouldCountAPlayerWhoPlayedNothingRatherThanLeavingThemOut() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        addPlayer(game, "quiet");
        recordPlays(game, winner, 3);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The quiet player is what the 3-5 band is measured against, so they have to be counted -
        // if they were dropped, the 3-5 band would read 100% of all players rather than half.
        String rendered = render(stats);
        assertThat(rendered).contains("- 0-2 cards: 0% win rate (0/1 players; 50% of all players)\n");
        assertThat(rendered).contains("- 3-5 cards: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldStillCountACardThatGotCanceled() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, loser);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        recordPlays(game, loser, 2);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The canceled Overrule never resolved, but it left the loser's hand all the same - which
        // is what carries them over into the 3-5 band instead of the 0-2 one.
        String rendered = render(stats);
        assertThat(rendered).contains("- 3-5 cards: 0% (0/1; 50%)\n");
        assertThat(rendered).doesNotContain("- 0-2 cards: 0%");
    }

    @Test
    void shouldCountOnlyTheWinnersSeatAsAWin() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        game.getGameStats().recordAcPlay("Flank Speed", winner);
        game.getGameStats().recordAcPlay("Flank Speed", loser);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // Both played one card, so they share a band - and only one of them won it.
        assertThat(render(stats)).contains("- 0-2 cards: 50% win rate (1/2 players; 100% of all players)\n");
    }

    @Test
    void shouldAssignAPlayWithNoPlayerToNobody() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The one player at the table is credited with nothing, and the whole sample is still just
        // them - a phantom carrying the orphaned play would show up as a second player here.
        assertThat(render(stats)).contains("- 0-2 cards: 100% win rate (1/1 players; 100% of all players)\n");
    }

    @Test
    void shouldDropAPlayByAnIdThatIsNotAtTheTable() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player stranger = new Player("stranger", "", game);
        recordPlays(game, stranger, 5);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // A seventh player counted here would never win, and would drag every rate down with them.
        assertThat(render(stats))
                .contains("- 0-2 cards: 100% win rate (1/1 players; 100% of all players)\n")
                .doesNotContain("- 3-5 cards:");
    }

    @Test
    void shouldCollectEveryPlayerAtOrAboveTheLastBandIntoOneRow() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 15);
        recordPlays(game, loser, 40);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The last band is open-ended, so 15 and 40 share it however far apart they are.
        assertThat(render(stats)).contains("- 15+ cards: 50% win rate (1/2 players; 100% of all players)\n");
    }

    @Test
    void shouldPoolCountsIntoBandsThreeWide() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 13);
        recordPlays(game, loser, 2);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        String rendered = render(stats);
        // 2 falls in the band starting at 0, and 13 in the one starting at 12.
        assertThat(rendered).contains("- 0-2 cards: 0% win rate (0/1 players; 50% of all players)\n");
        assertThat(rendered).contains("- 12-14 cards: 100% (1/1; 50%)\n");
        // Nobody played 3 through 11, so those bands are left out rather than divided by zero.
        assertThat(rendered).doesNotContain("- 3-5 cards:").doesNotContain("- 9-11 cards:");
    }

    @Test
    void shouldSpellOutTheLabelsOnTheFirstRowOnly() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 6);
        recordPlays(game, loser, 1);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        String rendered = render(stats);
        assertThat(rendered).contains("- 0-2 cards: 0% win rate (0/1 players; 50% of all players)\n");
        assertThat(rendered).contains("- 6-8 cards: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldMeasureTheBaselineFromTheSampleRatherThanAssumingIt() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser1 = addPlayer(game, "loser1");
        addPlayer(game, "loser2");
        addPlayer(game, "loser3");
        recordPlays(game, winner, 5);
        recordPlays(game, loser1, 3);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // One winner in four here, so it has to say 25% and not the 16.67% of a full table, and
        // eight cards over the four players who could have played them.
        assertThat(render(stats))
                .contains("- The average win rate is 25%. The average number of action cards played is 2.00.\n");
    }

    @Test
    void shouldAverageCardsOverThePlayersAboveTheLastBandAtTheirRealCounts() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 40);
        recordPlays(game, loser, 20);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // Both sit in the 15+ row, which cannot be summed back into a total - the average has to
        // come from the counts themselves or it would read 15.00.
        assertThat(render(stats)).contains("The average number of action cards played is 30.00.\n");
    }

    @Test
    void shouldAverageAFactionsCardsOverEveryGameItAppearedIn() {
        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();

        Game firstGame = new Game();
        Player sol = addPlayer(firstGame, "sol-player", "sol");
        recordPlays(firstGame, sol, 8);
        stats.accumulate(firstGame, sol);

        Game secondGame = new Game();
        Player otherSol = addPlayer(secondGame, "other-sol-player", "sol");
        Player xxcha = addPlayer(secondGame, "xxcha-player", "xxcha");
        recordPlays(secondGame, otherSol, 3);
        recordPlays(secondGame, xxcha, 4);
        stats.accumulate(secondGame, xxcha);

        // Sol played 8 then 3 across two games; Xxcha 4 across its one.
        String rendered = render(stats);
        assertThat(rendered).contains("- ` 5.50 from 2 games`");
        assertThat(rendered).contains("- ` 4.00 from 1 game`");
    }

    @Test
    void shouldSortFactionsByAverageCardsPlayedAndGiveEachItsOwnBlock() {
        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        Game game = new Game();
        Player sol = addPlayer(game, "sol-player", "sol");
        Player xxcha = addPlayer(game, "xxcha-player", "xxcha");
        recordPlays(game, sol, 3);
        recordPlays(game, xxcha, 9);
        stats.accumulate(game, sol);

        List<String> blocks = new ArrayList<>();
        stats.appendTo(blocks);

        // A block per faction, so the list can be split across messages however many factions it
        // runs to, and the heaviest hand leads.
        assertThat(blocks).anyMatch(block -> block.startsWith("- ` 9.00 from 1 game`"));
        assertThat(blocks).anyMatch(block -> block.startsWith("- ` 3.00 from 1 game`"));
        assertThat(indexOfBlockContaining(blocks, " 9.00 ")).isLessThan(indexOfBlockContaining(blocks, " 3.00 "));
    }

    @Test
    void shouldSayNothingMatchedWhenNoGameWasTracked() {
        String rendered = render(new ActionCardPlayerStatsService());

        assertThat(rendered)
                .contains("**Win rate by cards played**")
                .contains("**Cards played per faction**")
                .contains("No tracked action card plays matched the selected filters.");
    }

    private static int indexOfBlockContaining(List<String> blocks, String text) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    // A player only counts as real once it has both a faction and a color, and only real players
    // are the six the report divides by.
    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "orange");

    private static Player addPlayer(Game game, String userId) {
        return addPlayer(game, userId, userId);
    }

    private static Player addPlayer(Game game, String userId, String faction) {
        Player player = game.addPlayer(userId, userId);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
        return player;
    }

    private static void recordPlays(Game game, Player player, int plays) {
        for (int i = 0; i < plays; i++) {
            game.getGameStats().recordAcPlay("Flank Speed", player);
        }
    }

    private static String render(ActionCardPlayerStatsService stats) {
        List<String> blocks = new ArrayList<>();
        stats.appendTo(blocks);
        return String.join("", blocks);
    }
}
