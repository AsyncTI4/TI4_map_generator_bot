package ti4.game.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void planetsThatAreNotInTheGameAreRemovedFromThePlayersWhenTheGameIsUpdated() {
        holder.addPlanet("quann");
        holder.addPlanet("nosuchplanet");

        TransientGameInfoUpdater.update(game);

        assertTrue(holder.getPlanets().contains("quann"));
        assertFalse(holder.getPlanets().contains("nosuchplanet"));
    }
}
