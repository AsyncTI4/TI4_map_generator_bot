package ti4.spring.service.persistence;

import ti4.game.persistence.GameFileStamp;
import ti4.game.persistence.ManagedGameState;

public record PersistedManagedGame(ManagedGameState state, GameFileStamp gameFileStamp) {}
