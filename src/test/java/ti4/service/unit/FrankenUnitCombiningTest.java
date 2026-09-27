package ti4.service.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.settingsFramework.menus.FrankenSettings;
import ti4.json.JsonMapperManager;
import ti4.model.UnitModel;
import ti4.service.franken.FrankenUnitService;
import ti4.service.info.UnitInfoService;
import ti4.testUtils.BaseTi4Test;

class FrankenUnitCombiningTest extends BaseTi4Test {

    @Test
    void persistsDuplicateUnitCombiningSetting() throws Exception {
        FrankenSettings settings = new FrankenSettings(new Game(), null);
        settings.getCombineDuplicateUnitTypes().setVal(true);

        FrankenSettings restored =
                new FrankenSettings(new Game(), JsonMapperManager.basic().readTree(settings.json()));

        assertTrue(restored.getCombineDuplicateUnitTypes().isVal());
    }

    @Test
    void combinesDuplicateAsyncUnitsOnlyWhenEnabledForFranken() {
        Game game = new Game();
        Player player = game.addPlayer("user", "user");
        player.setFaction(game, "franken12");
        player.setColor("red");
        player.setUnitsOwned(new LinkedHashSet<>(List.of("cruiser", "cruiser2")));

        assertEquals(2, player.getUnitsByAsyncID("ca").size());

        game.setStoredValue(FrankenUnitService.COMBINE_DUPLICATE_UNIT_TYPES, "true");

        List<UnitModel> cruisers = player.getUnitsByAsyncID("ca");
        assertEquals(1, cruisers.size());
        assertEquals("Cruiser I / Cruiser II", cruisers.getFirst().getName());
        assertEquals(3, cruisers.getFirst().getMoveValue());
        assertEquals(6, cruisers.getFirst().getCombatHitsOn());
        assertTrue(player.ownsUnit("cruiser"));
        assertTrue(player.ownsUnit("cruiser2"));
    }

    @Test
    void doesNotCombineDuplicateAsyncUnitsInTwilightsFall() {
        Game game = new Game();
        game.setTwilightsFallMode(true);
        Player player = game.addPlayer("user", "user");
        player.setFaction(game, "franken12");
        player.setColor("red");
        player.setUnitsOwned(new LinkedHashSet<>(List.of("cruiser", "cruiser2")));
        game.setStoredValue(FrankenUnitService.COMBINE_DUPLICATE_UNIT_TYPES, "true");

        assertEquals(2, player.getUnitsByAsyncID("ca").size());
    }

    @Test
    void doesNotCombineDuplicateAsyncUnitsOutsideFranken() {
        Game game = new Game();
        Player player = game.addPlayer("user", "user");
        player.setFaction(game, "arborec");
        player.setColor("red");
        player.setUnitsOwned(new LinkedHashSet<>(List.of("cruiser", "cruiser2")));
        game.setStoredValue(FrankenUnitService.COMBINE_DUPLICATE_UNIT_TYPES, "true");

        assertEquals(2, player.getUnitsByAsyncID("ca").size());
        assertEquals(2, UnitInfoService.getUnitMessageEmbeds(player, true).size());
    }

    @Test
    void researchingOneMatchingUnitUpgradeResearchesAllMatchingUpgradesOnce() {
        Game game = new Game();
        Player player = game.addPlayer("user", "user");
        player.setFaction(game, "franken12");
        player.setColor("red");
        player.setUnitsOwned(new LinkedHashSet<>(List.of("cruiser", "khrask_cruiser", "qhet_cruiser")));
        game.setStoredValue(FrankenUnitService.COMBINE_DUPLICATE_UNIT_TYPES, "true");

        player.addTech("cr2");

        assertTrue(player.hasTech("cr2"));
        assertTrue(player.hasTech("dskhracr"));
        assertTrue(player.hasTech("dsqhetcr"));
        assertTrue(!player.hasTech("absol_cr2"));
        assertTrue(player.ownsUnit("cruiser2"));
        assertTrue(player.ownsUnit("khrask_cruiser2"));
        assertTrue(player.ownsUnit("qhet_cruiser2"));
        assertTrue(!player.ownsUnit("khrask_cruiser"));
        assertTrue(!player.ownsUnit("qhet_cruiser"));
        assertEquals(1, UnitInfoService.getUnitMessageEmbeds(player, true).size());
        assertTrue(player.getUnitsByAsyncID("ca").getFirst().getName().contains("Shattered Sky II"));
        assertTrue(!player.getUnitsByAsyncID("ca").getFirst().getName().contains("Cruiser II"));
        assertTrue(player.getUnitsByAsyncID("ca")
                .getFirst()
                .getAbility()
                .orElse("")
                .contains("move through"));
        assertEquals(1, player.getUnitsByAsyncID("ca").getFirst().getBombardDieCount());
        assertEquals(6, player.getUnitsByAsyncID("ca").getFirst().getBombardHitsOn());
    }
}
