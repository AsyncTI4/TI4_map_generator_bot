package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class PlanetWinRateStatisticsServiceTest extends BaseTi4Test {

    private static final int MINIMUM_SAMPLE = 25;

    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "orange");

    @Test
    void shouldCountOnlyThePlanetsOutsideAPlayersOwnHomeSystem() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon", "vefutii");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        String report = render(List.of(game));

        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 2\n");
        assertThat(report)
                .contains("- **All factions**: 1.00 non-home planets on average, 50% win rate from 2 players");
        assertThat(report).contains("  - 0-1 planets: 0% (0/1; 50%)\n");
        assertThat(report).contains("  - 2-3 planets: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldPoolCountsIntoBandsTwoWideAndCapTheLastOne() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord");
        takePlanets(game.getPlayerFromColorOrFaction("sol"), 4);
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        takePlanets(game.getPlayerFromColorOrFaction("letnev"), 20);

        String report = render(List.of(game));

        assertThat(report).contains("  - 4-5 planets: 100% (1/1; 50%)\n");
        assertThat(report).contains("  - 12+ planets: 0% (0/1; 50%)\n");
        assertThat(report).contains("12.00 non-home planets on average");
    }

    @Test
    void shouldNeverCountOceans() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon", "ocean3", "ocean4", "ocean5");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        String report = render(List.of(game));

        assertThat(report).contains("- **All factions**: 0.50 non-home planets on average");
        assertThat(report)
                .doesNotContain("Deep Abyss")
                .doesNotContain("Brine Pool")
                .doesNotContain("Ice Shelf");
    }

    @Test
    void shouldLeaveTradeStationsOutOfTheNonHomePlanetCountButStillRankThem() {
        String report = render(repeatGame(MINIMUM_SAMPLE, game -> {
            addPlayer(game, "sol", true, "jord", "wellon", "thewatchtower");
            addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        }));

        assertThat(report).contains("- **All factions**: 0.50 non-home planets on average");
        assertThat(report).contains("* `100%` (25/25) The Watchtower\n");
    }

    @Test
    void shouldSplitTheWinRateOnWhetherAHomePlanetWasLost() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "letnev", false, "arcprime");

        String report = render(List.of(game));

        assertThat(report).contains("### Home planets lost\n");
        assertThat(report)
                .contains("- **All factions**: 1/2 (50%) of players lost a home planet. 0% win rate when they did,"
                        + " 100% when they did not\n");
    }

    @Test
    void shouldRenderFactionHomePlanetsLostInItsOwnShorterForm() {
        String report = render(repeatGame(MINIMUM_SAMPLE, game -> {
            addPlayer(game, "sol", true, "jord");
            addPlayer(game, "letnev", false, "arcprime");
        }));

        assertThat(report).contains("- **All factions**: 25/50 (50%) of players lost a home planet.");
        assertThat(report).contains("(100%) homes lost. 0% win rate, 0% otherwise\n");
    }

    @Test
    void shouldSayNobodyLostAHomePlanetRatherThanDividingByZero() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        assertThat(render(List.of(game))).contains("- **All factions**: 0/2 (0%) of players lost a home planet\n");
    }

    @Test
    void shouldRankPlanetsByWinRateOnceTheyClearTheSampleThreshold() {
        String report = render(repeatGame(MINIMUM_SAMPLE, game -> {
            addPlayer(game, "sol", true, "jord", "wellon");
            addPlayer(game, "letnev", false, "arcprime", "wrenterra", "vefutii");
        }));

        assertThat(report).contains("* `100%` (25/25) Wellon\n");
        assertThat(report).contains("* `  0%` (0/25) Vefut II\n");
        assertThat(report).doesNotContain("Jord").doesNotContain("Arc Prime").doesNotContain("Wren Terra");
        assertThat(report.indexOf("Wellon")).isLessThan(report.indexOf("Vefut II"));
    }

    @Test
    void shouldKeepAConqueredHomePlanetOutOfThePerPlanetRankingButCountItAsNonHome() {
        String report = render(repeatGame(MINIMUM_SAMPLE, game -> {
            addPlayer(game, "sol", true, "jord", "arcprime", "wellon");
            addPlayer(game, "letnev", false, "wrenterra");
        }));

        assertThat(report).doesNotContain("Arc Prime");
        assertThat(report).contains("* `100%` (25/25) Wellon\n");
        assertThat(report).contains("- **All factions**: 1.00 non-home planets on average");
        assertThat(report).contains("- **All factions**: 25/50 (50%) of players lost a home planet.");
    }

    @Test
    void shouldSuppressPlanetsBelowTheSampleThreshold() {
        String report = render(repeatGame(MINIMUM_SAMPLE - 1, game -> {
            addPlayer(game, "sol", true, "jord", "wellon");
            addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        }));

        assertThat(report).contains("### Win rate by planet controlled\n");
        assertThat(report).contains("- No planet was held that often.\n");
        assertThat(report).doesNotContain("Wellon");
    }

    @Test
    void shouldGiveAFactionItsOwnSectionOnlyOnceItHasEnoughPlayers() {
        Consumer<Game> table = game -> {
            addPlayer(game, "sol", true, "jord", "wellon");
            addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        };

        assertThat(render(repeatGame(MINIMUM_SAMPLE - 1, table))).doesNotContain("The Federation of Sol");

        String atThreshold = render(repeatGame(MINIMUM_SAMPLE, table));
        assertThat(atThreshold).contains("The Federation of Sol");
        assertThat(atThreshold).contains("1.00 non-home planets on average, 100% win rate from 25 players");
        assertThat(atThreshold).contains("0.00 non-home planets on average, 0% win rate from 25 players");
    }

    @Test
    void shouldSayNothingMatchedWhenNoGameHadAWinner() {
        Game game = newGame("1");
        addPlayer(game, "sol", false, "jord");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        String report = render(List.of(game));

        assertThat(report).contains("No games matched.\n");
        assertThat(report).doesNotContain("### Home planets lost");
    }

    private static List<Game> repeatGame(int count, Consumer<Game> seatPlayers) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    Game game = newGame(Integer.toString(i));
                    seatPlayers.accept(game);
                    return game;
                })
                .toList();
    }

    private static String render(List<Game> games) {
        return String.join("", PlanetWinRateStatisticsService.buildReport(games));
    }

    private static Game newGame(String suffix) {
        Game game = new Game();
        game.setName("planet-stats-" + suffix);
        game.setVp(1);
        game.setRound(3);
        game.setHasEnded(true);
        return game;
    }

    private static Player addPlayer(Game game, String faction, boolean isWinner, String... planets) {
        Player player = game.addPlayer(faction + "-user-" + game.getName(), faction);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
        if (isWinner) {
            player.setSecretScored("so-" + faction + "-" + game.getName());
        }
        player.getPlanets().addAll(List.of(planets));
        return player;
    }

    private static void takePlanets(Player player, int count) {
        IntStream.range(0, count).mapToObj(i -> "filler-" + i).forEach(player.getPlanets()::add);
    }
}
