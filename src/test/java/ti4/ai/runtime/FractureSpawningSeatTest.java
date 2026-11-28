package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// -Dai.selfplay.fracture=true brings the Fracture in through a seat drawn from the game's seed, so the ingress that
// seat's breakthrough places changes from game to game while a seed always replays the same seat.
class FractureSpawningSeatTest extends BaseTi4Test {

    // SelfPlayGame seeds the tracker from the game seed, which runs on by one per slot in a batch.
    private static final long FIRST_BATCH_SEED = 7 * 1009L + 9700;

    private static AiTestGame table() {
        AiTestGame test = new AiTestGame();
        test.addSeat("300000000000000013", "hacan", "yellow");
        test.addSeat("300000000000000014", "letnev", "red");
        test.addSeat("300000000000000015", "xxcha", "green");
        return test;
    }

    @Test
    void theSlotsOfABatchSpreadOverTheSeats() {
        AiTestGame test = table();
        Set<String> seats = new HashSet<>();
        for (long slot = 0; slot < 16; slot++) {
            seats.add(FractureTracker.spawningSeat(test.game, FIRST_BATCH_SEED + slot)
                    .getFaction());
        }

        assertThat(seats).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void aSeedAlwaysPicksTheSameSeat() {
        AiTestGame test = table();

        assertThat(FractureTracker.spawningSeat(test.game, 9701))
                .isSameAs(FractureTracker.spawningSeat(test.game, 9701));
    }
}
