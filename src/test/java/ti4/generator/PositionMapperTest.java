package ti4.generator;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PositionMapperTest extends BaseTi4Test {
    @Test
    public void testIsCornerOfHexRing() {
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
    public void testGetSideNumberOfHexRing() {
        assertSame(1, PositionMapper.getRingSideNumberOfTileID("201"));
        assertSame(1, PositionMapper.getRingSideNumberOfTileID("202"));
        assertSame(2, PositionMapper.getRingSideNumberOfTileID("203"));
        assertSame(3, PositionMapper.getRingSideNumberOfTileID("205"));
        assertSame(4, PositionMapper.getRingSideNumberOfTileID("207"));
        assertSame(5, PositionMapper.getRingSideNumberOfTileID("209"));
        assertSame(6, PositionMapper.getRingSideNumberOfTileID("211"));
        assertSame(6, PositionMapper.getRingSideNumberOfTileID("212"));
        assertSame(1, PositionMapper.getRingSideNumberOfTileID("701"));
        assertSame(2, PositionMapper.getRingSideNumberOfTileID("708"));
        assertSame(2, PositionMapper.getRingSideNumberOfTileID("709"));
        assertSame(2, PositionMapper.getRingSideNumberOfTileID("710"));
        assertSame(3, PositionMapper.getRingSideNumberOfTileID("715"));
        assertSame(4, PositionMapper.getRingSideNumberOfTileID("722"));
        assertSame(5, PositionMapper.getRingSideNumberOfTileID("729"));
        assertSame(5, PositionMapper.getRingSideNumberOfTileID("735"));
        assertSame(6, PositionMapper.getRingSideNumberOfTileID("736"));
        assertSame(6, PositionMapper.getRingSideNumberOfTileID("737"));
        assertSame(6, PositionMapper.getRingSideNumberOfTileID("742"));
    }

    @Test
    public void testGetCornerPositionOfHexRing() {
        assertEquals("201", PositionMapper.getTileIDAtCornerPositionOfRing(2, 1));
        assertEquals("203", PositionMapper.getTileIDAtCornerPositionOfRing(2, 2));
        assertEquals("205", PositionMapper.getTileIDAtCornerPositionOfRing(2, 3));
        assertEquals("207", PositionMapper.getTileIDAtCornerPositionOfRing(2, 4));
        assertEquals("209", PositionMapper.getTileIDAtCornerPositionOfRing(2, 5));
        assertEquals("211", PositionMapper.getTileIDAtCornerPositionOfRing(2, 6));
        assertEquals("701", PositionMapper.getTileIDAtCornerPositionOfRing(7, 1));
        assertEquals("708", PositionMapper.getTileIDAtCornerPositionOfRing(7, 2));
        assertEquals("715", PositionMapper.getTileIDAtCornerPositionOfRing(7, 3));
        assertEquals("722", PositionMapper.getTileIDAtCornerPositionOfRing(7, 4));
        assertEquals("729", PositionMapper.getTileIDAtCornerPositionOfRing(7, 5));
        assertEquals("736", PositionMapper.getTileIDAtCornerPositionOfRing(7, 6));
    }
}
