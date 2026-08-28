package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
        game.getGameStats().recordAcPlay("Flank Speed", winner);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The quiet player is what the 1-card row is measured against, so they have to be counted.
        assertThat(stats.getPlayers(0)).isEqualTo(1);
        assertThat(stats.getWins(0)).isZero();
        assertThat(stats.getPlayers(1)).isEqualTo(1);
        assertThat(stats.getWins(1)).isEqualTo(1);
    }

    @Test
    void shouldStillCountACardThatGotCanceled() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, loser);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        game.getGameStats().recordAcPlay("Flank Speed", loser);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The canceled Overrule never resolved, but it left the loser's hand all the same.
        assertThat(stats.getPlayers(2)).isEqualTo(1);
        assertThat(stats.getWins(2)).isZero();
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

        assertThat(stats.getPlayers(1)).isEqualTo(2);
        assertThat(stats.getWins(1)).isEqualTo(1);
    }

    @Test
    void shouldAssignAPlayWithNoPlayerToNobody() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // The one player at the table is credited with nothing, and no phantom player appears.
        assertThat(stats.getPlayers(0)).isEqualTo(1);
        assertThat(stats.getPlayers(1)).isZero();
    }

    @Test
    void shouldDropAPlayByAnIdThatIsNotAtTheTable() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player stranger = new Player("stranger", "", game);
        game.getGameStats().recordAcPlay("Flank Speed", stranger);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // A seventh player counted here would never win, and would drag every rate down with them.
        assertThat(stats.getPlayers(0)).isEqualTo(1);
        assertThat(stats.getPlayers(1)).isZero();
    }

    @Test
    void shouldCollectEveryPlayerAtOrAboveTheCapIntoOneRow() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 15);
        recordPlays(game, loser, 40);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        assertThat(stats.getPlayers(15)).isEqualTo(2);
        assertThat(stats.getWins(15)).isEqualTo(1);
        assertThat(render(stats)).contains("- 15+ cards: 50% win rate (1/2 players; 100% of all players)\n");
    }

    @Test
    void shouldGiveEveryCountUnderTheCapItsOwnRow() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 14);
        recordPlays(game, loser, 1);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        String rendered = render(stats);
        assertThat(rendered).contains("- 1 card: 0% win rate (0/1 players; 50% of all players)\n");
        assertThat(rendered).contains("- 14 cards: 100% (1/1; 50%)\n");
        // Nobody played 2 through 13, so those rows are left out rather than divided by zero.
        assertThat(rendered).doesNotContain("- 2 cards:").doesNotContain("- 13 cards:");
    }

    @Test
    void shouldSpellOutTheLabelsOnTheFirstRowOnly() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        Player loser = addPlayer(game, "loser");
        recordPlays(game, winner, 3);
        recordPlays(game, loser, 1);

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        String rendered = render(stats);
        assertThat(rendered).contains("- 1 card: 0% win rate (0/1 players; 50% of all players)\n");
        assertThat(rendered).contains("- 3 cards: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldMeasureTheBaselineFromTheSampleRatherThanAssumingIt() {
        Game game = new Game();
        Player winner = addPlayer(game, "winner");
        addPlayer(game, "loser1");
        addPlayer(game, "loser2");
        addPlayer(game, "loser3");

        ActionCardPlayerStatsService stats = new ActionCardPlayerStatsService();
        stats.accumulate(game, winner);

        // One winner in four here, so the note has to say 25% and not the 16.7% of a full table.
        assertThat(render(stats)).contains("A player won 25% of the time across this sample");
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

        assertThat(stats.getGames("sol")).isEqualTo(2);
        assertThat(stats.getAverageCardsPlayed("sol")).isEqualTo(5.5, within(1e-9));
        assertThat(stats.getGames("xxcha")).isEqualTo(1);
        assertThat(stats.getAverageCardsPlayed("xxcha")).isEqualTo(4.0, within(1e-9));
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
