package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.image.GalaxyNames;
import ti4.image.MapSegment;
import ti4.testUtils.BaseTi4Test;

class MapSegmentSaveLoadTest extends BaseTi4Test {

    // Game stored values are saved as "key,value:" pairs, so segment values must survive ':' and ',' handling.
    @Test
    void namedAndClusterSegmentsSurviveSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            MapSegment.put(game, new MapSegment("home", "000", 2));
            MapSegment.put(game, MapSegment.cluster("north", "101", 1));
            List<MapSegment> before = MapSegment.stored(game);
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            assertThat(MapSegment.stored(reloaded)).isEqualTo(before);
        }
    }

    @Test
    void renamedAutoSectorsKeepTheirNamesAfterSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            MapSegment.setAutoSectors(game, true);
            String autoName = MapSegment.all(game).getFirst().name();
            MapSegment.rename(game, autoName, "homeland");
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            assertThat(MapSegment.all(reloaded).stream().map(MapSegment::name)).contains("homeland");
        }
    }

    @Test
    void galaxyNamesSurviveSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            GalaxyNames.rename(game, "a", "frontier");
            GalaxyNames.rename(game, GalaxyNames.MAIN_ID, "home-galaxy");
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            assertThat(GalaxyNames.name(reloaded, "a")).isEqualTo("frontier");
            assertThat(GalaxyNames.name(reloaded, GalaxyNames.MAIN_ID)).isEqualTo("home-galaxy");
        }
    }
}
