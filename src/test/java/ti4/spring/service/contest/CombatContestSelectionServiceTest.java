package ti4.spring.service.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class CombatContestSelectionServiceTest {

    private final CombatContestSelectionService service = new CombatContestSelectionService(
            mock(CombatContestSampleRepository.class), mock(CombatContestSelectionConfigRepository.class));

    @Test
    void fairnessAccountsForStrengthAndHpBalance() {
        CombatContestSelectionService.Evaluation strengthMismatch = service.evaluate(20, 40, 10, 10);
        CombatContestSelectionService.Evaluation hpMismatch = service.evaluate(20, 20, 5, 10);
        CombatContestSelectionService.Evaluation balanced = service.evaluate(20, 20, 10, 10);

        assertEquals(0.75, strengthMismatch.fairnessRatio(), 0.0001);
        assertEquals(0.75, hpMismatch.fairnessRatio(), 0.0001);
        assertEquals(1.0, balanced.fairnessRatio(), 0.0001);
        assertTrue(balanced.contestScore() > strengthMismatch.contestScore());
        assertTrue(balanced.contestScore() > hpMismatch.contestScore());
    }
}
