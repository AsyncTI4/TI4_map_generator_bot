package ti4.discord.interactions.commands.lazax;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.service.CombatReplayHouseFavorService;

class LazaxGrantFavorTest {

    @Test
    void grantFavorMessageShowsPositiveAdjustmentAndTotalFavor() {
        String message = LazaxGrantFavor.grantFavorMessage(
                CombatReplayHouse.MENTAK, 17, new CombatReplayHouseFavorService.FavorLedger(42, 5, 37));

        assertTrue(message.contains("Mentak Delegation receives `+17` Favor."));
        assertTrue(message.contains("**Total Favor:** `37`"));
    }

    @Test
    void grantFavorMessageShowsNegativeAdjustmentAndTotalFavor() {
        String message = LazaxGrantFavor.grantFavorMessage(
                CombatReplayHouse.MENTAK, -17, new CombatReplayHouseFavorService.FavorLedger(8, 5, 3));

        assertTrue(message.contains("Mentak Delegation loses `-17` Favor."));
        assertTrue(message.contains("**Total Favor:** `3`"));
    }
}
