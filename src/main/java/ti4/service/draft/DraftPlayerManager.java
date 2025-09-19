package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DraftPlayerManager {

    // Key: Player's UserID
    protected final Map<String, PlayerDraftState> playerStates = new HashMap<>();

    // Setup

    public void setPlayers(List<String> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }
        for (String player : players) {
            if (playerStates.containsKey(player)) {
                throw new IllegalArgumentException("Duplicate player: " + player);
            }
            playerStates.put(player, new PlayerDraftState());
        }
    }

    public void addPlayer(String playerUserId) {
        if (playerUserId == null || playerUserId.isEmpty()) {
            throw new IllegalArgumentException("Player user ID cannot be null or empty");
        }
        if (playerStates.containsKey(playerUserId)) {
            throw new IllegalArgumentException("Player " + playerUserId + " is already in the draft");
        }
        playerStates.put(playerUserId, new PlayerDraftState());
    }

    public void removePlayer(String playerUserId) {
        if (playerUserId == null || playerUserId.isEmpty()) {
            throw new IllegalArgumentException("Player user ID cannot be null or empty");
        }
        if (!playerStates.containsKey(playerUserId)) {
            throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
        }
        playerStates.remove(playerUserId);
    }

    public void resetForNewDraft() {
        this.playerStates.clear();
    }

    // Player management

    public void replacePlayer(String oldUserId, String newUserId) {
        if (!playerStates.containsKey(oldUserId)) {
            throw new IllegalArgumentException("Cannot replace player " + oldUserId + "; not in draft");
        }
        if (playerStates.containsKey(newUserId)) {
            throw new IllegalArgumentException("Cannot replace player with " + newUserId + "; already in draft");
        }

        PlayerDraftState state = playerStates.remove(oldUserId);
        playerStates.put(newUserId, state);
    }

    public void swapPlayers(String userId1, String userId2) {
        if (!playerStates.containsKey(userId1)) {
            throw new IllegalArgumentException("Cannot swap player " + userId1 + "; not in draft");
        }
        if (!playerStates.containsKey(userId2)) {
            throw new IllegalArgumentException("Cannot swap player " + userId2 + "; not in draft");
        }

        PlayerDraftState state1 = playerStates.get(userId1);
        PlayerDraftState state2 = playerStates.get(userId2);
        playerStates.put(userId1, state2);
        playerStates.put(userId2, state1);
    }

    // Information Access

    public List<DraftChoice> getPlayerPicks(String playerUserId, DraftableType type) {
        if (!playerStates.containsKey(playerUserId)) {
            throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
        }
        PlayerDraftState pState = playerStates.get(playerUserId);
        if (!pState.getPicks().containsKey(type)) {
            return List.of();
        }
        List<DraftChoice> choices = pState.getPicks().get(type);
        return choices;
    }

    public List<String> getPlayersWithChoiceKey(DraftableType type, String choiceKey) {
        List<String> playersWithChoice = new ArrayList<>();
        for (String userId : playerStates.keySet()) {
            List<DraftChoice> choices = getPlayerPicks(userId, type);
            for (DraftChoice choice : choices) {
                if (choice.getChoiceKey().equals(choiceKey)) {
                    playersWithChoice.add(userId);
                    break;
                }
            }
        }
        return playersWithChoice;
    }

    public List<DraftChoice> getAllPicksOfType(DraftableType type) {
        List<DraftChoice> allChoices = new ArrayList<>();
        for (String userId : playerStates.keySet()) {
            PlayerDraftState pState = playerStates.get(userId);
            if (pState.getPicks().containsKey(type)) {
                allChoices.addAll(pState.getPicks().get(type));
            }
        }
        return allChoices;
    }

    /**
     * Check that all required fields are set and that all shared state is
     * consistent.
     */
    public void validateState() {

        if (playerStates == null) {
            throw new IllegalStateException("Player states not set");
        }
    }
}
