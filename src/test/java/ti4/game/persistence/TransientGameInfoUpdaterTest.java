package ti4.game.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class TransientGameInfoUpdaterTest extends BaseTi4Test {

    private Game game;
    private Player holder;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("transient-game-info-updater-test");
        game.setTile(new Tile("25", "101"));
        holder = game.addPlayer("111", "sol");
        holder.setFaction(game, "sol");
        holder.setColor("blue");
        holder.setUnitsOwned(new HashSet<>(Mapper.getFaction("sol").getUnits()));
    }

    @Test
    void planetsThatAreNotInTheGameAreRemovedFromThePlayers() {
        holder.addPlanet("quann");
        holder.addPlanet("nosuchplanet");

        TransientGameInfoUpdater.removePlanetsMissingFromTheGame(game);

        assertTrue(holder.getPlanets().contains("quann"));
        assertFalse(holder.getPlanets().contains("nosuchplanet"));
    }

    // Loading runs on every read, including the copy each queued render takes, so it must leave the repair to the
    // save; otherwise an ended game nobody saves logs the same removal on every load.
    @Test
    void loadingAGameKeepsPlanetsThatAreNotInTheGame() throws Exception {
        holder.addPlanet("nosuchplanet");
        StringWriter saved = new StringWriter();
        GameSaveService.writeGame(game, saved);

        Game loaded = GameLoadService.readGame(saved.toString().lines().toList());

        assertTrue(loaded.getPlayer("111").getPlanets().contains("nosuchplanet"));
    }
}
