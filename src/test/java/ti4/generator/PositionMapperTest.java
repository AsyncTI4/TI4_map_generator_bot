package ti4.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PositionMapperTest {
    // @Test
    // void testGetAdjacentTilePositionsNew() {
    //     assertEquals(List.of("101", "102", "103", "104", "105", "106"), PositionMapper.getAdjacentTilePositionsNew("0"));
    //     assertEquals(List.of("101", "102", "103", "104", "105", "106"), PositionMapper.getAdjacentTilePositionsNew("000"));
    //     assertEquals(List.of("401", "402", "302", "201", "318", "424"), PositionMapper.getAdjacentTilePositionsNew("301"));
    //     assertEquals(List.of("402", "403", "303", "202", "201", "301"), PositionMapper.getAdjacentTilePositionsNew("302"));
    //     assertEquals(List.of("403", "404", "304", "203", "202", "302"), PositionMapper.getAdjacentTilePositionsNew("303"));
    //     assertEquals(List.of("404", "405", "406", "305", "203", "303"), PositionMapper.getAdjacentTilePositionsNew("304"));
    //     assertEquals(List.of("304", "406", "407", "306", "204", "203"), PositionMapper.getAdjacentTilePositionsNew("305"));
    //     assertEquals(List.of("305", "407", "408", "307", "205", "204"), PositionMapper.getAdjacentTilePositionsNew("306"));
    //     assertEquals(List.of("306", "408", "409", "410", "308", "205"), PositionMapper.getAdjacentTilePositionsNew("307"));
    //     assertEquals(List.of("205", "307", "410", "411", "309", "206"), PositionMapper.getAdjacentTilePositionsNew("308"));
    //     assertEquals(List.of("206", "308", "411", "412", "310", "207"), PositionMapper.getAdjacentTilePositionsNew("309"));
    //     assertEquals(List.of("207", "309", "412", "413", "414", "311"), PositionMapper.getAdjacentTilePositionsNew("310"));
    //     assertEquals(List.of("208", "207", "310", "414", "415", "312"), PositionMapper.getAdjacentTilePositionsNew("311"));
    //     assertEquals(List.of("209", "208", "311", "415", "416", "313"), PositionMapper.getAdjacentTilePositionsNew("312"));
    //     assertEquals(List.of("314", "209", "312", "416", "417", "418"), PositionMapper.getAdjacentTilePositionsNew("313"));
    //     assertEquals(List.of("315", "210", "209", "313", "418", "419"), PositionMapper.getAdjacentTilePositionsNew("314"));
    //     assertEquals(List.of("316", "211", "210", "314", "419", "420"), PositionMapper.getAdjacentTilePositionsNew("315"));
    //     assertEquals(List.of("422", "317", "211", "315", "420", "421"), PositionMapper.getAdjacentTilePositionsNew("316"));
    //     assertEquals(List.of("423", "318", "212", "211", "316", "422"), PositionMapper.getAdjacentTilePositionsNew("317"));
    //     assertEquals(List.of("424", "301", "201", "212", "317", "423"), PositionMapper.getAdjacentTilePositionsNew("318"));
    // }

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
    void testGetCornerPositionOfHexRing() {
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
