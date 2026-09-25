package ti4.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.service.map.FractureService;
import ti4.testUtils.BaseTi4Test;

/**
 * The roll gate means "has ingress placement happened", not "are the tiles on the board". In fog the GM places
 * every token by hand, so the two come apart and the roll must stay available until placement is resolved.
 */
class FractureServiceTest extends BaseTi4Test {

    private static Game game() {
        Game game = new Game();
        game.setName("fracture-service-test");
        return game;
    }

    private static Game gameWithFractureTiles() {
        Game game = game();
        game.setTile(new Tile("fracture1", "frac1"));
        return game;
    }

    @Test
    void anEmptyBoardCanBringTheFractureIntoPlay() {
        assertTrue(FractureService.canFractureEnterPlay(game()));
    }

    @Test
    void tilesOnTheBoardWithNoIngressStillAllowTheRoll() {
        // The reported fog bug: tiles down, the GM never placed ingress, and the roll was suppressed forever
        Game game = gameWithFractureTiles();
        assertTrue(FractureService.isFractureInPlay(game));
        assertFalse(FractureService.anyIngressOnBoard(game));
        assertTrue(FractureService.canFractureEnterPlay(game));
    }

    @Test
    void anIngressTokenOnTheBoardMeansTheFractureIsInPlay() {
        Game game = gameWithFractureTiles();
        Tile mecatol = new Tile("18", "000");
        mecatol.addToken(Constants.TOKEN_INGRESS, Constants.SPACE);
        game.setTile(mecatol);
        assertTrue(FractureService.anyIngressOnBoard(game));
        assertFalse(FractureService.canFractureEnterPlay(game));
    }

    @Test
    void thePlacedFlagSurvivesEveryIngressTokenBeingRemoved() {
        // Nova Seed rebuilds a tile and drops its ingress token; that must not re-open the roll
        Game game = gameWithFractureTiles();
        game.setStoredValue(FractureService.INGRESS_PLACED, "true");
        assertFalse(FractureService.anyIngressOnBoard(game));
        assertFalse(FractureService.canFractureEnterPlay(game));
    }

    @Test
    void disablingTheFractureBeatsEverythingElse() {
        Game game = game();
        game.setNoFractureMode(true);
        assertFalse(FractureService.canFractureEnterPlay(game));
    }

    @Test
    void cosmicConvergenceAlwaysAllowsTheRoll() {
        Game game = gameWithFractureTiles();
        game.setStoredValue(FractureService.INGRESS_PLACED, "true");
        game.setCosmicConvergenceMode(true);
        assertTrue(FractureService.canFractureEnterPlay(game));
    }

    @Test
    void spawningOverAnExistingRegionDoesNotDuplicateTiles() {
        // Callers do `if (spawnFracture(...)) spawnIngressTokens(...)`, so this must report success without
        // re-placing the 7 tiles and the neutral fleet on top of a region that is already down
        Game game = gameWithFractureTiles();
        int tilesBefore = game.getTileMap().size();
        assertTrue(FractureService.spawnFracture(null, game));
        assertEquals(tilesBefore, game.getTileMap().size());
    }
}
