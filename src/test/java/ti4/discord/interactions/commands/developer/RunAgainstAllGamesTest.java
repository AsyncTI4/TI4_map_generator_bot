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

    private static Game gameWithTiles(String... tileIds) {
        Game game = new Game();
        int position = 101;
        for (String tileId : tileIds) {
            game.setTile(new Tile(tileId, Integer.toString(position++)));
        }
        return game;
    }
}
