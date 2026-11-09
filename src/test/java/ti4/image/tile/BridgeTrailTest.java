package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;

class BridgeTrailTest {

    private static final Path2D NOTHING = new Path2D.Double();
    private static final BridgeEnd NORTH_IN = new BridgeEnd(0, true, false);
    private static final BridgeEnd SOUTH_OUT = new BridgeEnd(3, false, true);

    // Half the ring from N to S passes three rounded corners, each gets an arrow along the flow.
    @Test
    void oneWayThroughTheSystem() {
        BridgeTrail.Trail trail = BridgeTrail.of(List.of(NORTH_IN, SOUTH_OUT), NOTHING);
        assertThat(trail.ring()).singleElement().satisfies(piece -> {
            assertThat(piece.forward()).isTrue();
            assertThat(piece.backward()).isFalse();
        });
        assertThat(trail.stubs()).hasSize(2);
        assertThat(trail.cornerArrows()).hasSize(3);
        List<Point2D> travel = trail.ring().getFirst().inTravelDirection();
        assertThat(travel.getFirst().getY()).isLessThan(travel.getLast().getY());
    }

    @Test
    void twoWayEndsGiveATwoWayPieceWithoutArrows() {
        BridgeTrail.Trail trail =
                BridgeTrail.of(List.of(new BridgeEnd(1, true, true), new BridgeEnd(4, true, true)), NOTHING);
        assertThat(trail.ring())
                .singleElement()
                .satisfies(piece -> assertThat(piece.isOneWay()).isFalse());
        assertThat(trail.cornerArrows()).isEmpty();
    }

    // Two lanes that only lead in cannot be travelled between, so no ring piece links them.
    @Test
    void entriesOnlyCarryNoTraffic() {
        BridgeTrail.Trail trail = BridgeTrail.of(List.of(NORTH_IN, new BridgeEnd(3, true, false)), NOTHING);
        assertThat(trail.ring()).isEmpty();
        assertThat(trail.stubs()).hasSize(2);
    }

    @Test
    void trailGoesRoundTheClearSide() {
        Rectangle2D eastHalf = new Rectangle2D.Double(TileGeometry.TILE_WIDTH / 2.0 + 1, 0, 400, 400);
        BridgeTrail.Trail trail = BridgeTrail.of(List.of(NORTH_IN, SOUTH_OUT), eastHalf);
        assertThat(trail.ring().getFirst().points())
                .allSatisfy(point -> assertThat(point.getX()).isLessThanOrEqualTo(TileGeometry.TILE_WIDTH / 2.0 + 1));
    }

    // With three ends the ring minus one gap links them all; both pieces carry traffic out of the north lane.
    @Test
    void splitFromNorthToSouthEastAndSouthWest() {
        BridgeTrail.Trail trail = BridgeTrail.of(
                List.of(NORTH_IN, new BridgeEnd(2, false, true), new BridgeEnd(4, false, true)), NOTHING);
        assertThat(trail.ring())
                .hasSize(2)
                .allSatisfy(piece -> assertThat(piece.isOneWay()).isTrue());
    }
}
