package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ti4.service.game.RematchService;
import ti4.testUtils.BaseTi4Test;

class MiscHelperTest extends BaseTi4Test {
    @Test
    void testNextRematchId() {
        assertEquals("b", StringHelper.nextId("a"));
        assertEquals("o", StringHelper.nextId("n"));
        assertEquals("aa", StringHelper.nextId("z"));
        assertEquals("ab", StringHelper.nextId("aa"));
        assertEquals("ay", StringHelper.nextId("ax"));
        assertEquals("ba", StringHelper.nextId("az"));
        assertEquals("aaa", StringHelper.nextId("zz"));
        assertEquals("baa", StringHelper.nextId("azz"));
        assertEquals("bba", StringHelper.nextId("baz"));
        assertEquals("caa", StringHelper.nextId("bzz"));
    }

    @Test
    void testNextRematchGameName() {
        assertEquals("island4b", RematchService.newGameName("island4"));
        assertEquals("island4o", RematchService.newGameName("island4n"));
        assertEquals("pbd1234b", RematchService.newGameName("pbd1234"));
        assertEquals("pbd1234b", RematchService.newGameName("pbd1234a"));
        assertEquals("pbd1234aa", RematchService.newGameName("pbd1234z"));
        assertEquals("asdf123b", RematchService.newGameName("asdf123"));
        assertEquals("asdf123aa", RematchService.newGameName("asdf123z"));
    }
}
