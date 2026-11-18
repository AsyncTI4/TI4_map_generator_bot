package ti4.spring.service.persistence;

import ti4.game.persistence.ManagedGameState;

public record ParticipantRow(
        String gameName, String userId, String userName, boolean playing, boolean eliminated, boolean npc) {

    ManagedGameState.Participant toParticipant() {
        return new ManagedGameState.Participant(userId, userName, playing, eliminated, npc);
    }
}
