package ti4.game.persistence;

/**
 * Describes why managed-game metadata is being materialized, which determines whether the parsed Game should be
 * retained for an immediate follow-up read.
 */
enum ManagedGameLoadMode {
    /** Keeps the parsed Game for a single immediate getGame() call on the same request path. */
    LAZY_REQUEST,

    /** Discards the parsed Game after extracting lightweight metadata to minimize warmup memory usage. */
    WARMUP
}
