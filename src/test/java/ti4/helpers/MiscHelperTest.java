package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ti4.service.game.RematchService;
import ti4.testUtils.BaseTi4Test;

public class MiscHelperTest extends BaseTi4Test {
    @Test
    void testNextRematchId() {
        assertEquals(StringHelper.nextId("a"), "b");
        assertEquals(StringHelper.nextId("n"), "o");
        assertEquals(StringHelper.nextId("z"), "aa");
        assertEquals(StringHelper.nextId("aa"), "ab");
        assertEquals(StringHelper.nextId("ax"), "ay");
        assertEquals(StringHelper.nextId("az"), "ba");
        assertEquals(StringHelper.nextId("zz"), "aaa");
        assertEquals(StringHelper.nextId("azz"), "baa");
        assertEquals(StringHelper.nextId("baz"), "bba");
        assertEquals(StringHelper.nextId("bzz"), "caa");
    }

    @Test
    void testNextRematchGameName() {
        assertEquals(RematchService.newGameName("island4"), "island4b");
        assertEquals(RematchService.newGameName("island4n"), "island4o");
        assertEquals(RematchService.newGameName("pbd1234"), "pbd1234b");
        assertEquals(RematchService.newGameName("pbd1234a"), "pbd1234b");
        assertEquals(RematchService.newGameName("pbd1234z"), "pbd1234aa");
        assertEquals(RematchService.newGameName("asdf123"), "asdf123b");
        assertEquals(RematchService.newGameName("asdf123z"), "asdf123aa");
    }
}
