package ti4.executors;

public enum ShutdownResult {
    GRACEFUL_TERMINATION,
    FORCED_TERMINATION,
    TIMED_OUT,
    INTERRUPTED
}
