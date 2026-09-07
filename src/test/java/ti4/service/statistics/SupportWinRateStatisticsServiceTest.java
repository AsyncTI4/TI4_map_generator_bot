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
    void shouldSplitOnWhetherAPlayerGaveTheirOwnSupportAway() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        Player jolnar = addPlayer(game, "jolnar", false);
        Player hacan = addPlayer(game, "hacan", false);
        // Letnev and Jol-Nar gave theirs to Sol; Sol and Hacan still hold their own.
        playSupport(letnev, sol);
        playSupport(jolnar, sol);

        String report = render(List.of(game));

        assertThat(report).contains("### Win rate by support location\n");
        assertThat(report).contains("- Kept it: 50% win rate (1/2; 50% of players)\n");
        assertThat(report).contains("- Gave away: 0% win rate (0/2; 50% of players)\n");
        assertThat(hacan.getPromissoryNotes()).containsKey(hacan.getColor() + "_sftt");
    }

    /** The Enlightenment ability leaves a player with no support to give. */
    @Test
    void shouldLeavePlayersWhoOwnNoSupportOutOfTheLocationSection() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        Player letnev = addPlayer(game, "letnev", false);
        Player jolnar = addPlayer(game, "jolnar", false);
        playSupport(jolnar, sol);
        letnev.removeOwnedPromissoryNoteByID(letnev.getColor() + "_sftt");
        letnev.removePromissoryNote(letnev.getColor() + "_sftt");

        String report = render(List.of(game));

        // Letnev is still one of the three players analyzed, just not one of the two with a support.
        assertThat(report).contains("Players analyzed: 3\n");
        assertThat(report).contains("- Kept it: 100% win rate (1/1; 50% of players)\n");
        assertThat(report).contains("- Gave away: 0% win rate (0/1; 50% of players)\n");
        assertThat(report).doesNotContain("owned no Support for the Throne");
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
    void shouldSplitEachWellSampledFactionOnHoldingASupport() {
        List<Game> games = new ArrayList<>();
        // Whoever is handed a support wins, so both Sol and Letnev see each side of the split.
        games.addAll(repeatGame(MINIMUM_SAMPLE, "sol", game -> {
            Player sol = addPlayer(game, "sol", true);
            playSupport(addPlayer(game, "letnev", false), sol);
        }));
        games.addAll(repeatGame(MINIMUM_SAMPLE, "letnev", game -> {
            addPlayer(game, "sol", false);
            Player letnev = addPlayer(game, "letnev", true);
            playSupport(addPlayer(game, "jolnar", false), letnev);
        }));

        String report = render(games);

        assertThat(report)
                .contains("- **All factions**: +100.0 pts - 100% (50/50) holding one, 0% (0/75) holding none\n");
        assertThat(report).contains("The Federation of Sol");
        assertThat(report).contains("The Barony of Letnev");
        // Jol-Nar only ever gave one away, so it has no with-a-support side to report.
        assertThat(report).doesNotContain("Jol-Nar");
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
        assertThat(report).contains("- Swaps per game: 1.00 on average\n");
        assertThat(report).contains("  - 1 swap: 1 game(s) (100%)\n");
        assertThat(report).contains("  - 1+ swaps: 1 game(s) (100%)\n");
        assertThat(report).contains("- Swap rate: 66.67% (2/3 of the supports played)\n");
        assertThat(report).contains("- Win rate after giving a support away:\n");
        assertThat(report).contains("  - Swap: 50% (1/2)\n");
        assertThat(report).contains("  - No swap: 0% (0/1)\n");
        // The swaps section is read before the faction split.
        assertThat(report.indexOf("### Support swaps")).isLessThan(report.indexOf("by faction"));
    }

    @Test
    void shouldNotCallAOneWaySupportASwap() {
        Game game = newGame("1");
        Player sol = addPlayer(game, "sol", true);
        playSupport(addPlayer(game, "letnev", false), sol);

        String report = render(List.of(game));

        assertThat(report).contains("- Swaps per game: 0.00 on average\n");
        assertThat(report).contains("  - 0 swaps: 1 game(s) (100%)\n");
        assertThat(report).contains("  - 1+ swaps: 0 game(s) (0%)\n");
        assertThat(report).contains("- Swap rate: 0% (0/1 of the supports played)\n");
    }

    /** Nobody played a support, so the table almost certainly agreed not to use them. */
    @Test
    void shouldDropGamesWhereEveryPlayerStillHoldsTheirOwnSupport() {
        Game noneplayed = newGame("1");
        addPlayer(noneplayed, "sol", true);
        addPlayer(noneplayed, "letnev", false);

        String report = render(List.of(noneplayed));

        assertThat(report).contains("No games matched.\n");
        assertThat(report).contains("Dropped 1 game(s) that purged Support for the Throne or never played one.\n");
    }

    @Test
    void shouldDropGamesFlaggedAsHavingPurgedSupports() {
        Game purged = newGame("1");
        Player sol = addPlayer(purged, "sol", true);
        playSupport(addPlayer(purged, "letnev", false), sol);
        purged.setStoredValue("removeSupports", "true");

        // A support is sitting in a play area, but the game says they were purged, so it is stale data.
        String report = render(List.of(purged));

        assertThat(report).contains("No games matched.\n");
        assertThat(report).contains("Dropped 1 game(s) that purged Support for the Throne or never played one.\n");
    }

    @Test
    void shouldDropGamesWhoseSupportsWereRemovedOutright() {
        Game purged = newGame("1");
        for (String faction : List.of("sol", "letnev")) {
            Player player = addPlayer(purged, faction, "sol".equals(faction));
            player.removeOwnedPromissoryNoteByID(player.getColor() + "_sftt");
            player.removePromissoryNote(player.getColor() + "_sftt");
        }

        assertThat(render(List.of(purged))).contains("No games matched.\n");
    }

    @Test
    void shouldStillCountGamesAlongsideDroppedOnes() {
        Game noneplayed = newGame("1");
        addPlayer(noneplayed, "sol", true);
        addPlayer(noneplayed, "letnev", false);

        Game played = newGame("2");
        Player sol = addPlayer(played, "sol", true);
        playSupport(addPlayer(played, "letnev", false), sol);

        String report = render(List.of(noneplayed, played));

        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 2\n");
        assertThat(report).contains("Dropped 1 game(s) that purged Support for the Throne or never played one.\n");
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
        assertThat(report).contains("  - 1+ swaps: 1 game(s) (100%)\n");
        assertThat(report).contains("- Swap rate: 100% (4/4 of the supports played)\n");
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
}
