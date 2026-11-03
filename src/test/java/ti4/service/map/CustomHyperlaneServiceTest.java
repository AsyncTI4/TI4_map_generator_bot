package ti4.service.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Pure string-math utilities on hyperlane connection matrices. rotateMatrix60 moved here from
 * HyperlaneTileGenerator (where it was private) and is now shared with the lore effect system,
 * so it needs direct coverage.
 */
class CustomHyperlaneServiceTest {

    private static final Pattern PATTERN = Pattern.compile("1");

    private static String matrixWithOneConnection(int row, int col) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(";");
            for (int j = 0; j < 6; j++) {
                if (j > 0) sb.append(",");
                sb.append(i == row && j == col ? 1 : 0);
            }
        }
        return sb.toString();
    }

    @Test
    void rotationMovesEachCellDiagonallyByOne() {
        // [i][j] -> [(i+1)%6][(j+1)%6]
        assertEquals(
                matrixWithOneConnection(1, 2), CustomHyperlaneService.rotateMatrix60(matrixWithOneConnection(0, 1)));
        // wraps around at the edge
        assertEquals(
                matrixWithOneConnection(0, 0), CustomHyperlaneService.rotateMatrix60(matrixWithOneConnection(5, 5)));
    }

    @Test
    void sixRotationsAreTheIdentity() {
        String matrix = matrixWithOneConnection(2, 4);
        String current = matrix;
        for (int i = 0; i < 6; i++) {
            current = CustomHyperlaneService.rotateMatrix60(current);
        }
        assertEquals(matrix, current);
    }

    @Test
    void rotationIsNullSafe() {
        assertNull(CustomHyperlaneService.rotateMatrix60(null));
    }

    @Test
    void encodeDecodeRoundTrips() {
        String matrix = matrixWithOneConnection(0, 3);
        String encoded = CustomHyperlaneService.encodeMatrix(matrix);
        assertEquals(9, encoded.length(), "encoded form is 9 hex chars (36 bits)");
        assertEquals(matrix, CustomHyperlaneService.decodeMatrix(encoded));
    }

    @Test
    void connectionMatrixValidation() {
        String oneWay = oneWay(matrixWithOneConnection(0, 3));
        assertTrue(CustomHyperlaneService.isValidConnectionMatrix(matrixWithOneConnection(1, 4)));
        assertTrue(CustomHyperlaneService.isValidConnectionMatrix(oneWay), "typed form allows 2");
        assertFalse(CustomHyperlaneService.isValidConnectionMatrix("0,0,0;0,0,0"), "wrong dimensions");
        assertFalse(CustomHyperlaneService.isValidConnectionMatrix(
                matrixWithOneConnection(0, 0).replace("1", "3")));
        // a 2 reaching storage would make encodeMatrix throw on the next save
        assertFalse(CustomHyperlaneService.isValidStoredMatrix(oneWay));
        assertTrue(CustomHyperlaneService.isValidStoredMatrix(matrixWithOneConnection(0, 3)));
    }

    @Test
    void typedOneMirrorsAndTypedTwoStaysOneWay() {
        assertEquals(
                CustomHyperlaneService.toStoredMatrix(matrixWithOneConnection(3, 0)),
                CustomHyperlaneService.toStoredMatrix(matrixWithOneConnection(0, 3)));
        assertEquals(
                matrixWithOneConnection(0, 3),
                CustomHyperlaneService.toStoredMatrix(oneWay(matrixWithOneConnection(0, 3))));
    }

    @Test
    void typedFormRoundTripsThroughStorage() {
        String typed = CustomHyperlaneService.toTypedMatrix(
                CustomHyperlaneService.toStoredMatrix(oneWay(matrixWithOneConnection(5, 1))));
        assertEquals(oneWay(matrixWithOneConnection(5, 1)), typed);

        String twoWay = CustomHyperlaneService.toStoredMatrix(matrixWithOneConnection(1, 4));
        assertEquals(twoWay, CustomHyperlaneService.toStoredMatrix(CustomHyperlaneService.toTypedMatrix(twoWay)));
    }

    @Test
    void rotationKeepsTheDirectionOfAOneWayLane() {
        assertEquals(
                matrixWithOneConnection(1, 4), CustomHyperlaneService.rotateMatrix60(matrixWithOneConnection(0, 3)));
        assertTrue(CustomHyperlaneService.hasOneWayLanes(
                CustomHyperlaneService.rotateMatrix60(matrixWithOneConnection(0, 3))));
    }

    @Test
    void describesOneWayLanesBySide() {
        assertEquals(List.of("N → S"), CustomHyperlaneService.describeOneWayLanes(matrixWithOneConnection(0, 3)));
        assertEquals(
                List.of(),
                CustomHyperlaneService.describeOneWayLanes(
                        CustomHyperlaneService.toStoredMatrix(matrixWithOneConnection(0, 3))));
    }

    // A seen two-way lane keeps both cells, so it is never drawn as one-way; unseen lanes drop out.
    @Test
    void onlyLanesKeepsTheSeenLanesWithTheirDirection() {
        String stored = CustomHyperlaneService.toStoredMatrix(
                "0,0,0,1,0,0;0,0,0,0,2,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        String seen = CustomHyperlaneService.onlyLanes(stored, "000", Set.of("000:0:3"));
        assertEquals(CustomHyperlaneService.toStoredMatrix(matrixWithOneConnection(0, 3)), seen);
        assertEquals(
                matrixWithOneConnection(1, 4),
                CustomHyperlaneService.onlyLanes(stored, "000", Set.of("000:1:4", "101:0:3")));
        assertFalse(CustomHyperlaneService.onlyLanes(stored, "000", Set.of()).contains("1"));
    }

    // On a roundabout you can always drive on to the next exit, so every side you can enter reaches every side you
    // can leave: a one-way loop ends up two-way, while "enter at N, leave at SE or SW" stays one-way.
    @Test
    void roundaboutsConnectEveryEntryToEveryExit() {
        String loop = "1,0,2,0,0,0;0,0,0,0,0,0;0,0,1,0,2,0;0,0,0,0,0,0;2,0,0,0,1,0;0,0,0,0,0,0";
        String allWays = "1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0";
        assertEquals(allWays, CustomHyperlaneService.toStoredMatrix(loop));

        String fromNorth = "1,0,2,0,2,0;0,0,0,0,0,0;0,0,1,0,0,0;0,0,0,0,0,0;0,0,0,0,1,0;0,0,0,0,0,0";
        assertEquals(
                List.of("N → SE", "N → SW"),
                CustomHyperlaneService.describeOneWayLanes(CustomHyperlaneService.toStoredMatrix(fromNorth)));
    }

    // A 2 whose mirror cell is also set is saved as two-way; the save message warns about it.
    @Test
    void describesOneWayLanesTypedInBothDirections() {
        String bothWays = "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
        assertEquals(List.of("N ↔ S"), CustomHyperlaneService.describeContradictions(bothWays));
        assertEquals(List.of(), CustomHyperlaneService.describeContradictions(oneWay(matrixWithOneConnection(0, 3))));
        assertEquals(List.of(), CustomHyperlaneService.describeContradictions(matrixWithOneConnection(0, 3)));
    }

    private static String oneWay(String matrix) {
        return PATTERN.matcher(matrix).replaceFirst("2");
    }
}
