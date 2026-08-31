package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void shouldNameTheFlavourAPlayerStillSittingOnTheirHomeworldWas() {
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("mollprimusk", "wellon")))
                .isEqualTo("keleresm");
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("archonrenk", "archontauk")))
                .isEqualTo("keleresx");
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("valkk", "ylirk", "avark")))
                .isEqualTo("keleresa");
    }

    /**
     * Keleres predates the re-skinned Keleres planets, so the games this command exists for hold the
     * base faction's planet ids. The player's faction is already known to be Keleres, so these name
     * a flavour just as well as the k-suffixed ones.
     */
    @Test
    void shouldNameTheFlavourFromTheBaseFactionsPlanetIds() {
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("mollprimus", "wellon")))
                .isEqualTo("keleresm");
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("archonren", "archontau")))
                .isEqualTo("keleresx");
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("valk", "avar", "ylir")))
                .isEqualTo("keleresa");
    }

    @Test
    void shouldNameTheFlavourFromTheTileThePlayerIsAnchoredTo() {
        Game mentakHome = gameWithTiles("02");
        assertThat(RunAgainstAllGames.factionFromTheirOwnHomeTile(mentakHome, anchoredAt(mentakHome, "101")))
                .isEqualTo("keleresm");

        Game xxchaHome = gameWithTiles("14");
        assertThat(RunAgainstAllGames.factionFromTheirOwnHomeTile(xxchaHome, anchoredAt(xxchaHome, "101")))
                .isEqualTo("keleresx");

        Game argentHome = gameWithTiles("58");
        assertThat(RunAgainstAllGames.factionFromTheirOwnHomeTile(argentHome, anchoredAt(argentHome, "101")))
                .isEqualTo("keleresa");
    }

    /**
     * A board-wide sweep must not read the base home systems, since 02, 14 and 58 belong to Mentak,
     * Xxcha and Argent whenever those factions are the ones playing them.
     */
    @Test
    void shouldNotSweepTheBoardForBaseFactionHomeSystems() {
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("02", "19")))
                .isNull();
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("14", "58")))
                .isNull();
    }

    @Test
    void shouldNotGuessFromAnAnchorPointingAtNothingKeleres() {
        Game game = gameWithTiles("19");
        assertThat(RunAgainstAllGames.factionFromTheirOwnHomeTile(game, anchoredAt(game, "101")))
                .isNull();
        assertThat(RunAgainstAllGames.factionFromTheirOwnHomeTile(game, anchoredAt(game, "999")))
                .isNull();
        assertThat(RunAgainstAllGames.factionFromTheirOwnHomeTile(game, anchoredAt(game, null)))
                .isNull();
    }

    @Test
    void shouldNotGuessFromPlanetsThatNameNoFlavourOrMoreThanOne() {
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("wellon", "vefutii")))
                .isNull();
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding()))
                .isNull();
        // Conquering another Keleres home would make the planets disagree - the board decides then.
        assertThat(RunAgainstAllGames.factionFromTheirHomePlanets(playerHolding("valkk", "mollprimusk")))
                .isNull();
    }

    @Test
    void shouldNameTheFlavourFromTheHomeSystemLeftOnTheBoard() {
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("93new", "19", "20")))
                .isEqualTo("keleresa");
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("92new")))
                .isEqualTo("keleresx");
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("94new")))
                .isEqualTo("keleresm");
    }

    @Test
    void shouldNotGuessFromABoardWithNoKeleresHomeOrMoreThanOne() {
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("19", "20")))
                .isNull();
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles()))
                .isNull();
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("92new", "94new")))
                .isNull();
    }

    /** The plain Xxcha, Argent and Mentak homes are different systems, and must not be read as Keleres. */
    @Test
    void shouldNotReadThePlainHomeSystemsAsKeleres() {
        assertThat(RunAgainstAllGames.factionFromTheOnlyKeleresHomeOnTheBoard(gameWithTiles("92", "93", "94")))
                .isNull();
    }

    private static Player playerHolding(String... planets) {
        Game game = new Game();
        Player player = game.addPlayer("user", "user");
        player.setFaction("keleres");
        player.setColor("red");
        player.getPlanets().addAll(List.of(planets));
        return player;
    }

    @Test
    void shouldNameTheFlavourFromABaseHomeSystemWhoseOwnFactionIsNotPlaying() {
        // The Xxcha home is on the board and no Xxcha is at the table, so it is the Keleres seat.
        Game game = gameWithTiles("14", "19", "20");
        seat(game, "keleres");
        seat(game, "sol");

        assertThat(RunAgainstAllGames.factionFromAnOrphanedBaseHome(game)).isEqualTo("keleresx");
    }

    @Test
    void shouldNotClaimABaseHomeSystemItsOwnFactionIsPlaying() {
        Game game = gameWithTiles("14", "19");
        seat(game, "keleres");
        seat(game, "xxcha");

        assertThat(RunAgainstAllGames.factionFromAnOrphanedBaseHome(game)).isNull();
    }

    @Test
    void shouldNotClaimABaseHomeSystemItsOwnVariantFactionIsPlaying() {
        Game game = gameWithTiles("02", "19");
        seat(game, "keleres");
        seat(game, "pi_mentak");

        assertThat(RunAgainstAllGames.factionFromAnOrphanedBaseHome(game)).isNull();
    }

    @Test
    void shouldNotGuessBetweenTwoOrphanedBaseHomeSystems() {
        Game game = gameWithTiles("14", "58");
        seat(game, "keleres");
        seat(game, "sol");

        assertThat(RunAgainstAllGames.factionFromAnOrphanedBaseHome(game)).isNull();
    }

    private static void seat(Game game, String faction) {
        Player player = game.addPlayer(faction + "-user", faction);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
    }

    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "orange");

    private static Player anchoredAt(Game game, String position) {
        Player player = game.addPlayer("user-" + position, "user");
        player.setFaction("keleres");
        player.setColor("red");
        player.setPlayerStatsAnchorPosition(position);
        return player;
    }

    private static Game gameWithTiles(String... tileIds) {
        Game game = new Game();
        int position = 101;
        for (String tileId : tileIds) {
            game.setTile(new Tile(tileId, Integer.toString(position++)));
        }
        return game;
    }
}
