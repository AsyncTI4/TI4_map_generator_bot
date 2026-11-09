package ti4.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;

class HyperlaneBridgeEndsTest {

    // The system's side 0 touches the neighbour's side 3; matrix row = side entered, column = side left.
    @Test
    void laneLeavingTheNeighbourTowardsTheSystemIsAnEntry() {
        int[][] lanes = new int[6][6];
        lanes[0][3] = 1;
        assertThat(HyperlaneBridgeEnds.endOn(0, lanes)).contains(new BridgeEnd(0, true, false));
    }

    @Test
    void laneStartingAtTheSharedEdgeIsAnExit() {
        int[][] lanes = new int[6][6];
        lanes[3][0] = 1;
        assertThat(HyperlaneBridgeEnds.endOn(0, lanes)).contains(new BridgeEnd(0, false, true));
    }

    @Test
    void twoWayLaneIsBothAndALaneElsewhereIsNothing() {
        int[][] lanes = new int[6][6];
        lanes[0][3] = 1;
        lanes[3][0] = 1;
        assertThat(HyperlaneBridgeEnds.endOn(0, lanes)).contains(new BridgeEnd(0, true, true));
        assertThat(HyperlaneBridgeEnds.endOn(1, lanes)).isEmpty();
    }

    // A roundabout's diagonal marks its connectors; only lanes to other sides count as traffic.
    @Test
    void roundaboutConnectorCountsByItsLanesToOtherSides() {
        int[][] lanes = new int[6][6];
        lanes[0][0] = lanes[3][3] = 1;
        lanes[0][3] = 1;
        assertThat(HyperlaneBridgeEnds.endOn(0, lanes)).contains(new BridgeEnd(0, true, false));
        assertThat(HyperlaneBridgeEnds.endOn(3, lanes)).contains(new BridgeEnd(3, false, true));
    }

    @Test
    void aBridgeNeedsTwoEndsAndListsThemBySide() {
        BridgeEnd south = new BridgeEnd(3, false, true), north = new BridgeEnd(0, true, false);
        assertThat(HyperlaneBridgeEnds.bridgeable(Map.of(3, south))).isEmpty();
        assertThat(HyperlaneBridgeEnds.bridgeable(Map.of(3, south, 0, north))).containsExactly(north, south);
    }
}
