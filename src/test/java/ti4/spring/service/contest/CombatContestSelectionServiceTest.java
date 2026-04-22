package ti4.spring.service.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
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

    @Test
    void loadPersistedSettingsUsesCurrentMinimumSampleCountConstant() {
        CombatContestSampleRepository sampleRepository = mock(CombatContestSampleRepository.class);
        CombatContestSelectionConfigRepository configRepository = mock(CombatContestSelectionConfigRepository.class);
        CombatContestSelectionService selectionService =
                new CombatContestSelectionService(sampleRepository, configRepository);
        CombatContestSelectionConfigEntity entity = new CombatContestSelectionConfigEntity();
        entity.setId(1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setSelectionMode("WARMUP");
        entity.setLookbackMinutes(60);
        entity.setWindowSampleCount(15);
        entity.setTargetPostsPerHour(1.0);
        entity.setTargetSelectionFraction(0.15);
        entity.setScoreCutoff(0.78);
        entity.setStrengthScale(28.0);
        entity.setHpScale(10.0);
        entity.setWeakerStrengthWeight(0.30);
        entity.setWeakerHpWeight(0.20);
        entity.setFairnessWeight(0.50);
        entity.setCooldownMinutes(60);
        entity.setMinimumWeakerStrength(8.0);
        entity.setMinimumSampleCount(24);
        when(configRepository.findById(1)).thenReturn(Optional.of(entity));

        selectionService.loadPersistedSettings();

        assertEquals(8, selectionService.getCurrentSettings().minimumSampleCount());
    }
}
