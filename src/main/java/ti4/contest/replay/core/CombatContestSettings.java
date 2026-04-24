package ti4.contest.replay.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Mutable in-memory settings for the replay contest system.
 */
@Component
@Getter
@Setter
public class CombatContestSettings {

    public static final int PROMOTION_LOOKBACK_FALLBACK_MAX_HOURS = 8;

    @Setter(AccessLevel.NONE)
    private boolean isProd = true;

    private CandidateSelection candidateSelection = new CandidateSelection();
    private Promotion promotion = new Promotion();
    private ReplayExecution replayExecution = new ReplayExecution();
    private Retention retention = new Retention();
    private Runtime runtime = new Runtime();
    private SideBets sideBets = new SideBets();

    public CombatContestSettings() {
        applyEnvironmentDefaults();
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
        require(promotion.candidateLookbackHours > 0, "promotion.candidateLookbackHours must be > 0.");
        require(promotion.maxPromotionsPerHour >= 0, "promotion.maxPromotionsPerHour must be >= 0.");
        require(replayExecution.startDelayMinutes >= 0, "replayExecution.startDelayMinutes must be >= 0.");
        require(replayExecution.replayIntervalSeconds > 0, "replayExecution.replayIntervalSeconds must be > 0.");
        require(replayExecution.maxEventGapSeconds >= 0, "replayExecution.maxEventGapSeconds must be >= 0.");
        require(retention.observationRetentionDays > 0, "retention.observationRetentionDays must be > 0.");
        require(retention.eventRetentionDays > 0, "retention.eventRetentionDays must be > 0.");
        require(runtime.versionEnabled != null, "runtime.versionEnabled is required.");
        require(sideBets.maxBetsPerUser >= 0, "sideBets.maxBetsPerUser must be >= 0.");
        require(sideBets.costPoints >= 0, "sideBets.costPoints must be >= 0.");
        require(
                "v1".equalsIgnoreCase(runtime.versionEnabled) || "v2".equalsIgnoreCase(runtime.versionEnabled),
                "runtime.versionEnabled must be 'v1' or 'v2'.");
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

    private void applyEnvironmentDefaults() {
        if (isProd) {
            replayExecution.setStartDelayMinutes(10);
            replayExecution.setReplayIntervalSeconds(15);
            replayExecution.setMaxEventGapSeconds(30);
            runtime.setTrackAllCombatsAsCandidates(false);
            runtime.setImmediatePromotionOnResolve(false);
            sideBets.setEnableSideBets(true);
        } else {
            replayExecution.setStartDelayMinutes(1);
            replayExecution.setReplayIntervalSeconds(1);
            replayExecution.setMaxEventGapSeconds(1);
            runtime.setTrackAllCombatsAsCandidates(true);
            runtime.setImmediatePromotionOnResolve(true);
            sideBets.setEnableSideBets(true);
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
        private int lookbackMinutes = 60;
        private int refreshCronIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class Promotion {
        private int intervalSeconds = 60;
        private int candidateLookbackHours = 4;
        private int maxPromotionsPerHour = 1;
    }

    @Getter
    @Setter
    public static class ReplayExecution {
        private int startDelayMinutes = 10;
        private int replayIntervalSeconds = 15;
        private int maxEventGapSeconds = 30;
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
        private boolean discordPostingEnabled = true;
        private String versionEnabled = "v2";
        private boolean trackAllCombatsAsCandidates = false;
        private boolean immediatePromotionOnResolve = false;
    }

    @Getter
    @Setter
    public static class SideBets {
        private boolean enableSideBets = false;
        private int maxBetsPerUser = 5;
        private int costPoints = 1;
    }
}
