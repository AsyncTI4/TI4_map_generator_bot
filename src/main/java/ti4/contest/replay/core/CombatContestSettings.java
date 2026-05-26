package ti4.contest.replay.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Properties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ti4.spring.context.SpringContext;

/**
 * Mutable in-memory settings for the replay contest system.
 */
@Component
@Getter
@Setter
public class CombatContestSettings {

    @Setter(AccessLevel.NONE)
    private boolean isProd;

    private boolean enabled;
    private CandidateSelection candidateSelection = new CandidateSelection();
    private Promotion promotion = new Promotion();
    private ReplayExecution replayExecution = new ReplayExecution();
    private Retention retention = new Retention();
    private Runtime runtime = new Runtime();
    private SideBets sideBets = new SideBets();
    private int initialIndividualPoints = 100;

    public CombatContestSettings() {
        loadEnvironmentDefaults(false);
        validate();
    }

    @Autowired
    public CombatContestSettings(@Value("${combat-contest.is-dev:false}") boolean isDev) {
        isProd = !isDev;
        loadEnvironmentDefaults(isProd);
        validate();
    }

    public void validate() {
        require(candidateSelection != null, "candidateSelection is required.");
        require(candidateSelection.window != null, "candidateSelection.window is required.");
        require(promotion != null, "promotion is required.");
        require(replayExecution != null, "replayExecution is required.");
        require(retention != null, "retention is required.");
        require(runtime != null, "runtime is required.");
        require(sideBets != null, "sideBets is required.");
        require(initialIndividualPoints >= 0, "initialIndividualPoints must be >= 0.");
        require(
                candidateSelection.window.lookbackMinutes > 0,
                "candidateSelection.window.lookbackMinutes must be > 0.");
        require(
                candidateSelection.window.refreshCronIntervalSeconds > 0,
                "candidateSelection.window.refreshCronIntervalSeconds must be > 0.");
        require(
                candidateSelection.targetCandidatesPerHour >= 0,
                "candidateSelection.targetCandidatesPerHour must be >= 0.");
        require(promotion.intervalSeconds > 0, "promotion.intervalSeconds must be > 0.");
        require(replayExecution.startDelaySeconds >= 0, "replayExecution.startDelaySeconds must be >= 0.");
        require(
                replayExecution.dailyLockHourCentral >= -1 && replayExecution.dailyLockHourCentral <= 23,
                "replayExecution.dailyLockHourCentral must be between -1 and 23.");
        require(
                replayExecution.dailyLockMinuteCentral >= 0 && replayExecution.dailyLockMinuteCentral <= 59,
                "replayExecution.dailyLockMinuteCentral must be between 0 and 59.");
        require(replayExecution.replayIntervalSeconds > 0, "replayExecution.replayIntervalSeconds must be > 0.");
        require(replayExecution.maxEventGapSeconds >= 0, "replayExecution.maxEventGapSeconds must be >= 0.");
        require(
                replayExecution.pendingResolutionWindowSeconds > 0,
                "replayExecution.pendingResolutionWindowSeconds must be > 0.");
        require(retention.observationRetentionDays > 0, "retention.observationRetentionDays must be > 0.");
        require(retention.eventRetentionDays > 0, "retention.eventRetentionDays must be > 0.");
        require(sideBets.maxBetsPerUser >= 0, "sideBets.maxBetsPerUser must be >= 0.");
        require(sideBets.costPoints >= 0, "sideBets.costPoints must be >= 0.");
        require(sideBets.dynamicPayoutCap >= 1, "sideBets.dynamicPayoutCap must be >= 1.");
        require(sideBets.afbWhiffSelectionBias >= 0.0, "sideBets.afbWhiffSelectionBias must be >= 0.");
        require(sideBets.roundOneWhiffSelectionBias >= 0.0, "sideBets.roundOneWhiffSelectionBias must be >= 0.");
        require(sideBets.roundOneSlamSelectionBias >= 0.0, "sideBets.roundOneSlamSelectionBias must be >= 0.");
        require(sideBets.winnerOneHpPayoutMultiplier >= 0.0, "sideBets.winnerOneHpPayoutMultiplier must be >= 0.");
        require(
                sideBets.dynamicPayoutTiers != null && !sideBets.dynamicPayoutTiers.isBlank(),
                "sideBets.dynamicPayoutTiers is required.");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @JsonProperty("isProd")
    public boolean isProd() {
        return isProd;
    }

    public static boolean isEnabledStatic() {
        return SpringContext.getBean(CombatContestSettings.class).enabled;
    }

    private void loadEnvironmentDefaults(boolean isProd) {
        String fileName = isProd ? "config/combat-contest-prod.yml" : "config/combat-contest-dev.yml";
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(fileName));
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalStateException("Combat contest settings file is empty: " + fileName);
        }

        BeanWrapper wrapper = new BeanWrapperImpl(this);
        for (var entry : properties.entrySet()) {
            String propertyName = String.valueOf(entry.getKey());
            if ("isProd".equals(propertyName)) continue;
            if (!wrapper.isWritableProperty(propertyName)) {
                throw new IllegalStateException("Unknown combat contest setting: " + propertyName);
            }
            wrapper.setPropertyValue(propertyName, entry.getValue());
        }
    }

    @Getter
    @Setter
    public static class CandidateSelection {
        private Window window = new Window();
        private int targetCandidatesPerHour = 4;
    }

    @Getter
    @Setter
    public static class Window {
        private int lookbackMinutes = 480;
        private int refreshCronIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class Promotion {
        private boolean enabled;
        private int intervalSeconds = 60;
    }

    @Getter
    @Setter
    public static class ReplayExecution {
        private int startDelaySeconds = 15 * 60;
        private int dailyLockHourCentral = -1;
        private int dailyLockMinuteCentral;
        private int replayIntervalSeconds = 15;
        private int maxEventGapSeconds = 30;
        private int pendingResolutionWindowSeconds = 900;
    }

    @Getter
    @Setter
    public static class Retention {
        private int observationRetentionDays = 2;
        private int eventRetentionDays = 2;
    }

    @Getter
    @Setter
    public static class Runtime {
        private boolean devMode;
        private boolean trackAllCombatsAsCandidates;
        private boolean immediatePromotionOnResolve;
    }

    @Getter
    @Setter
    public static class SideBets {
        private boolean enableSideBets = true;
        private int maxBetsPerUser = 3;
        private int costPoints = 1;
        private int dynamicPayoutCap = 50;
        private double afbWhiffSelectionBias = 2.0;
        private double roundOneWhiffSelectionBias = 3.0;
        private double roundOneSlamSelectionBias = 1.5;
        private double winnerOneHpPayoutMultiplier = 0.5625;
        private String dynamicPayoutTiers = "0.20:4,0.10:5,0.05:8,0.025:12,0.01:20,0.005:30";
    }
}
