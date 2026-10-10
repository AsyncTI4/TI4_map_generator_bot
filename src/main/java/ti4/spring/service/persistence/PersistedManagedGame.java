package ti4.spring.service.persistence;

import ti4.game.persistence.ManagedGameState;

public record PersistedManagedGame(ManagedGameState state, long gameFileModifiedEpochMilliseconds) {

    public boolean matchesGameFile(long gameFileLastModifiedEpochMilliseconds) {
        return gameFileModifiedEpochMilliseconds > 0
                && gameFileModifiedEpochMilliseconds == gameFileLastModifiedEpochMilliseconds;
    }
}
