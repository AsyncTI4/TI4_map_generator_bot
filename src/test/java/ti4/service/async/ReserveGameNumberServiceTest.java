package ti4.service.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ReserveGameNumberServiceTest {

    @Test
    void skipsEveryReservedNumberInARow() {
        Set<String> reserved = Set.of("fow101", "fow102", "fow104");

        assertThat(ReserveGameNumberService.firstUnreservedNumber("fow", 101, reserved::contains))
                .isEqualTo(103);
    }

    @Test
    void anUnreservedStartIsReturnedAsIs() {
        assertThat(ReserveGameNumberService.firstUnreservedNumber("fow", 101, Set.of("fow100")::contains))
                .isEqualTo(101);
    }

    // A reserved pbd number must not block the same number for fog games, and the other way round.
    @Test
    void reservationsOnlyApplyToTheirOwnPrefix() {
        assertThat(ReserveGameNumberService.firstUnreservedNumber("fow", 101, Set.of("pbd101")::contains))
                .isEqualTo(101);
    }
}
