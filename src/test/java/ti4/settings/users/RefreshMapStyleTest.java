package ti4.settings.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefreshMapStyleTest {

    // SPLIT must post in channel in every game, not only in Fog of War games.
    @Test
    void onlySplitPostsInChannel() {
        assertTrue(RefreshMapStyle.SPLIT.postsInChannel());
        assertFalse(RefreshMapStyle.SPLIT_PRIVATE.postsInChannel());
        assertFalse(RefreshMapStyle.COMBINED.postsInChannel());
    }

    @Test
    void bothSplitStylesOfferMapParts() {
        assertTrue(RefreshMapStyle.SPLIT.isSplit());
        assertTrue(RefreshMapStyle.SPLIT_PRIVATE.isSplit());
        assertFalse(RefreshMapStyle.COMBINED.isSplit());
    }

    // Stored user settings keep the old value strings, so they must still resolve after relabelling.
    @Test
    void storedValuesStillResolve() {
        assertEquals(Optional.of(RefreshMapStyle.SPLIT), RefreshMapStyle.fromValue("split"));
        assertEquals(Optional.of(RefreshMapStyle.SPLIT_PRIVATE), RefreshMapStyle.fromValue("split_private"));
        assertEquals(Optional.of(RefreshMapStyle.COMBINED), RefreshMapStyle.fromValue("combined"));
    }
}
