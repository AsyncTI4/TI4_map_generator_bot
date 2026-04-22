package ti4.spring.service.contest;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final int COOLDOWN_MINUTES = 60;
    private static final double TARGET_POSTS_PER_HOUR = 1.0;
    private static final double MIN_TARGET_SELECTION_FRACTION = 0.08;
    private static final double MAX_TARGET_SELECTION_FRACTION = 0.50;
    private static final double DEFAULT_STRENGTH_SCALE = 28.0;
    private static final double DEFAULT_HP_SCALE = 10.0;
    private static final double DEFAULT_SCORE_CUTOFF = 0.78;
    private static final double MINIMUM_WEAKER_STRENGTH = 8.0;
    private static final double WEAKER_STRENGTH_WEIGHT = 0.30;
    private static final double WEAKER_HP_WEIGHT = 0.20;
    private static final double FAIRNESS_WEIGHT = 0.50;
    private static final double SCALE_PERCENTILE = 0.90;

    private final CombatContestSampleRepository sampleRepository;
    private final CombatContestSelectionConfigRepository configRepository;

    private volatile Settings currentSettings = defaultSettings(LocalDateTime.now());

    @PostConstruct
    void initialize() {
        loadPersistedSettings();
    }

    public Evaluation evaluate(double attackerStrength, double defenderStrength, double attackerHp, double defenderHp) {
        return evaluate(attackerStrength, defenderStrength, attackerHp, defenderHp, currentSettings);
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

    public void loadPersistedSettings() {
        configRepository.findById(CONFIG_ID).ifPresent(config -> currentSettings = fromEntity(config));
    }

    public Settings recomputeAndPersistSettings() {
        LocalDateTime now = LocalDateTime.now();
        List<CombatContestSampleEntity> samples =
                sampleRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(now.minusMinutes(LOOKBACK_MINUTES));
        Settings recomputed = recomputeSettings(samples, now);
        CombatContestSelectionConfigEntity entity = toEntity(recomputed);
        configRepository.save(entity);
        currentSettings = recomputed;
        return recomputed;
    }

    private Settings recomputeSettings(List<CombatContestSampleEntity> samples, LocalDateTime now) {
        if (samples.size() < MINIMUM_SAMPLE_COUNT) {
            Settings defaults = defaultSettings(now);
            return new Settings(
                    defaults.updatedAt(),
                    MODE_WARMUP,
                    defaults.lookbackMinutes(),
                    samples.size(),
                    defaults.targetPostsPerHour(),
                    defaults.targetSelectionFraction(),
                    defaults.scoreCutoff(),
                    defaults.strengthScale(),
                    defaults.hpScale(),
                    defaults.weakerStrengthWeight(),
                    defaults.weakerHpWeight(),
                    defaults.fairnessWeight(),
                    defaults.cooldownMinutes(),
                    defaults.minimumWeakerStrength(),
                    defaults.minimumSampleCount());
        }

        double strengthScale = Math.max(
                DEFAULT_STRENGTH_SCALE,
                percentile(
                        samples.stream()
                                .map(CombatContestSampleEntity::getWeakerStrength)
                                .toList(),
                        SCALE_PERCENTILE));
        double hpScale = Math.max(
                DEFAULT_HP_SCALE,
                percentile(
                        samples.stream()
                                .map(CombatContestSampleEntity::getWeakerHp)
                                .toList(),
                        SCALE_PERCENTILE));

        List<Double> scores = new ArrayList<>(samples.size());
        for (CombatContestSampleEntity sample : samples) {
            scores.add(computeScore(
                    sample.getWeakerStrength(),
                    sample.getWeakerHp(),
                    sample.getFairnessRatio(),
                    strengthScale,
                    hpScale,
                    WEAKER_STRENGTH_WEIGHT,
                    WEAKER_HP_WEIGHT,
                    FAIRNESS_WEIGHT));
        }

        double observedCombatsPerHour = samples.size() * 60.0 / LOOKBACK_MINUTES;
        double targetSelectionFraction = clamp(
                TARGET_POSTS_PER_HOUR / Math.max(observedCombatsPerHour, 1.0),
                MIN_TARGET_SELECTION_FRACTION,
                MAX_TARGET_SELECTION_FRACTION);
        double scoreCutoff = percentile(scores, 1.0 - targetSelectionFraction);

        return new Settings(
                now,
                MODE_DYNAMIC,
                LOOKBACK_MINUTES,
                samples.size(),
                TARGET_POSTS_PER_HOUR,
                targetSelectionFraction,
                scoreCutoff,
                strengthScale,
                hpScale,
                WEAKER_STRENGTH_WEIGHT,
                WEAKER_HP_WEIGHT,
                FAIRNESS_WEIGHT,
                COOLDOWN_MINUTES,
                MINIMUM_WEAKER_STRENGTH,
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
        entity.setScoreCutoff(settings.scoreCutoff());
        entity.setStrengthScale(settings.strengthScale());
        entity.setHpScale(settings.hpScale());
        entity.setWeakerStrengthWeight(settings.weakerStrengthWeight());
        entity.setWeakerHpWeight(settings.weakerHpWeight());
        entity.setFairnessWeight(settings.fairnessWeight());
        entity.setCooldownMinutes(settings.cooldownMinutes());
        entity.setMinimumWeakerStrength(settings.minimumWeakerStrength());
        entity.setMinimumSampleCount(settings.minimumSampleCount());
        return entity;
    }

    private Settings fromEntity(CombatContestSelectionConfigEntity entity) {
        return new Settings(
                entity.getUpdatedAt(),
                entity.getSelectionMode(),
                entity.getLookbackMinutes(),
                entity.getWindowSampleCount(),
                entity.getTargetPostsPerHour(),
                entity.getTargetSelectionFraction(),
                entity.getScoreCutoff(),
                entity.getStrengthScale(),
                entity.getHpScale(),
                entity.getWeakerStrengthWeight(),
                entity.getWeakerHpWeight(),
                entity.getFairnessWeight(),
                entity.getCooldownMinutes(),
                entity.getMinimumWeakerStrength(),
                entity.getMinimumSampleCount());
    }

    private Settings defaultSettings(LocalDateTime now) {
        return new Settings(
                now,
                MODE_WARMUP,
                LOOKBACK_MINUTES,
                0,
                TARGET_POSTS_PER_HOUR,
                0.15,
                DEFAULT_SCORE_CUTOFF,
                DEFAULT_STRENGTH_SCALE,
                DEFAULT_HP_SCALE,
                WEAKER_STRENGTH_WEIGHT,
                WEAKER_HP_WEIGHT,
                FAIRNESS_WEIGHT,
                COOLDOWN_MINUTES,
                MINIMUM_WEAKER_STRENGTH,
                MINIMUM_SAMPLE_COUNT);
    }

    private Evaluation evaluate(
            double attackerStrength, double defenderStrength, double attackerHp, double defenderHp, Settings settings) {
        double weakerStrength = Math.min(attackerStrength, defenderStrength);
        double strongerStrength = Math.max(attackerStrength, defenderStrength);
        double weakerHp = Math.min(attackerHp, defenderHp);
        double strongerHp = Math.max(attackerHp, defenderHp);
        double strengthFairnessRatio = computeFairnessRatio(weakerStrength, strongerStrength);
        double hpFairnessRatio = computeFairnessRatio(weakerHp, strongerHp);
        double fairnessRatio = (strengthFairnessRatio + hpFairnessRatio) / 2.0;
        double contestScore = computeScore(
                weakerStrength,
                weakerHp,
                fairnessRatio,
                settings.strengthScale(),
                settings.hpScale(),
                settings.weakerStrengthWeight(),
                settings.weakerHpWeight(),
                settings.fairnessWeight());
        boolean eligible = weakerStrength >= settings.minimumWeakerStrength()
                && attackerHp > 0
                && defenderHp > 0
                && contestScore >= settings.scoreCutoff();
        return new Evaluation(
                weakerStrength, strongerStrength, weakerHp, strongerHp, fairnessRatio, contestScore, eligible);
    }

    private double computeFairnessRatio(double weakerValue, double strongerValue) {
        if (strongerValue <= 0) return 0.0;
        return clamp(weakerValue / strongerValue, 0.0, 1.0);
    }

    private double computeScore(
            double weakerStrength,
            double weakerHp,
            double fairnessRatio,
            double strengthScale,
            double hpScale,
            double weakerStrengthWeight,
            double weakerHpWeight,
            double fairnessWeight) {
        return weakerStrengthWeight * normalize(weakerStrength, strengthScale)
                + weakerHpWeight * normalize(weakerHp, hpScale)
                + fairnessWeight * clamp(fairnessRatio, 0.0, 1.0);
    }

    private double normalize(double value, double scale) {
        if (scale <= 0) return 0.0;
        return clamp(value / scale, 0.0, 1.0);
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
            double scoreCutoff,
            double strengthScale,
            double hpScale,
            double weakerStrengthWeight,
            double weakerHpWeight,
            double fairnessWeight,
            int cooldownMinutes,
            double minimumWeakerStrength,
            int minimumSampleCount) {}
}
