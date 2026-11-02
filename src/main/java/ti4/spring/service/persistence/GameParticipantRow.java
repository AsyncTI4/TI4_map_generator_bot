package ti4.spring.service.persistence;

import ti4.game.persistence.ManagedGameState;

public record GameParticipantRow(
        String gameName, String userId, String userName, boolean realPlayer, boolean realOrEliminated) {

    ManagedGameState.Participant toParticipant() {
        return new ManagedGameState.Participant(userId, userName, realPlayer, realOrEliminated);
    }
}
