package ti4.spring.service.persistence;

import ti4.game.persistence.ManagedGameState;

public record PersistedManagedGame(ManagedGameState state) {

    public boolean matchesRevision(String fileRevisionKey) {
        return fileRevisionKey != null && !fileRevisionKey.isBlank() && fileRevisionKey.equals(state.revisionKey());
    }
}
