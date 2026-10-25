package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import ti4.model.ColorModel;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class AnonymousCommsThreadNameTest extends BaseTi4Test {

    // Sidebars cut long names, so the partner's color leads; "-private" stays last for the fog checks
    // (the tile renderer, /game info, spin) that look at the thread's own name.
    @Test
    void partnerThreadLeadsWithHueAndColor() {
        assertEquals("🔴 Red-comms-private", AnonymousCommsService.partnerThreadName("red"));
        assertEquals("🔴🖤 Splitred-comms-private", AnonymousCommsService.partnerThreadName("splitred"));
        assertEquals("📨 Inbox-comms-private", AnonymousCommsService.inboxThreadName());
    }

    // Threads created before the rename keep the old name until the bot next touches them.
    @Test
    void parsesOldAndNewNames() {
        assertEquals("red", AnonymousCommsService.threadTarget("fow123-comms-red-private"));
        assertEquals("inbox", AnonymousCommsService.threadTarget("fow123-comms-inbox-private"));
        assertEquals("red", AnonymousCommsService.threadTarget("🔴 Red-comms-private"));
        assertEquals("splitred", AnonymousCommsService.threadTarget("🔴 Splitred-comms-private"));
        assertEquals("inbox", AnonymousCommsService.threadTarget("📨 Inbox-comms-private"));
        assertEquals("pink", AnonymousCommsService.threadTarget("🩷 Pink-comms-private"));
    }

    @Test
    void namesItBuildsParseBack() {
        for (ColorModel color : Mapper.getColors()) {
            String name = AnonymousCommsService.partnerThreadName(color.getName());
            assertEquals(color.getName(), AnonymousCommsService.threadTarget(name), name);
        }
        assertEquals("inbox", AnonymousCommsService.threadTarget(AnonymousCommsService.inboxThreadName()));
    }

    @Test
    void ignoresOtherPrivateThreads() {
        assertNull(AnonymousCommsService.threadTarget("fow123-red-private"));
        assertNull(AnonymousCommsService.threadTarget("fow123-round-2-system-305-turn-3-red-vs-blue-private"));
        assertNull(AnonymousCommsService.threadTarget("🔴 Red-comms"));
        assertFalse(AnonymousCommsService.isCommsThreadName("Cards Info-fow123-red-private"));
    }
}
