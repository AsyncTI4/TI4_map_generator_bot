package ti4.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class PositionMapperTest {
    @Test
    void testGetAdjacentTilePositionsNew() {
        assertEquals(List.of("101", "102", "103", "104", "105", "106"), PositionMapper.getAdjacentTilePositionsNew("0"));
        assertEquals(List.of("101", "102", "103", "104", "105", "106"), PositionMapper.getAdjacentTilePositionsNew("000"));
        assertEquals(List.of("101", "202", "212", "301", "302", "318"), PositionMapper.getAdjacentTilePositionsNew("201")); //corner
        assertEquals(List.of("101", "102", "201", "203", "302", "303"), PositionMapper.getAdjacentTilePositionsNew("202")); //non-corner
        assertEquals(List.of("209", "312", "314", "416", "417", "418"), PositionMapper.getAdjacentTilePositionsNew("313")); //corner
        assertEquals(List.of("210", "211", "314", "316", "419", "420"), PositionMapper.getAdjacentTilePositionsNew("315")); //non-corner
    }

    @Test
    void testIsCornerOfHexRing() {
        assertTrue(PositionMapper.isCornerOfHexRing("201"));
        assertTrue(PositionMapper.isCornerOfHexRing("203"));
        assertTrue(PositionMapper.isCornerOfHexRing("526"));
        assertTrue(PositionMapper.isCornerOfHexRing("625"));
        assertTrue(PositionMapper.isCornerOfHexRing("722"));
        assertFalse(PositionMapper.isCornerOfHexRing("202"));
        assertFalse(PositionMapper.isCornerOfHexRing("204"));
        assertFalse(PositionMapper.isCornerOfHexRing("603"));
        assertFalse(PositionMapper.isCornerOfHexRing("618"));
        assertFalse(PositionMapper.isCornerOfHexRing("411"));
        assertFalse(PositionMapper.isCornerOfHexRing("415"));
        assertFalse(PositionMapper.isCornerOfHexRing("419"));
    }

    @Test
    void testGetSideNumberOfHexRing() {
        assertSame(1, PositionMapper.getSideNumberOfHexRing("201"));
        assertSame(1, PositionMapper.getSideNumberOfHexRing("202"));
        assertSame(2, PositionMapper.getSideNumberOfHexRing("203"));
        assertSame(3, PositionMapper.getSideNumberOfHexRing("205"));
        assertSame(4, PositionMapper.getSideNumberOfHexRing("207"));
        assertSame(5, PositionMapper.getSideNumberOfHexRing("209"));
        assertSame(6, PositionMapper.getSideNumberOfHexRing("211"));
        assertSame(6, PositionMapper.getSideNumberOfHexRing("212"));
        assertSame(1, PositionMapper.getSideNumberOfHexRing("701"));
        assertSame(2, PositionMapper.getSideNumberOfHexRing("708"));
        assertSame(2, PositionMapper.getSideNumberOfHexRing("709"));
        assertSame(2, PositionMapper.getSideNumberOfHexRing("710"));
        assertSame(3, PositionMapper.getSideNumberOfHexRing("715"));
        assertSame(4, PositionMapper.getSideNumberOfHexRing("722"));
        assertSame(5, PositionMapper.getSideNumberOfHexRing("729"));
        assertSame(5, PositionMapper.getSideNumberOfHexRing("735"));
        assertSame(6, PositionMapper.getSideNumberOfHexRing("736"));
        assertSame(6, PositionMapper.getSideNumberOfHexRing("737"));
        assertSame(6, PositionMapper.getSideNumberOfHexRing("742"));
    }

    @Test
    void testGetCornerPositionOfHexRing() {
        assertEquals("201", PositionMapper.getCornerPositionOfHexRing(2, 1));
        assertEquals("203", PositionMapper.getCornerPositionOfHexRing(2, 2));
        assertEquals("205", PositionMapper.getCornerPositionOfHexRing(2, 3));
        assertEquals("207", PositionMapper.getCornerPositionOfHexRing(2, 4));
        assertEquals("209", PositionMapper.getCornerPositionOfHexRing(2, 5));
        assertEquals("211", PositionMapper.getCornerPositionOfHexRing(2, 6));
        assertEquals("701", PositionMapper.getCornerPositionOfHexRing(7, 1));
        assertEquals("708", PositionMapper.getCornerPositionOfHexRing(7, 2));
        assertEquals("715", PositionMapper.getCornerPositionOfHexRing(7, 3));
        assertEquals("722", PositionMapper.getCornerPositionOfHexRing(7, 4));
        assertEquals("729", PositionMapper.getCornerPositionOfHexRing(7, 5));
        assertEquals("736", PositionMapper.getCornerPositionOfHexRing(7, 6));
    }
}
