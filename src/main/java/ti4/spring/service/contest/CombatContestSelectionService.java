package ti4.spring.service.contest;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CombatContestSelectionService {

    private static final int CONFIG_ID = 1;
    private static final String MODE_WARMUP = "WARMUP";
    private static final String MODE_DYNAMIC = "DYNAMIC";
    private static final int LOOKBACK_MINUTES = 60;
    private static final int MINIMUM_SAMPLE_COUNT = 8;
    private static final int COOLDOWN_MINUTES = 30;
    private static final double TARGET_POSTS_PER_HOUR = 1.0;
    private static final double TARGET_FAIR_CANDIDATES_PER_HOUR = 4.0;
    private static final double MIN_TARGET_SELECTION_FRACTION = 0.08;
    private static final double MAX_TARGET_SELECTION_FRACTION = 1.0;
    private static final double DEFAULT_COMBAT_SIZE_CUTOFF = 14.0;
    private static final double DEFAULT_FAIRNESS_FLOOR = 0.72;
    private static final double MIN_FAIRNESS_FLOOR = 0.60;
    private static final double MAX_FAIRNESS_FLOOR = 0.85;

    private final CombatContestSampleRepository sampleRepository;
    private final CombatContestSelectionConfigRepository configRepository;

    private volatile Settings currentSettings = defaultSettings(LocalDateTime.now());

    @PostConstruct
    void initialize() {
        loadPersistedSettings();
    }

    public Evaluation evaluate(
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double attackerExpectedHits,
            double defenderExpectedHits) {
        return evaluate(
                attackerStrength,
                defenderStrength,
                attackerHp,
                defenderHp,
                attackerExpectedHits,
                defenderExpectedHits,
                currentSettings);
    }

    public Settings getCurrentSettings() {
        return currentSettings;
    }

    public Snapshot getSelectionSnapshot() {
        Settings settings = currentSettings;
        int sampleCountLastHour = sampleRepository
                .findByStartedAtGreaterThanEqualOrderByStartedAtAsc(
                        LocalDateTime.now().minusMinutes(LOOKBACK_MINUTES))
                .size();
        double observedCombatsPerHour = sampleCountLastHour * 60.0 / LOOKBACK_MINUTES;
        return new Snapshot(settings, sampleCountLastHour, observedCombatsPerHour, LocalDateTime.now());
    }

    public Snapshot recomputeAndGetSelectionSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        List<CombatContestSampleEntity> samples =
                sampleRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(now.minusMinutes(LOOKBACK_MINUTES));
        Settings recomputed = recomputeAndPersistSettings(samples, now);
        return new Snapshot(recomputed, samples.size(), samples.size() * 60.0 / LOOKBACK_MINUTES, now);
    }

    public void loadPersistedSettings() {
        configRepository.findById(CONFIG_ID).ifPresent(config -> currentSettings = fromEntity(config));
    }

    public Settings recomputeAndPersistSettings() {
        LocalDateTime now = LocalDateTime.now();
        List<CombatContestSampleEntity> samples =
                sampleRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(now.minusMinutes(LOOKBACK_MINUTES));
        return recomputeAndPersistSettings(samples, now);
    }

    private Settings recomputeAndPersistSettings(List<CombatContestSampleEntity> samples, LocalDateTime now) {
        Settings recomputed = recomputeSettings(samples, now);
        CombatContestSelectionConfigEntity entity = toEntity(recomputed);
        configRepository.save(entity);
        currentSettings = recomputed;
        return recomputed;
    }

    private Settings recomputeSettings(List<CombatContestSampleEntity> samples, LocalDateTime now) {
        if (samples.size() < MINIMUM_SAMPLE_COUNT) {
            if (MODE_DYNAMIC.equals(currentSettings.selectionMode())) {
                return preserveDynamicSettings(now, samples.size());
            }
            Settings defaults = defaultSettings(now);
            return new Settings(
                    defaults.updatedAt(),
                    MODE_WARMUP,
                    defaults.lookbackMinutes(),
                    samples.size(),
                    defaults.targetPostsPerHour(),
                    defaults.targetSelectionFraction(),
                    defaults.combatSizeCutoff(),
                    defaults.fairnessFloor(),
                    defaults.fairnessPercentile(),
                    defaults.averageFairness(),
                    defaults.cooldownMinutes(),
                    defaults.minimumSampleCount());
        }

        List<Double> fairnessRatios =
                samples.stream().map(this::computeSampleFairnessRatio).toList();
        double averageFairness = average(fairnessRatios);
        double observedCombatsPerHour = samples.size() * 60.0 / LOOKBACK_MINUTES;
        double fairnessSelectionFraction = clamp(
                TARGET_FAIR_CANDIDATES_PER_HOUR / Math.max(observedCombatsPerHour, 1.0),
                MIN_TARGET_SELECTION_FRACTION,
                MAX_TARGET_SELECTION_FRACTION);
        double fairnessPercentile = 1.0 - fairnessSelectionFraction;
        double fairnessFloor =
                clamp(percentile(fairnessRatios, fairnessPercentile), MIN_FAIRNESS_FLOOR, MAX_FAIRNESS_FLOOR);
        List<CombatContestSampleEntity> fairSamples = samples.stream()
                .filter(sample -> computeSampleFairnessRatio(sample) >= fairnessFloor)
                .toList();
        double fairCombatsPerHour = fairSamples.size() * 60.0 / LOOKBACK_MINUTES;
        double targetSelectionFraction = clamp(
                TARGET_POSTS_PER_HOUR / Math.max(fairCombatsPerHour, 1.0),
                MIN_TARGET_SELECTION_FRACTION,
                MAX_TARGET_SELECTION_FRACTION);
        double combatSizeCutoff = fairSamples.isEmpty()
                ? DEFAULT_COMBAT_SIZE_CUTOFF
                : Math.max(
                        DEFAULT_COMBAT_SIZE_CUTOFF,
                        percentile(
                                fairSamples.stream()
                                        .map(CombatContestSampleEntity::getWeakerStrength)
                                        .toList(),
                                1.0 - targetSelectionFraction));

        return new Settings(
                now,
                MODE_DYNAMIC,
                LOOKBACK_MINUTES,
                samples.size(),
                TARGET_POSTS_PER_HOUR,
                targetSelectionFraction,
                combatSizeCutoff,
                fairnessFloor,
                fairnessPercentile,
                averageFairness,
                COOLDOWN_MINUTES,
                MINIMUM_SAMPLE_COUNT);
    }

    private Settings preserveDynamicSettings(LocalDateTime now, int sampleCount) {
        return new Settings(
                now,
                MODE_DYNAMIC,
                currentSettings.lookbackMinutes(),
                sampleCount,
                currentSettings.targetPostsPerHour(),
                currentSettings.targetSelectionFraction(),
                currentSettings.combatSizeCutoff(),
                currentSettings.fairnessFloor(),
                currentSettings.fairnessPercentile(),
                currentSettings.averageFairness(),
                currentSettings.cooldownMinutes(),
                MINIMUM_SAMPLE_COUNT);
    }

    private CombatContestSelectionConfigEntity toEntity(Settings settings) {
        CombatContestSelectionConfigEntity entity = new CombatContestSelectionConfigEntity();
        entity.setId(CONFIG_ID);
        entity.setUpdatedAt(settings.updatedAt());
        entity.setSelectionMode(settings.selectionMode());
        entity.setLookbackMinutes(settings.lookbackMinutes());
        entity.setWindowSampleCount(settings.windowSampleCount());
        entity.setTargetPostsPerHour(settings.targetPostsPerHour());
        entity.setTargetSelectionFraction(settings.targetSelectionFraction());
        entity.setScoreCutoff(settings.combatSizeCutoff());
        entity.setStrengthScale(DEFAULT_COMBAT_SIZE_CUTOFF);
        entity.setHpScale(settings.averageFairness());
        entity.setWeakerStrengthWeight(1.0);
        entity.setWeakerHpWeight(settings.fairnessPercentile());
        entity.setFairnessWeight(settings.fairnessFloor());
        entity.setCooldownMinutes(settings.cooldownMinutes());
        entity.setMinimumWeakerStrength(0.0);
        entity.setMinimumSampleCount(settings.minimumSampleCount());
        return entity;
    }

    private Settings fromEntity(CombatContestSelectionConfigEntity entity) {
        double fairnessPercentile = entity.getWeakerHpWeight() != null && entity.getWeakerHpWeight() > 0
                ? entity.getWeakerHpWeight()
                : 1.0 - entity.getTargetSelectionFraction();
        double averageFairness = entity.getHpScale() != null && entity.getHpScale() > 0
                ? entity.getHpScale()
                : entity.getFairnessWeight();
        return new Settings(
                entity.getUpdatedAt(),
                entity.getSelectionMode(),
                entity.getLookbackMinutes(),
                entity.getWindowSampleCount(),
                entity.getTargetPostsPerHour(),
                entity.getTargetSelectionFraction(),
                entity.getScoreCutoff(),
                entity.getFairnessWeight(),
                fairnessPercentile,
                averageFairness,
                entity.getCooldownMinutes(),
                MINIMUM_SAMPLE_COUNT);
    }

    private Settings defaultSettings(LocalDateTime now) {
        return new Settings(
                now,
                MODE_WARMUP,
                LOOKBACK_MINUTES,
                0,
                TARGET_POSTS_PER_HOUR,
                0.15,
                DEFAULT_COMBAT_SIZE_CUTOFF,
                DEFAULT_FAIRNESS_FLOOR,
                0.85,
                DEFAULT_FAIRNESS_FLOOR,
                COOLDOWN_MINUTES,
                MINIMUM_SAMPLE_COUNT);
    }

    private Evaluation evaluate(
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double attackerExpectedHits,
            double defenderExpectedHits,
            Settings settings) {
        double weakerStrength = Math.min(attackerStrength, defenderStrength);
        double strongerStrength = Math.max(attackerStrength, defenderStrength);
        double weakerHp = Math.min(attackerHp, defenderHp);
        double strongerHp = Math.max(attackerHp, defenderHp);
        double strengthFairnessRatio = computeFairnessRatio(weakerStrength, strongerStrength);
        double hpFairnessRatio = computeFairnessRatio(weakerHp, strongerHp);
        double weakerExpectedHits = Math.min(attackerExpectedHits, defenderExpectedHits);
        double strongerExpectedHits = Math.max(attackerExpectedHits, defenderExpectedHits);
        double expectedHitsFairnessRatio = computeFairnessRatio(weakerExpectedHits, strongerExpectedHits);
        double fairnessRatio = averageFairnessRatios(strengthFairnessRatio, hpFairnessRatio, expectedHitsFairnessRatio);
        double contestScore = weakerStrength;
        boolean eligible = fairnessRatio >= settings.fairnessFloor() && contestScore >= settings.combatSizeCutoff();
        return new Evaluation(
                weakerStrength, strongerStrength, weakerHp, strongerHp, fairnessRatio, contestScore, eligible);
    }

    private double computeSampleFairnessRatio(CombatContestSampleEntity sample) {
        if (sample.getAttackerExpectedHits() == null || sample.getDefenderExpectedHits() == null) {
            return sample.getFairnessRatio();
        }
        return averageFairnessRatios(
                computeFairnessRatio(sample.getWeakerStrength(), sample.getStrongerStrength()),
                computeFairnessRatio(sample.getWeakerHp(), sample.getStrongerHp()),
                computeFairnessRatio(
                        Math.min(sample.getAttackerExpectedHits(), sample.getDefenderExpectedHits()),
                        Math.max(sample.getAttackerExpectedHits(), sample.getDefenderExpectedHits())));
    }

    private double computeFairnessRatio(double weakerValue, double strongerValue) {
        if (strongerValue <= 0) return 0.0;
        return clamp(weakerValue / strongerValue, 0.0, 1.0);
    }

    private double averageFairnessRatios(double... fairnessRatios) {
        if (fairnessRatios.length == 0) return 0.0;
        double total = 0.0;
        for (double fairnessRatio : fairnessRatios) {
            total += fairnessRatio;
        }
        return total / fairnessRatios.length;
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(clamp(percentile, 0.0, 1.0) * (sorted.size() - 1));
        return sorted.get(index);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Snapshot(
            Settings settings, int sampleCountLastHour, double observedCombatsPerHour, LocalDateTime generatedAt) {}

    public record Evaluation(
            double weakerStrength,
            double strongerStrength,
            double weakerHp,
            double strongerHp,
            double fairnessRatio,
            double contestScore,
            boolean eligibleUnderCurrentThresholds) {}

    public record Settings(
            LocalDateTime updatedAt,
            String selectionMode,
            int lookbackMinutes,
            int windowSampleCount,
            double targetPostsPerHour,
            double targetSelectionFraction,
            double combatSizeCutoff,
            double fairnessFloor,
            double fairnessPercentile,
            double averageFairness,
            int cooldownMinutes,
            int minimumSampleCount) {

        public double combatSizePercentile() {
            return 1.0 - targetSelectionFraction;
        }
    }
}
