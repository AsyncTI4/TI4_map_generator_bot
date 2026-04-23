package ti4.contest.replay.core;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import ti4.json.JsonMapperManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Mutable in-memory settings for the replay contest system.
 */
@Component
@Data
@NoArgsConstructor
public class CombatContestSettings {

    private static final ObjectMapper MAPPER = JsonMapperManager.basic();

    private CandidateSelection candidateSelection = new CandidateSelection();
    private Promotion promotion = new Promotion();
    private ReplayExecution replayExecution = new ReplayExecution();
    private Retention retention = new Retention();
    private Runtime runtime = new Runtime();

    public synchronized CombatContestSettings update(String payloadJson) {
        CombatContestSettings updated = snapshot();
        try {
            MAPPER.readerForUpdating(updated).readValue(payloadJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid settings JSON.", e);
        }
        updated.validate();
        candidateSelection = updated.candidateSelection;
        promotion = updated.promotion;
        replayExecution = updated.replayExecution;
        retention = updated.retention;
        runtime = updated.runtime;
        return snapshot();
    }

    public synchronized CombatContestSettings snapshot() {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsBytes(this), CombatContestSettings.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to snapshot replay settings.", e);
        }
    }

    public void validate() {
        require(candidateSelection != null, "candidateSelection is required.");
        require(candidateSelection.window != null, "candidateSelection.window is required.");
        require(promotion != null, "promotion is required.");
        require(replayExecution != null, "replayExecution is required.");
        require(retention != null, "retention is required.");
        require(runtime != null, "runtime is required.");
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
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @Data
    @NoArgsConstructor
    public static class CandidateSelection {
        private Window window = new Window();
        private int targetCandidatesPerHour = 4;
    }

    @Data
    @NoArgsConstructor
    public static class Window {
        private int lookbackMinutes = 60;
        private int refreshCronIntervalSeconds = 300;
    }

    @Data
    @NoArgsConstructor
    public static class Promotion {
        private int intervalSeconds = 60;
        private int candidateLookbackHours = 4;
        private int maxPromotionsPerHour = 1;
    }

    @Data
    @NoArgsConstructor
    public static class ReplayExecution {
        private int startDelayMinutes = 10;
        private int replayIntervalSeconds = 15;
        private int maxEventGapSeconds = 30;
    }

    @Data
    @NoArgsConstructor
    public static class Retention {
        private int observationRetentionDays = 2;
        private int eventRetentionDays = 2;
    }

    @Data
    @NoArgsConstructor
    public static class Runtime {
        private boolean discordPostingEnabled = false;
    }
}
