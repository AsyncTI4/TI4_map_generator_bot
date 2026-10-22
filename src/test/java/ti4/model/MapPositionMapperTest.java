package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class MapPositionMapperTest extends BaseTi4Test {
    @Test
    void testIsCornerOfHexRing() {
        assertTrue(MapPositionMapper.isCornerOfHexRing("201"));
        assertTrue(MapPositionMapper.isCornerOfHexRing("203"));
        assertTrue(MapPositionMapper.isCornerOfHexRing("526"));
        assertTrue(MapPositionMapper.isCornerOfHexRing("625"));
        assertTrue(MapPositionMapper.isCornerOfHexRing("722"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("202"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("204"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("603"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("618"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("411"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("415"));
        assertFalse(MapPositionMapper.isCornerOfHexRing("419"));
    }

    @Test
    void testGetSideNumberOfHexRing() {
        assertSame(1, MapPositionMapper.getRingSideNumberOfTileID("201"));
        assertSame(1, MapPositionMapper.getRingSideNumberOfTileID("202"));
        assertSame(2, MapPositionMapper.getRingSideNumberOfTileID("203"));
        assertSame(3, MapPositionMapper.getRingSideNumberOfTileID("205"));
        assertSame(4, MapPositionMapper.getRingSideNumberOfTileID("207"));
        assertSame(5, MapPositionMapper.getRingSideNumberOfTileID("209"));
        assertSame(6, MapPositionMapper.getRingSideNumberOfTileID("211"));
        assertSame(6, MapPositionMapper.getRingSideNumberOfTileID("212"));
        assertSame(1, MapPositionMapper.getRingSideNumberOfTileID("701"));
        assertSame(2, MapPositionMapper.getRingSideNumberOfTileID("708"));
        assertSame(2, MapPositionMapper.getRingSideNumberOfTileID("709"));
        assertSame(2, MapPositionMapper.getRingSideNumberOfTileID("710"));
        assertSame(3, MapPositionMapper.getRingSideNumberOfTileID("715"));
        assertSame(4, MapPositionMapper.getRingSideNumberOfTileID("722"));
        assertSame(5, MapPositionMapper.getRingSideNumberOfTileID("729"));
        assertSame(5, MapPositionMapper.getRingSideNumberOfTileID("735"));
        assertSame(6, MapPositionMapper.getRingSideNumberOfTileID("736"));
        assertSame(6, MapPositionMapper.getRingSideNumberOfTileID("737"));
        assertSame(6, MapPositionMapper.getRingSideNumberOfTileID("742"));
    }

    @Test
    void testGetCornerPositionOfHexRing() {
        assertEquals("201", MapPositionMapper.getTileIDAtCornerPositionOfRing(2, 1));
        assertEquals("203", MapPositionMapper.getTileIDAtCornerPositionOfRing(2, 2));
        assertEquals("205", MapPositionMapper.getTileIDAtCornerPositionOfRing(2, 3));
        assertEquals("207", MapPositionMapper.getTileIDAtCornerPositionOfRing(2, 4));
        assertEquals("209", MapPositionMapper.getTileIDAtCornerPositionOfRing(2, 5));
        assertEquals("211", MapPositionMapper.getTileIDAtCornerPositionOfRing(2, 6));
        assertEquals("701", MapPositionMapper.getTileIDAtCornerPositionOfRing(7, 1));
        assertEquals("708", MapPositionMapper.getTileIDAtCornerPositionOfRing(7, 2));
        assertEquals("715", MapPositionMapper.getTileIDAtCornerPositionOfRing(7, 3));
        assertEquals("722", MapPositionMapper.getTileIDAtCornerPositionOfRing(7, 4));
        assertEquals("729", MapPositionMapper.getTileIDAtCornerPositionOfRing(7, 5));
        assertEquals("736", MapPositionMapper.getTileIDAtCornerPositionOfRing(7, 6));
    }
}
