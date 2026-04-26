package ti4.spring.service.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CombatContestSelectionServiceTest {

    private final CombatContestSelectionService service = new CombatContestSelectionService(
            mock(CombatContestSampleRepository.class), mock(CombatContestSelectionConfigRepository.class));

    @Test
    void fairnessAccountsForStrengthHpAndExpectedHitsBalance() {
        CombatContestSelectionService.Evaluation strengthMismatch = service.evaluate(20, 40, 10, 10, 4, 4);
        CombatContestSelectionService.Evaluation hpMismatch = service.evaluate(20, 20, 5, 10, 4, 4);
        CombatContestSelectionService.Evaluation expectedHitsMismatch = service.evaluate(20, 20, 10, 10, 2, 4);
        CombatContestSelectionService.Evaluation balanced = service.evaluate(20, 20, 10, 10, 4, 4);

        assertEquals(5.0 / 6.0, strengthMismatch.fairnessRatio(), 0.0001);
        assertEquals(5.0 / 6.0, hpMismatch.fairnessRatio(), 0.0001);
        assertEquals(5.0 / 6.0, expectedHitsMismatch.fairnessRatio(), 0.0001);
        assertEquals(1.0, balanced.fairnessRatio(), 0.0001);
    }

    @Test
    void eligibilityRequiresFairnessAndCombatSize() {
        CombatContestSampleRepository sampleRepository = mock(CombatContestSampleRepository.class);
        CombatContestSelectionConfigRepository configRepository = mock(CombatContestSelectionConfigRepository.class);
        CombatContestSelectionService selectionService =
                new CombatContestSelectionService(sampleRepository, configRepository);
        when(configRepository.findById(1)).thenReturn(Optional.of(configEntity("DYNAMIC", 14.0, 0.72)));

        selectionService.loadPersistedSettings();

        assertTrue(selectionService.evaluate(20, 20, 10, 10, 4, 4).eligibleUnderCurrentThresholds());
        assertFalse(selectionService.evaluate(13, 13, 10, 10, 4, 4).eligibleUnderCurrentThresholds());
        assertFalse(selectionService.evaluate(20, 50, 10, 10, 2, 8).eligibleUnderCurrentThresholds());
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
        entity.setScoreCutoff(14.0);
        entity.setStrengthScale(14.0);
        entity.setHpScale(0.0);
        entity.setWeakerStrengthWeight(1.0);
        entity.setWeakerHpWeight(0.0);
        entity.setFairnessWeight(0.72);
        entity.setCooldownMinutes(60);
        entity.setMinimumWeakerStrength(0.0);
        entity.setMinimumSampleCount(24);
        when(configRepository.findById(1)).thenReturn(Optional.of(entity));

        selectionService.loadPersistedSettings();

        assertEquals(8, selectionService.getCurrentSettings().minimumSampleCount());
        assertEquals(14.0, selectionService.getCurrentSettings().combatSizeCutoff(), 0.0001);
        assertEquals(0.72, selectionService.getCurrentSettings().fairnessFloor(), 0.0001);
        assertEquals(0.85, selectionService.getCurrentSettings().fairnessPercentile(), 0.0001);
        assertEquals(0.72, selectionService.getCurrentSettings().averageFairness(), 0.0001);
    }

    @Test
    void recomputeKeepsDynamicModeWhenSampleWindowDrops() {
        CombatContestSampleRepository sampleRepository = mock(CombatContestSampleRepository.class);
        CombatContestSelectionConfigRepository configRepository = mock(CombatContestSelectionConfigRepository.class);
        CombatContestSelectionService selectionService =
                new CombatContestSelectionService(sampleRepository, configRepository);
        CombatContestSelectionConfigEntity entity = new CombatContestSelectionConfigEntity();
        entity.setId(1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setSelectionMode("DYNAMIC");
        entity.setLookbackMinutes(60);
        entity.setWindowSampleCount(12);
        entity.setTargetPostsPerHour(1.0);
        entity.setTargetSelectionFraction(0.10);
        entity.setScoreCutoff(16.0);
        entity.setStrengthScale(14.0);
        entity.setHpScale(0.0);
        entity.setWeakerStrengthWeight(1.0);
        entity.setWeakerHpWeight(0.0);
        entity.setFairnessWeight(0.72);
        entity.setCooldownMinutes(60);
        entity.setMinimumWeakerStrength(0.0);
        entity.setMinimumSampleCount(24);
        when(configRepository.findById(1)).thenReturn(Optional.of(entity));
        when(sampleRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(any()))
                .thenReturn(List.of());

        selectionService.loadPersistedSettings();
        CombatContestSelectionService.Settings recomputed = selectionService.recomputeAndPersistSettings();

        assertEquals("DYNAMIC", recomputed.selectionMode());
        assertEquals(0, recomputed.windowSampleCount());
        assertEquals(16.0, recomputed.combatSizeCutoff(), 0.0001);
        assertEquals(0.72, recomputed.fairnessFloor(), 0.0001);
        assertEquals(0.90, recomputed.fairnessPercentile(), 0.0001);
        assertEquals(0.72, recomputed.averageFairness(), 0.0001);
        assertEquals(8, recomputed.minimumSampleCount());
    }

    @Test
    void recomputeUsesFairCombatsToSetCombatSizeCutoff() {
        CombatContestSampleRepository sampleRepository = mock(CombatContestSampleRepository.class);
        CombatContestSelectionConfigRepository configRepository = mock(CombatContestSelectionConfigRepository.class);
        CombatContestSelectionService selectionService =
                new CombatContestSelectionService(sampleRepository, configRepository);
        when(sampleRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(any()))
                .thenReturn(List.of(
                        sample(18, 0.80),
                        sample(22, 0.76),
                        sample(30, 0.90),
                        sample(40, 0.50),
                        sample(50, 0.60),
                        sample(16, 0.74),
                        sample(28, 0.85),
                        sample(14, 0.72)));

        CombatContestSelectionService.Settings recomputed = selectionService.recomputeAndPersistSettings();

        assertEquals("DYNAMIC", recomputed.selectionMode());
        assertEquals(0.75, recomputed.targetSelectionFraction(), 0.0001);
        assertEquals(22.0, recomputed.combatSizeCutoff(), 0.0001);
        assertEquals(0.25, recomputed.combatSizePercentile(), 0.0001);
        assertEquals(0.76, recomputed.fairnessFloor(), 0.0001);
        assertEquals(0.50, recomputed.fairnessPercentile(), 0.0001);
        assertEquals(0.733_75, recomputed.averageFairness(), 0.0001);
    }

    @Test
    void recomputeAllowsCombatSizeCutoffToDropToNewMinimumFloor() {
        CombatContestSampleRepository sampleRepository = mock(CombatContestSampleRepository.class);
        CombatContestSelectionConfigRepository configRepository = mock(CombatContestSelectionConfigRepository.class);
        CombatContestSelectionService selectionService =
                new CombatContestSelectionService(sampleRepository, configRepository);
        when(sampleRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(any()))
                .thenReturn(List.of(
                        sample(7, 0.90),
                        sample(6, 0.88),
                        sample(5, 0.85),
                        sample(4, 0.83),
                        sample(3, 0.80),
                        sample(2, 0.78),
                        sample(1, 0.76),
                        sample(0.5, 0.74)));

        CombatContestSelectionService.Settings recomputed = selectionService.recomputeAndPersistSettings();

        assertEquals(8.0, recomputed.combatSizeCutoff(), 0.0001);
    }

    private CombatContestSelectionConfigEntity configEntity(
            String mode, double combatSizeCutoff, double fairnessFloor) {
        CombatContestSelectionConfigEntity entity = new CombatContestSelectionConfigEntity();
        entity.setId(1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setSelectionMode(mode);
        entity.setLookbackMinutes(60);
        entity.setWindowSampleCount(12);
        entity.setTargetPostsPerHour(1.0);
        entity.setTargetSelectionFraction(0.10);
        entity.setScoreCutoff(combatSizeCutoff);
        entity.setStrengthScale(14.0);
        entity.setHpScale(0.0);
        entity.setWeakerStrengthWeight(1.0);
        entity.setWeakerHpWeight(0.0);
        entity.setFairnessWeight(fairnessFloor);
        entity.setCooldownMinutes(60);
        entity.setMinimumWeakerStrength(0.0);
        entity.setMinimumSampleCount(24);
        return entity;
    }

    private CombatContestSampleEntity sample(double weakerStrength, double fairnessRatio) {
        CombatContestSampleEntity sample = new CombatContestSampleEntity();
        sample.setWeakerStrength(weakerStrength);
        sample.setFairnessRatio(fairnessRatio);
        return sample;
    }
}
