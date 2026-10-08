package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.image.GalaxyNames;
import ti4.image.MapSegment;
import ti4.testUtils.BaseTi4Test;

class MapSegmentSaveLoadTest extends BaseTi4Test {

    // Game stored values are saved as "key,value:" pairs. Sector definitions, renamed sectors and galaxy names all
    // contain ':', ',' or ';', so they must come back intact after a save and reload.
    @Test
    void sectorsRenamesAndGalaxyNamesSurviveSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            MapSegment.put(game, new MapSegment("far", "1237", 2));
            MapSegment.put(game, MapSegment.cluster("edge", "1201", 1));
            List<MapSegment> stored = MapSegment.stored(game);
            MapSegment.setAutoSectors(game, true);
            String autoName = MapSegment.all(game).stream()
                    .filter(segment -> segment.kind() == MapSegment.Kind.AUTO)
                    .findFirst()
                    .orElseThrow()
                    .name();
            MapSegment.rename(game, autoName, "homeland");
            GalaxyNames.rename(game, "a", "frontier");
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            assertThat(MapSegment.stored(reloaded)).isEqualTo(stored);
            assertThat(MapSegment.all(reloaded).stream().map(MapSegment::name)).contains("homeland");
            assertThat(GalaxyNames.name(reloaded, "a")).isEqualTo("frontier");
        }
    }
}
