package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class SupportWinRateStatisticsServiceTest extends BaseTi4Test {

    private static final int MINIMUM_SAMPLE = 25;

    private static final int VICTORY_POINT_GOAL = 3;

    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "orange");

    @Test
    void shouldBandPlayersByHowManySupportsTheyHeld() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        Player jolnar = addPlayer(game, "jolnar", false);
        playSupport(letnev, sol);
        playSupport(jolnar, sol);

        String report = render(List.of(game));

        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 3\n");
        assertThat(report).contains("- **All players**: 0.67 supports held on average, from 3 players\n");
        assertThat(report).contains("  - 0 supports: 0% (0/2; 66.67%)\n");
        assertThat(report).contains("  - 2 supports: 100% (1/1; 33.33%)\n");
    }

    @Test
    void shouldSayOneSupportInTheSingular() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        playSupport(addPlayer(game, "letnev", false), sol);

        assertThat(render(List.of(game))).contains("  - 1 support: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldSplitOnWhereAPlayersOwnSupportEndedUp() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        Player jolnar = addPlayer(game, "jolnar", false);
        Player hacan = addPlayer(game, "hacan", false);
        // Letnev's support was played for the point, Jol-Nar's was traded but never played, and
        // Hacan's left the game entirely.
        playSupport(letnev, sol);
        handSupportOver(jolnar, sol);
        discardSupport(hacan);

        String report = render(List.of(game));

        assertThat(report).contains("### Win rate by what became of your own support\n");
        assertThat(report).contains("- Kept it: 100% win rate (1/1; 25% of players)\n");
        assertThat(report).contains("- Given away and played for the point: 0% win rate (0/1; 25% of players)\n");
        assertThat(report).contains("- Given away but never played: 0% win rate (0/1; 25% of players)\n");
        assertThat(report).contains("- No longer anywhere in the game: 0% win rate (0/1; 25% of players)\n");
    }

    @Test
    void shouldLeavePlayersWhoOwnNoSupportOutOfTheOwnSupportSection() {
        Game game = newGame("1");
        addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        letnev.removeOwnedPromissoryNoteByID(letnev.getColor() + "_sftt");
        letnev.removePromissoryNote(letnev.getColor() + "_sftt");

        String report = render(List.of(game));

        assertThat(report).contains("Players analyzed: 2\n");
        assertThat(report)
                .contains("_1 player(s) owned no Support for the Throne at all and are left out of this section._\n");
        assertThat(report).contains("- Kept it: 100% win rate (1/1; 100% of players)\n");
    }

    @Test
    void shouldGiveAFactionItsOwnRowOnlyOnceBothSidesOfTheSplitAreSampled() {
        Consumer<Game> table = game -> {
            Player sol = addPlayer(game, "sol", true);
            playSupport(addPlayer(game, "letnev", false), sol);
        };

        // Sol always holds a support and Letnev never does, so neither side ever splits.
        assertThat(render(repeatGame(MINIMUM_SAMPLE, table)))
                .contains("- No faction had enough players on both sides of the split.\n");
    }

    @Test
    void shouldRankFactionsByTheGapBetweenHoldingASupportAndNot() {
        List<Game> games = new ArrayList<>();
        // Sol wins whenever it is handed a support and loses when it is not.
        games.addAll(repeatGame(MINIMUM_SAMPLE, "won", game -> {
            Player sol = addPlayer(game, "sol", true);
            playSupport(addPlayer(game, "letnev", false), sol);
        }));
        games.addAll(repeatGame(MINIMUM_SAMPLE, "lost", game -> {
            addPlayer(game, "sol", false);
            addPlayer(game, "letnev", true);
        }));

        String report = render(games);

        assertThat(report)
                .contains("- **All factions**: +66.7 pts - 100% (25/25) holding one, 33.33% (25/75) holding none\n");
        assertThat(report).contains("+100.0 pts - 100% (25/25) holding one, 0% (0/25) holding none\n");
        assertThat(report).contains("The Federation of Sol");
        // Letnev never held one, so it has no split to report.
        assertThat(report).doesNotContain("Barony");
    }

    @Test
    void shouldCountTwoPlayersHoldingEachOthersSupportAsOneSwap() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        Player jolnar = addPlayer(game, "jolnar", false);
        playSupport(sol, letnev);
        playSupport(letnev, sol);
        playSupport(jolnar, sol);

        String report = render(List.of(game));

        assertThat(report).contains("### Support swaps\n");
        assertThat(report).contains("- Games with at least one swap: 1/1 (100%)\n");
        assertThat(report).contains("- Swaps per game: 1.00 on average\n");
        assertThat(report).contains("  - 1 swap: 1 game(s) (100%)\n");
        assertThat(report).contains("- Supports played into a swap: 2/3 (66.67% of the supports played)\n");
        assertThat(report)
                .contains("- Win rate after giving a support away: 50% (1/2) in a swap, 0% (0/1)" + " outside one\n");
    }

    @Test
    void shouldNotCallAOneWaySupportASwap() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        playSupport(addPlayer(game, "letnev", false), sol);

        String report = render(List.of(game));

        assertThat(report).contains("- Games with at least one swap: 0/1 (0%)\n");
        assertThat(report).contains("- Swaps per game: 0.00 on average\n");
        assertThat(report).contains("  - 0 swaps: 1 game(s) (100%)\n");
        assertThat(report).contains("- Supports played into a swap: 0/1 (0% of the supports played)\n");
    }

    /** A support handed over but left in hand is a trade, not a swap - nobody scored anything. */
    @Test
    void shouldNotCountUnplayedSupportsAsASwap() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        handSupportOver(sol, letnev);
        handSupportOver(letnev, sol);

        String report = render(List.of(game));

        assertThat(report).contains("- Games with at least one swap: 0/1 (0%)\n");
        assertThat(report).contains("- Supports played into a swap: no supports were played at all\n");
        assertThat(report).contains("- Given away but never played: 50% win rate (1/2; 100% of players)\n");
    }

    @Test
    void shouldCountBothSwapsAtATableThatTradedTwice() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        Player jolnar = addPlayer(game, "jolnar", false);
        Player hacan = addPlayer(game, "hacan", false);
        playSupport(sol, letnev);
        playSupport(letnev, sol);
        playSupport(jolnar, hacan);
        playSupport(hacan, jolnar);

        String report = render(List.of(game));

        assertThat(report).contains("- Swaps per game: 2.00 on average\n");
        assertThat(report).contains("  - 2 swaps: 1 game(s) (100%)\n");
        assertThat(report).contains("- Supports played into a swap: 4/4 (100% of the supports played)\n");
    }

    @Test
    void shouldSayNothingMatchedWhenNoGameHadAWinner() {
        Game game = newGame("1");
        addPlayer(game, "sol", false);
        addPlayer(game, "letnev", false);

        String report = render(List.of(game));

        assertThat(report).contains("No games matched.\n");
        assertThat(report).doesNotContain("### Support swaps");
    }

    private static List<Game> repeatGame(int count, Consumer<Game> seatPlayers) {
        return repeatGame(count, "", seatPlayers);
    }

    private static List<Game> repeatGame(int count, String prefix, Consumer<Game> seatPlayers) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    Game game = newGame(prefix + i);
                    seatPlayers.accept(game);
                    return game;
                })
                .toList();
    }

    private static String render(List<Game> games) {
        return String.join("", SupportWinRateStatisticsService.buildReport(games));
    }

    private static Game newGame(String suffix) {
        Game game = new Game();
        game.setName("support-stats-" + suffix);
        // The goal has to sit above the most supports anyone at these tables holds, or a player
        // handed a support would meet it too and the game would have no single winner.
        game.setVp(VICTORY_POINT_GOAL);
        game.setRound(3);
        game.setHasEnded(true);
        return game;
    }

    private static Player addPlayer(Game game, String faction, boolean isWinner) {
        Player player = game.addPlayer(faction + "-user-" + game.getName(), faction);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
        player.addOwnedPromissoryNoteByID(player.getColor() + "_sftt");
        player.setPromissoryNote(player.getColor() + "_sftt");
        if (isWinner) {
            IntStream.rangeClosed(1, VICTORY_POINT_GOAL)
                    .forEach(i -> player.setSecretScored("so-" + i + "-" + faction + "-" + game.getName()));
        }
        return player;
    }

    /** The receiver played it to their play area, so it is a victory point against the giver. */
    private static void playSupport(Player giver, Player receiver) {
        giver.removePromissoryNote(giver.getColor() + "_sftt");
        receiver.addPromissoryNoteToPlayArea(giver.getColor() + "_sftt");
    }

    /** Traded across the table but never played. */
    private static void handSupportOver(Player giver, Player receiver) {
        giver.removePromissoryNote(giver.getColor() + "_sftt");
        receiver.setPromissoryNote(giver.getColor() + "_sftt");
    }

    private static void discardSupport(Player giver) {
        giver.removePromissoryNote(giver.getColor() + "_sftt");
    }
}
