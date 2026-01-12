package ti4.ai;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for AI player system.
 * Controls feature flags, timing, and behavior limits.
 */
@Component
@Getter
public class AiConfig {

    /**
     * Master feature flag - when false, AI system is completely disabled.
     */
    @Value("${ai.enabled:false}")
    private boolean enabled;

    /**
     * Delay in milliseconds between checking for AI turns (polling fallback).
     */
    @Value("${ai.scheduler.delay:5000}")
    private long schedulerDelay;

    /**
     * Maximum number of actions an AI can take in a single phase.
     */
    @Value("${ai.max-actions-per-phase:10}")
    private int maxActionsPerPhase;

    /**
     * Timeout in milliseconds for a single AI action.
     */
    @Value("${ai.action-timeout:30000}")
    private long actionTimeout;

    /**
     * Minimum delay between AI actions to prevent bursts (milliseconds).
     */
    @Value("${ai.action-delay:1000}")
    private long actionDelay;

    /**
     * Maximum jitter to add to action delays (milliseconds).
     */
    @Value("${ai.action-jitter:500}")
    private long actionJitter;

    /**
     * Default strategy difficulty for new AI players.
     */
    @Value("${ai.default-difficulty:simple}")
    private String defaultDifficulty;

    /**
     * Whether to run in dry-run mode (log actions without executing).
     */
    @Value("${ai.dry-run:false}")
    private boolean dryRun;

    /**
     * Maximum number of retries for failed actions.
     */
    @Value("${ai.max-retries:3}")
    private int maxRetries;

    /**
     * Whether AI should be verbose in logging decisions.
     */
    @Value("${ai.verbose-logging:true}")
    private boolean verboseLogging;

    /**
     * Check if AI system is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if dry-run mode is active.
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Get the strategy class name for a given difficulty.
     */
    public String getStrategyClassName(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "simple" -> "ti4.ai.SimpleHeuristicStrategy";
            case "medium" -> "ti4.ai.MediumStrategy"; // Future implementation
            case "hard" -> "ti4.ai.HardStrategy"; // Future implementation
            default -> "ti4.ai.SimpleHeuristicStrategy";
        };
    }
}
