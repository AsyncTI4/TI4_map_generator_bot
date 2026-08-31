package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
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
        assertThat(report).contains("  - 0 planets: 0% (0/1; 50%)\n");
        assertThat(report).contains("  - 1-2 planets: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldGiveZeroPlanetsABandOfItsOwnAheadOfOneAndTwo() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra", "wellon");
        addPlayer(game, "jolnar", false, "jol", "nar", "vefutii", "quann");

        String report = render(List.of(game));

        assertThat(report).contains("  - 0 planets: 100% (1/1; 33.33%)\n");
        assertThat(report).contains("  - 1-2 planets: 0% (0/2; 66.67%)\n");
    }

    @Test
    void shouldPoolCountsIntoOddStartingBandsTwoWideAndCapTheLastOne() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord");
        takePlanets(game.getPlayerFromColorOrFaction("sol"), 4);
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        takePlanets(game.getPlayerFromColorOrFaction("letnev"), 20);

        String report = render(List.of(game));

        assertThat(report).contains("  - 3-4 planets: 100% (1/1; 50%)\n");
        assertThat(report).contains("  - 11+ planets: 0% (0/1; 50%)\n");
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
    void shouldCountDeepwroughtCoexistingOnItsHomePlanetAsHoldingIt() {
        Game game = newGame("1");
        game.setTile(new Tile("95", "101"));
        Player deepwrought = addPlayer(game, "deepwrought", false);
        addPlayer(game, "sol", true, "jord", "ikatena");
        // Sol took Ikatena, but the Deepwrought are still standing on it.
        putInfantryOn(game, "ikatena", deepwrought);

        String report = render(List.of(game));

        assertThat(report).contains("- **All factions**: 0/2 (0%) of players lost a home planet\n");
    }

    /** The combined row is every faction summed, so a rescue only one of them can earn stays off it. */
    @Test
    void shouldKeepTheCoexistedThroughLineOffTheCombinedRow() {
        String report = render(repeatGame(MINIMUM_SAMPLE, game -> {
            game.setTile(new Tile("95", "101"));
            Player deepwrought = addPlayer(game, "deepwrought", false);
            addPlayer(game, "sol", true, "jord", "ikatena");
            putInfantryOn(game, "ikatena", deepwrought);
        }));

        assertThat(report).contains("- **All factions**: 0/50 (0%) of players lost a home planet\n");
        assertThat(report).contains("homes lost. Coexisted through 25/25 (100%) of home system losses.\n");
    }

    /** Every other faction has to actually control its home planets to keep them. */
    @Test
    void shouldNotLetAnyOtherFactionCoexistThroughAHomePlanetLoss() {
        Game game = newGame("1");
        game.setTile(new Tile("01", "101"));
        Player letnev = addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        addPlayer(game, "sol", true, "jord");
        // Letnev is standing on Jord, which does nothing for Sol or for Letnev's own home.
        putInfantryOn(game, "jord", letnev);

        String report = render(List.of(game));

        assertThat(report).contains("- **All factions**: 0/2 (0%) of players lost a home planet\n");
        assertThat(report).doesNotContain("Coexisted through");
    }

    @Test
    void shouldStillCountALossWhenDeepwroughtIsNotStandingOnItsHome() {
        Game game = newGame("1");
        game.setTile(new Tile("95", "101"));
        addPlayer(game, "deepwrought", false);
        addPlayer(game, "sol", true, "jord", "ikatena");

        String report = render(List.of(game));

        assertThat(report).contains("- **All factions**: 1/2 (50%) of players lost a home planet.");
        assertThat(report).doesNotContain("Coexisted through");
    }

    @Test
    void shouldBandTheCoexistedPlanetCountAndAverageIt() {
        Game game = newGame("1");
        game.setTile(new Tile("19", "101"));
        game.setTile(new Tile("20", "102"));
        Player sol = addPlayer(game, "sol", true, "jord");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra", "wellon", "vefutii", "abyz");
        // Sol controls neither Wellon nor Vefut II but is standing on both.
        putInfantryOn(game, "wellon", sol);
        putInfantryOn(game, "vefutii", sol);

        String report = render(List.of(game));

        assertThat(report).contains("### Win rate by planets coexisted on\n");
        assertThat(report)
                .contains("- **All factions**: 1.00 planets coexisted on on average, 50% win rate from 2 players");
        assertThat(report).contains("  - 0 planets: 0% (0/1; 50%)\n");
        assertThat(report).contains("  - 2 planets: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldCollectEveryCoexistenceAtOrAboveFiveIntoOneBand() {
        Game game = newGame("1");
        // Wellon on 19, Vefut II on 20, Abyz on 38, Arinam and Meer both on 37.
        game.setTile(new Tile("19", "101"));
        game.setTile(new Tile("20", "102"));
        game.setTile(new Tile("38", "103"));
        game.setTile(new Tile("37", "104"));
        Player sol = addPlayer(game, "sol", true, "jord");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra", "wellon", "vefutii", "abyz", "arinam", "meer");
        for (String planet : List.of("wellon", "vefutii", "abyz", "arinam", "meer")) {
            putInfantryOn(game, planet, sol);
        }

        String report = render(List.of(game));

        assertThat(report).contains("  - 5+ planets: 100% (1/1; 50%)\n");
        assertThat(report).contains("2.50 planets coexisted on on average");
    }

    @Test
    void shouldSayOnePlanetInTheSingularForTheCoexistBands() {
        Game game = newGame("1");
        game.setTile(new Tile("19", "101"));
        Player sol = addPlayer(game, "sol", true, "jord");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra", "wellon");
        putInfantryOn(game, "wellon", sol);

        assertThat(render(List.of(game))).contains("  - 1 planet: 100% (1/1; 50%)\n");
    }

    @Test
    void shouldSayNobodyLostAHomePlanetRatherThanDividingByZero() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        assertThat(render(List.of(game))).contains("- **All factions**: 0/2 (0%) of players lost a home planet\n");
    }

    @Test
    void shouldRankPlanetsByWinRateHighestFirst() {
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
    void shouldRankAPlanetHeldOnlyOnce() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        assertThat(render(List.of(game))).contains("* `100%` (1/1) Wellon\n");
    }

    @Test
    void shouldSayNoPlanetsWereHeldWhenEveryoneOnlyHasTheirHome() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        String report = render(List.of(game));

        assertThat(report).contains("### Win rate by planet controlled\n");
        assertThat(report).contains("- No planets were held.\n");
    }

    @Test
    void shouldLeaveOutTwilightsFallGames() {
        Game twilightsFall = newGame("1");
        twilightsFall.setTwilightsFallMode(true);
        addPlayer(twilightsFall, "sol", true, "jord", "wellon");
        addPlayer(twilightsFall, "letnev", false, "arcprime", "wrenterra");

        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(twilightsFall, false))
                .isFalse();
        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(twilightsFall, true))
                .isFalse();
        assertThat(render(List.of(twilightsFall))).contains("No games matched.\n");

        Game normal = newGame("2");
        addPlayer(normal, "sol", true, "jord", "wellon");
        addPlayer(normal, "letnev", false, "arcprime", "wrenterra");

        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(normal, false))
                .isTrue();
        assertThat(render(List.of(twilightsFall, normal))).contains("Games analyzed: 1 | Players analyzed: 2\n");
    }

    @Test
    void shouldTakeBothThundersEdgeAndProphecyOfKingsByDefault() {
        Game thundersEdge = pokAndThundersEdgeGame("1");
        Game prophecyOfKings = prophecyOfKingsGame("2");

        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(thundersEdge, false))
                .isTrue();
        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(prophecyOfKings, false))
                .isTrue();

        String report = render(List.of(thundersEdge, prophecyOfKings));
        assertThat(report).contains("_Thunder's Edge and Prophecy of Kings games.");
        assertThat(report).contains("Games analyzed: 2 | Players analyzed: 4\n");
    }

    @Test
    void shouldDropThundersEdgeGamesWhenPokOnly() {
        Game thundersEdge = pokAndThundersEdgeGame("1");
        Game prophecyOfKings = prophecyOfKingsGame("2");

        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(thundersEdge, true))
                .isFalse();
        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(prophecyOfKings, true))
                .isTrue();

        String report = render(List.of(thundersEdge, prophecyOfKings), true);
        assertThat(report).contains("_Prophecy of Kings games, no Thunder's Edge.");
        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 2\n");
    }

    @Test
    void shouldDropGamesThatAreNeitherThundersEdgeNorProphecyOfKings() {
        Game baseGame = pokAndThundersEdgeGame("1");
        baseGame.setThundersEdge(false);
        baseGame.setProphecyOfKings(false);

        assertThat(PlanetWinRateStatisticsService.isEligibleGameType(baseGame, false))
                .isFalse();
        assertThat(render(List.of(baseGame))).contains("No games matched.\n");
    }

    private static Game pokAndThundersEdgeGame(String suffix) {
        Game game = newGame(suffix);
        game.setThundersEdge(true);
        game.setProphecyOfKings(true);
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");
        return game;
    }

    private static Game prophecyOfKingsGame(String suffix) {
        Game game = pokAndThundersEdgeGame(suffix);
        game.setThundersEdge(false);
        return game;
    }

    @Test
    void shouldReadHomePlanetsOffTheBoardWhenTheFactionFileHasNone() {
        // Older games stored Keleres as a bare "keleres", which has no faction model behind it.
        Game game = newGame("1");
        game.setTile(new Tile("92new", "101"));
        Player keleres = addPlayer(game, "keleres", true, "archonrenk", "archontauk", "wellon");
        keleres.setPlayerStatsAnchorPosition("101");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        String report = render(List.of(game));

        assertThat(report).doesNotContain("### Skipped players");
        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 2\n");
        // Archon Ren and Archon Tau are home, so Wellon is the one planet taken outside it.
        assertThat(report).contains("- **All factions**: 0.50 non-home planets on average");
        assertThat(report).contains("* `100%` (1/1) Wellon\n");
        assertThat(report).doesNotContain("Archon Ren").doesNotContain("Archon Tau");
    }

    @Test
    void shouldFindALegacyKeleresHomeWithoutAPositionToGoOn() {
        // pbd298-era games store a bare "keleres" and no home system position, so the only thing
        // naming their home is the Keleres tile sitting on the board.
        Game game = newGame("1");
        game.setTile(new Tile("93new", "101"));
        addPlayer(game, "keleres", true, "valkk", "ylirk", "avark", "wellon");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        String report = render(List.of(game));

        assertThat(report).doesNotContain("### Skipped players");
        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 2\n");
        assertThat(report).contains("- **All factions**: 0.50 non-home planets on average");
        assertThat(report).contains("* `100%` (1/1) Wellon\n");
    }

    @Test
    void shouldStillSkipAPlayerWhoseFactionHasNoHomeworldAnywhereOnTheBoard() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "keleres", false, "wellon");

        String report = render(List.of(game));

        assertThat(report).contains("### Skipped players\n");
        assertThat(report).contains("- `keleres` - 1 player(s), e.g. game `planet-stats-1`\n");
    }

    @Test
    void shouldKeepEveryFactionHomeworldOutOfThePerPlanetRanking() {
        // Nobody at this table is Letnev or Arborec, so their homeworlds are not covered by any
        // seated player's home planets - the planet data is what has to keep them out.
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "arcprime", "wrenterra", "nestphar", "wellon");
        addPlayer(game, "jolnar", false, "jol", "nar");

        String report = render(List.of(game));

        assertThat(report)
                .doesNotContain("Arc Prime")
                .doesNotContain("Wren Terra")
                .doesNotContain("Nestphar");
        assertThat(report).contains("* `100%` (1/1) Wellon\n");
        // They are still ground taken outside Sol's own home system.
        assertThat(report).contains("- **All factions**: 2.00 non-home planets on average");
    }

    @Test
    void shouldBreakDownSkippedPlayersByFactionWithAGameToLookAt() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "bluetf", false);
        addPlayer(game, "redtf", false);

        String report = render(List.of(game));

        assertThat(report).contains("Games analyzed: 1 | Players analyzed: 1\n");
        assertThat(report).contains("### Skipped players\n");
        assertThat(report).contains("2 player(s) had no home planets on file for their faction");
        assertThat(report).contains("- `bluetf` - 1 player(s), e.g. game `planet-stats-1`\n");
        assertThat(report).contains("- `redtf` - 1 player(s), e.g. game `planet-stats-1`\n");
    }

    @Test
    void shouldLeaveOutTheSkippedSectionWhenEveryHomeWasIdentified() {
        Game game = newGame("1");
        addPlayer(game, "sol", true, "jord", "wellon");
        addPlayer(game, "letnev", false, "arcprime", "wrenterra");

        assertThat(render(List.of(game))).doesNotContain("### Skipped players");
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
        return render(games, false);
    }

    private static String render(List<Game> games, boolean pokOnly) {
        return String.join("", PlanetWinRateStatisticsService.buildReport(games, pokOnly));
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

    private static void putInfantryOn(Game game, String planet, Player player) {
        // Coexistence is read through the player's own unit models, so they have to own infantry.
        player.addOwnedUnitByID("infantry");
        game.getUnitHolderFromPlanet(planet).addUnit(Units.getUnitKey(UnitType.Infantry, player.getColorID()), 1);
    }

    private static void takePlanets(Player player, int count) {
        IntStream.range(0, count).mapToObj(i -> "filler-" + i).forEach(player.getPlanets()::add);
    }
}
