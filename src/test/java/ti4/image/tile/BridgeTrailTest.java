package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.service.map.BridgeMatrix;
import ti4.service.map.CustomHyperlaneService;

class BridgeTrailTest {

    private static final Path2D NOTHING = new Path2D.Double();

    // Half the ring from N to S passes three rounded corners, each gets an arrow along the flow.
    @Test
    void oneWayThroughTheSystem() {
        BridgeTrail.Trail trail = BridgeTrail.of(lanes(new int[][] {{0, 3}}), NOTHING);
        assertThat(trail.ring())
                .singleElement()
                .satisfies(piece -> assertThat(piece.isOneWay()).isTrue());
        assertThat(trail.stubs()).hasSize(2);
        assertThat(trail.cornerArrows()).hasSize(3);
        List<Point2D> travel = trail.ring().getFirst().inTravelDirection();
        assertThat(travel.getFirst().getY()).isLessThan(travel.getLast().getY());
    }

    @Test
    void twoWayLaneGivesATwoWayPieceWithoutArrows() {
        BridgeTrail.Trail trail = BridgeTrail.of(lanes(new int[][] {{1, 4}, {4, 1}}), NOTHING);
        assertThat(trail.ring())
                .singleElement()
                .satisfies(piece -> assertThat(piece.isOneWay()).isFalse());
        assertThat(trail.cornerArrows()).isEmpty();
    }

    @Test
    void eachLaneGoesRoundTheClearSide() {
        Rectangle2D eastHalf = new Rectangle2D.Double(TileGeometry.TILE_WIDTH / 2.0 + 1, 0, 400, 400);
        BridgeTrail.Trail trail = BridgeTrail.of(lanes(new int[][] {{0, 3}}), eastHalf);
        assertThat(trail.ring().getFirst().points())
                .allSatisfy(point -> assertThat(point.getX()).isLessThanOrEqualTo(TileGeometry.TILE_WIDTH / 2.0 + 1));
    }

    // A planet shape over the west corner: the corner arrow slides along the trail to a clear spot instead of
    // covering the planet's icons or disappearing.
    @Test
    void cornerArrowMovesOffAPlanetShape() {
        Rectangle2D eastHalf = new Rectangle2D.Double(TileGeometry.TILE_WIDTH / 2.0 + 1, 0, 400, 400);
        Ellipse2D westCorner = new Ellipse2D.Double(0, TileGeometry.TILE_HEIGHT / 2.0 - 15, 40, 30);
        Area occluders = new Area(eastHalf);
        occluders.add(new Area(westCorner));
        BridgeTrail.Trail trail = BridgeTrail.of(lanes(new int[][] {{0, 3}}), occluders);
        assertThat(trail.cornerArrows())
                .hasSize(3)
                .noneSatisfy(arrow ->
                        assertThat(westCorner.contains(arrow.x(), arrow.y())).isTrue());
    }

    // N -> S and SE -> NE: N -> S must take the west side, so the two one-way lanes never share the rim (sharing
    // it in opposite directions would draw a two-way piece).
    @Test
    void nestedLanesKeepTheirOwnStretchOfRim() {
        BridgeTrail.Trail trail = BridgeTrail.of(lanes(new int[][] {{0, 3}, {2, 1}}), NOTHING);
        assertThat(trail.ring())
                .hasSize(2)
                .allSatisfy(piece -> assertThat(piece.isOneWay()).isTrue());
        assertThat(trail.ring().stream().flatMap(piece -> piece.points().stream()))
                .anySatisfy(point -> assertThat(point.getX()).isLessThan(TileGeometry.TILE_WIDTH / 4.0));
    }

    // Lanes the matrix doesn't list are never drawn: N -> S alone leaves the trail clear of the NW and SW ends.
    @Test
    void sidesWithoutLanesGetNoStub() {
        BridgeTrail.Trail trail = BridgeTrail.of(lanes(new int[][] {{0, 3}}), NOTHING);
        assertThat(trail.stubs()).hasSize(2);
        assertThat(BridgeTrail.of(BridgeMatrix.NONE, NOTHING).ring()).isEmpty();
    }

    private static BridgeMatrix lanes(int[][] pairs) {
        int[][] grid = new int[6][6];
        for (int[] pair : pairs) grid[pair[0]][pair[1]] = 1;
        return BridgeMatrix.parse(CustomHyperlaneService.formatMatrix(grid));
    }
}
