package ti4.game.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class ReplaceRemovedBaldrickUnitUpgradesMigrationTest extends BaseTi4Test {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("baldrick-migration-test");
        player = game.addPlayer("p1", "sol");
        player.setFaction(game, "sol");
        player.setColor("blue");
    }

    // Player.addTech refuses unknown ids, so the lists are filled directly the way loading a save fills them.
    @Test
    void replacesRemovedTechsInEveryTechList() {
        player.getTechs().addAll(List.of("gd", "baldrick_ff2", "baldrick_ac2"));
        player.getExhaustedTechs().add("baldrick_ff2");
        player.getPurgedTechs().add("baldrick_dn2");
        player.getFactionTechs().add("baldrick_exo2");

        assertTrue(DataMigrationManager.replaceRemovedBaldrickUnitUpgrades_221026_withEnded(game));

        assertEquals(List.of("gd", "ff2", "ac2"), player.getTechs());
        assertEquals(List.of("ff2"), player.getExhaustedTechs());
        assertEquals(List.of("dn2"), player.getPurgedTechs());
        assertEquals(List.of("exo2"), player.getFactionTechs());
        player.getTechs().forEach(tech -> assertNotNull(Mapper.getTech(tech), tech));
    }

    @Test
    void replacesRemovedUnitsOwned() {
        player.setUnitsOwned(new HashSet<>(Set.of("carrier", "baldrick_sol_carrier2", "baldrick_fighter2")));

        assertTrue(DataMigrationManager.replaceRemovedBaldrickUnitUpgrades_221026_withEnded(game));

        assertEquals(Set.of("carrier", "sol_carrier2", "fighter2"), player.getUnitsOwned());
        player.getUnitsOwned().forEach(unit -> assertNotNull(Mapper.getUnit(unit), unit));
    }

    // A seat holding both the removed id and its replacement must not end up with the replacement twice.
    @Test
    void doesNotDuplicateATechAlreadyHeld() {
        player.getTechs().addAll(List.of("ff2", "baldrick_ff2"));

        DataMigrationManager.replaceRemovedBaldrickUnitUpgrades_221026_withEnded(game);

        assertEquals(List.of("ff2"), player.getTechs());
    }

    @Test
    void reportsNoChangeForGamesWithoutRemovedIds() {
        player.getTechs().add("ff2");

        assertFalse(DataMigrationManager.replaceRemovedBaldrickUnitUpgrades_221026_withEnded(game));
    }
}
