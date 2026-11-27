package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.map.MapSegment;
import ti4.model.GalaxyNames;
import ti4.service.option.FOWOptionService.FOWOption;
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
            MapSegment.put(game, MapSegment.shape("wedge", Set.of("1210", "1211", "1310")));
            MapSegment.rename(game, "wedge", "sliver");
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
            assertThat(MapSegment.find(reloaded, "sliver").orElseThrow().positions())
                    .containsExactlyInAnyOrder("1210", "1211", "1310");
            assertThat(MapSegment.all(reloaded).stream().map(MapSegment::name)).contains("homeland");
            assertThat(GalaxyNames.name(reloaded, "a")).isEqualTo("frontier");
        }
    }

    // Normal games must save exactly as before: the ghost-hex flag is only written when a player turned it off.
    // Fog view settings belong to the player, not the game: they stay out of the save and follow the player into
    // every game. Old saves that still carry them load without them.
    @Test
    void fogViewSettingsFollowThePlayerNotTheSave() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player player = game.getRealPlayers().getFirst();
            try {
                player.setFogGhostHexes(false);
                player.setFogOverviewBoxes(false);
                player.setFogOverviewReach(0);
                player.setFogFilter("pink");
                GameSaveService.save(game, "test");

                Path saveFile = Storage.getGamePath(harness.getGameName() + Constants.TXT);
                assertThat(Files.readString(saveFile))
                        .doesNotContain(Constants.FOG_GHOST_HEXES)
                        .doesNotContain(Constants.FOG_OVERVIEW_BOXES)
                        .doesNotContain(Constants.FOG_OVERVIEW_REACH)
                        .doesNotContain(Constants.FOG_FILTER + " ");

                Player loaded = harness.load().getPlayer(player.getUserID());
                assertThat(loaded.isFogGhostHexes()).isFalse();
                assertThat(loaded.isFogOverviewBoxes()).isFalse();
                assertThat(loaded.getFogOverviewReach()).isZero();
                assertThat(loaded.getFogFilter()).isEqualTo("pink");
            } finally {
                player.setFogGhostHexes(true);
                player.setFogOverviewBoxes(true);
                player.setFogOverviewReach(2);
                player.setFogFilter("default");
            }
        }
    }

    // Only new fog games get map connections switched on (at creation). A game saved without the option, like
    // every game from before it existed, must stay off after a reload; a GM switching it on must stick.
    @Test
    void mapConnectionsStayOffInOlderGamesUntilAGmTurnsThemOn() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            GameSaveService.save(game, "test");
            assertThat(harness.load().getFowOption(FOWOption.MAP_CONNECTIONS)).isFalse();

            game.setFowOption(FOWOption.MAP_CONNECTIONS, true);
            GameSaveService.save(game, "test");

            assertThat(harness.load().getFowOption(FOWOption.MAP_CONNECTIONS)).isTrue();
        }
    }
}
