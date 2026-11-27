package ti4.image.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.image.map.TileCatalogueImage.Group;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.testUtils.BaseTi4Test;

class TileCatalogueImageTest extends BaseTi4Test {

    @Test
    void groupsTilesByType() {
        assertThat(group("st30")).contains(Group.ANOMALIES);
        assertThat(group("46")).contains(Group.EMPTY_SYSTEMS);
        assertThat(group("01")).contains(Group.HOME_SYSTEMS);
        // Creuss Gate has no homeworld planet; it counts as a home because Creuss lists it and it has a green back.
        assertThat(group("17")).contains(Group.HOME_SYSTEMS);
        // Sem-Lore is some homebrew factions' home, but it is an ordinary blue tile.
        assertThat(group("62")).contains(Group.ONE_PLANET);
        assertThat(group("25")).contains(Group.WORMHOLES);
        assertThat(group("65")).contains(Group.LEGENDARY);
        assertThat(group("fracture1")).contains(Group.FRACTURE_PLANETS);
        assertThat(group("83a")).contains(Group.HYPERLANES);
        assertThat(group("red5")).contains(Group.BLANKS_AND_MARKERS);
    }

    @Test
    void leavesOutRotationsOtherDraftColoursAndObsoleteEronousTiles() {
        assertThat(group("83a60")).isEmpty();
        assertThat(group("blue5")).isEmpty();
        assertThat(group("er01")).isEmpty();
        assertThat(group("er116b")).isEmpty();
    }

    @Test
    void everyCataloguedTileGetsItsOwnPositionWithinTheSegmentCap() {
        Set<String> factionHomes = TileCatalogueImage.factionHomeSystems();
        Map<Group, List<TileModel>> groups = new EnumMap<>(Group.class);
        List<TileModel> tiles = TileCatalogueImage.catalogued(TileHelper.getAllTileModels());
        for (TileModel tile : tiles) {
            TileCatalogueImage.groupOf(tile, factionHomes)
                    .ifPresent(group -> groups.computeIfAbsent(group, key -> new ArrayList<>())
                            .add(tile));
        }
        Game game = new Game();
        game.newGameSetup();
        game.setFowMode(true);

        Map<String, String> idByPosition = TileCatalogueImage.place(game, groups);

        assertThat(idByPosition).hasSameSizeAs(tiles);
        assertThat(MapSegment.stored(game)).hasSize(groups.size()).hasSizeLessThanOrEqualTo(MapSegment.MAX_SEGMENTS);
    }

    @Test
    void sortsNumbersByValue() {
        assertThat(TileCatalogueImage.naturalOrder("fracture2", "fracture10")).isNegative();
        assertThat(TileCatalogueImage.naturalOrder("st01", "st10")).isNegative();
    }

    @Test
    void rendersASmallSet() {
        List<TileModel> tiles = List.of("st01", "st10", "st20", "st30", "fracture1").stream()
                .map(TileHelper::getTileById)
                .toList();

        BufferedImage image = TileCatalogueImage.render(tiles);

        assertThat(image.getWidth()).isPositive();
        assertThat(image.getHeight()).isPositive();
    }

    private static Optional<Group> group(String tileId) {
        return TileCatalogueImage.groupOf(TileHelper.getTileById(tileId), TileCatalogueImage.factionHomeSystems());
    }
}
