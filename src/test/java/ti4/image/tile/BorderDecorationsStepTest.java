package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_PADDING;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.service.fow.FogViewer;
import ti4.service.map.HyperlaneBridges;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class BorderDecorationsStepTest extends BaseTi4Test {

    @Test
    void fullRatNestIsTheWholeImageOnceAndAPartialOneOnlyItsEdges() {
        BufferedImage allSides = renderRatNest(List.of(0, 1, 2, 3, 4, 5));
        BufferedImage oneSide = renderRatNest(List.of(0));

        assertThat(pixels(allSides)).isEqualTo(pixels(centredImage(allSides)));
        assertThat(painted(oneSide)).isPositive().isLessThan(painted(allSides));
        assertThat(painted(renderRatNest(List.of()))).isZero();
    }

    // One-way hyperlane 000 runs N -> S: with "One-way lane hints" on, 104 (where it leads) glows at its north edge,
    // 101 (where it starts) does not, and nothing glows with the option off.
    @Test
    void oneWayLaneHintGlowsOnlyOnTheSystemTheLaneLeadsInto() {
        Game game = gameWithOneWayLaneIntoTile104(true);

        assertThat(painted(extras(game, "104"))).isZero();
        game.setFowOption(FOWOption.ONE_WAY_LANE_HINTS, true);
        assertThat(glowsAtNorthEdge(extras(game, "104"))).isTrue();
        assertThat(painted(extras(game, "101"))).isZero();
    }

    // The option is a fog option: a normal game that has it stored (e.g. converted from fog) draws nothing extra.
    @Test
    void normalGamesNeverGlowAndKeepTheirExtrasTiles() {
        Game game = gameWithOneWayLaneIntoTile104(false);
        game.setFowOption(FOWOption.ONE_WAY_LANE_HINTS, true);

        assertThat(painted(extras(game, "104"))).isZero();
        assertThat(ExtrasStepTiles.positions(game)).isEmpty();
    }

    // Fog safety: a player only gets the hint from a hyperlane they currently see.
    @Test
    void privateViewGlowsOnlyFromASeenHyperlane() {
        Game game = gameWithOneWayLaneIntoTile104(true);
        game.setFowOption(FOWOption.ONE_WAY_LANE_HINTS, true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        game.getTileByPosition("104").setTileFog(viewer, false);
        TileLayers privateLayers =
                TileLayers.forPreparedViewer(game, new FogViewer(game, true, viewer), DisplayType.all);

        game.getTileByPosition("000").setTileFog(viewer, true);
        assertThat(painted(privateLayers.draw(game.getTileByPosition("104"), TileStep.Extras)))
                .isZero();
        game.getTileByPosition("000").setTileFog(viewer, false);
        assertThat(glowsAtNorthEdge(privateLayers.draw(game.getTileByPosition("104"), TileStep.Extras)))
                .isTrue();
    }

    // A spatial tear on the lane's exit edge blocks the route, so the system behind it must not glow; a malformed
    // hyperlane entry must not break the render either.
    @Test
    void noHintAcrossABlockedEdgeOrFromBrokenData() {
        Game game = gameWithOneWayLaneIntoTile104(true);
        game.setFowOption(FOWOption.ONE_WAY_LANE_HINTS, true);
        game.addBorderAnomaly("000", 3, "spatial_tear");
        assertThat(painted(extras(game, "104"))).isZero();

        game.getCustomHyperlaneData().put("000", "broken");
        assertThat(ExtrasStepTiles.positions(game)).containsExactly("000");
        assertThat(painted(extras(game, "104"))).isZero();
    }

    // With bridges on and a lane leaving 104 to the south (207), 104 is bridged: the lane does not end there, so no
    // "a one-way lane leads in here" glow on its north edge.
    @Test
    void noHintWhereABridgeCarriesTheLaneOn() {
        Game game = gameWithOneWayLaneIntoTile104(true);
        game.setFowOption(FOWOption.ONE_WAY_LANE_HINTS, true);
        game.setTile(new Tile("hl", "207"));
        game.getCustomHyperlaneData()
                .put("207", "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        assertThat(glowsAtNorthEdge(extras(game, "104"))).isTrue();

        HyperlaneBridges.set(game, "104", "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        assertThat(painted(extras(game, "104"))).isZero();
    }

    // 104 is bridged S -> SW, so the lane arriving from 207 (south) carries on. The 000 lane arriving from the north
    // isn't in the matrix, so it still ends in 104 and keeps its glow; 207's does not.
    @Test
    void hintStaysWhereALaneEndsInABridgedSystem() {
        Game game = gameWithOneWayLaneIntoTile104(true);
        game.setFowOption(FOWOption.ONE_WAY_LANE_HINTS, true);
        game.setTile(new Tile("hl", "207"));
        game.getCustomHyperlaneData()
                .put("207", "0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        HyperlaneBridges.set(game, "104", "0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,2,0;0,0,0,0,0,0;0,0,0,0,0,0");

        BufferedImage layer = extras(game, "104");
        assertThat(glowsAtNorthEdge(layer)).isTrue();
        assertThat(layer.getRGB(TILE_PADDING + TILE_WIDTH / 2, TILE_PADDING + TILE_HEIGHT - 4) >>> 24)
                .isZero();
    }

    // 000 is bridged N -> S straight into system 104, so their shared edge is lane-only. A fog player who can't see
    // that lane (Hide unseen hyperlanes, no vision along it) gets a Spatial Tear on the edge; once the lanes are
    // shown, or in a public view, the tear goes away.
    @Test
    void laneBlockedBorderShowsATearOnlyToPlayersWhoCantSeeTheLane() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("lane-blocked-test");
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES, true);
        game.setTile(new Tile("19", "000"));
        game.setTile(new Tile("20", "104"));
        HyperlaneBridges.set(game, "000", "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        game.getTileByPosition("000").setTileFog(viewer, false);
        game.getTileByPosition("104").setTileFog(viewer, false);

        assertThat(painted(privateExtras(game, viewer, "000"))).isPositive();
        assertThat(game.getBorderAnomalies()).isEmpty();
        assertThat(painted(extras(game, "000"))).isZero();

        game.setFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES, false);
        assertThat(painted(privateExtras(game, viewer, "000"))).isZero();
    }

    private static BufferedImage privateExtras(Game game, Player viewer, String position) {
        return TileLayers.forPreparedViewer(game, new FogViewer(game, true, viewer), DisplayType.all)
                .draw(game.getTileByPosition(position), TileStep.Extras);
    }

    // Systems 101 (north) and 104 (south) around custom hyperlane 000, whose one lane runs N -> S.
    private static Game gameWithOneWayLaneIntoTile104(boolean fog) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("one-way-hint-test");
        game.setFowMode(fog);
        game.setTile(new Tile("19", "101"));
        game.setTile(new Tile("20", "104"));
        game.setTile(new Tile("hl", "000"));
        game.getCustomHyperlaneData()
                .put("000", "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        return game;
    }

    private static boolean glowsAtNorthEdge(BufferedImage layer) {
        return (layer.getRGB(TILE_PADDING + TILE_WIDTH / 2, TILE_PADDING + 3) >>> 24) > 0;
    }

    private static BufferedImage extras(Game game, String position) {
        return TileLayers.of(game, null, DisplayType.all).draw(game.getTileByPosition(position), TileStep.Extras);
    }

    private static BufferedImage renderRatNest(List<Integer> directions) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("border-decorations-test");
        Tile tile = new Tile("19", "101");
        game.setTile(tile);
        directions.forEach(direction -> game.addBorderAnomaly("101", direction, "ratnest"));
        return TileLayers.of(game, null, DisplayType.all).draw(tile, TileStep.Extras);
    }

    // What a single, unrotated, centred draw of the rat nest image looks like on the same layer.
    private static BufferedImage centredImage(BufferedImage layer) {
        BufferedImage expected = new BufferedImage(layer.getWidth(), layer.getHeight(), layer.getType());
        BufferedImage ratNest = ImageHelper.read(ImageResources.border("ratnest_border.png"));
        Graphics2D g = expected.createGraphics();
        g.drawImage(
                ratNest,
                TILE_PADDING + (TILE_WIDTH - ratNest.getWidth()) / 2,
                TILE_PADDING + (TILE_HEIGHT - ratNest.getHeight()) / 2,
                null);
        g.dispose();
        return expected;
    }

    private static long painted(BufferedImage image) {
        return pixels(image).stream().filter(pixel -> (pixel >>> 24) != 0).count();
    }

    private static List<Integer> pixels(BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                .boxed()
                .toList();
    }
}
