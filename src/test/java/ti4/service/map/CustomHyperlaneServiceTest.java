package ti4.service.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure string-math utilities on hyperlane connection matrices. rotateMatrix60 moved here from
 * HyperlaneTileGenerator (where it was private) and is now shared with the lore effect system,
 * so it needs direct coverage.
 */
class CustomHyperlaneServiceTest {

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
        assertTrue(CustomHyperlaneService.isValidConnectionMatrix(matrixWithOneConnection(1, 4)));
        assertFalse(CustomHyperlaneService.isValidConnectionMatrix("0,0,0;0,0,0"), "wrong dimensions");
        assertFalse(
                CustomHyperlaneService.isValidConnectionMatrix(
                        matrixWithOneConnection(0, 0).replaceFirst("1", "2")),
                "cells must be 0 or 1");
    }
}
