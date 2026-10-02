package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class GMServiceTest extends BaseTi4Test {

    /**
     * The activity-log helpers used by FoW lore dispatch must early-return for non-FoW games. A bare
     * game has no guild, so if the {@code isFowMode()} guard were dropped these would NPE while
     * resolving the GM channel — asserting they don't throw verifies the guard. (The FoW-positive
     * dispatch in {@code LoreService.showLore} is JDA/thread-coupled and not unit-tested here.)
     */
    @Test
    void activityThreadHelpersAreNoOpOutsideFow() {
        Game game = new Game();
        assertDoesNotThrow(() -> GMService.postToActivityThread(game, "should be ignored outside FoW"));
        assertDoesNotThrow(() -> GMService.refreshMapInActivityThread(game));
    }
}
