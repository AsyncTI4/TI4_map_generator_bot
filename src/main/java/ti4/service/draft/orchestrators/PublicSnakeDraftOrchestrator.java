package ti4.service.draft.orchestrators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.OrchestratorState;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.draft.PublicDraftInfoService;

public class PublicSnakeDraftOrchestrator extends DraftOrchestrator {
    public static class State extends OrchestratorState {
        private int orderIndex;

        public int getOrderIndex() {
            return orderIndex;
        }

        public void setOrderIndex(int orderIndex) {
            this.orderIndex = orderIndex;
        }
    }

    // Value: Player UserIDs in draft order
    // private final List<String> playerOrder = new ArrayList<>();
    private int currentPlayerIndex;
    private boolean isReversing;

    public void initialize(DraftManager draftManager, List<String> presetPlayerOrder) {
        int orderIndex = 0;
        if (presetPlayerOrder != null) {
            for (String playerUserId : presetPlayerOrder) {
                PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
                if (playerState == null) {
                    throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
                }
                State orchestratorState = new State();
                orchestratorState.setOrderIndex(orderIndex++);
                playerState.setOrchestratorState(orchestratorState);
            }
        } else {
            List<String> shuffledPlayers = new ArrayList<>(draftManager.getPlayerStates().keySet());
            Collections.shuffle(shuffledPlayers);
            for (String playerUserId : shuffledPlayers) {
                PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
                State orchestratorState = new State();
                orchestratorState.setOrderIndex(orderIndex++);
                playerState.setOrchestratorState(orchestratorState);
            }
        }

        currentPlayerIndex = 0;
        isReversing = false;
    }

    @Override
    public void startDraft(DraftManager draftManager) {
        List<String> playerOrder = getPlayerOrder(draftManager);
        PublicDraftInfoService.send(
                draftManager, playerOrder, getCurrentPlayer(playerOrder), getNextPlayer(playerOrder),
                List.of(getReprintDraftButton()));
    }

    @Override
    public String applyDraftChoice(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {
        List<String> playerOrder = getPlayerOrder(draftManager);

        // Picks are made one player at a time, with all buttons visible.
        // Ensure this is the current player
        if (!playerUserId.equals(getCurrentPlayer(playerOrder))) {
            return "It's not your turn to draft.";
        }
        // Ensure no one else has picked this choice
        if (draftManager.getPlayersWithChoiceKey(choice.getType(), choice.getChoiceKey()).size() > 0) {
            return "That choice has already been taken.";
        }

        // Persist the choice in Player State.
        Map<DraftableType, List<DraftChoice>> playerChoices = draftManager.getPlayerStates().get(playerUserId)
                .getPicks();
        playerChoices.computeIfAbsent(choice.getType(), k -> new ArrayList<>()).add(choice);

        Player player = draftManager.getGame().getPlayer(playerUserId);
        StringBuilder announcement = new StringBuilder();
        announcement.append(player.getPing() + " drafted ");
        if (choice.getIdentifyingEmoji() != null) {
            announcement.append(choice.getIdentifyingEmoji() + " ");
        }
        announcement.append(choice.getDisplayName() + "!");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), announcement.toString());

        // Move the draft to the next player
        advanceToNextPlayer(playerOrder);

        // Can the next player be auto-picked?
        int simultaneousPicks = 1;
        if (currentPlayerIndex == 0 && isReversing)
            simultaneousPicks = 2;
        else if (currentPlayerIndex == playerOrder.size() - 1 && !isReversing)
            simultaneousPicks = 2;
        List<DraftChoice> totalPossiblePicks = new ArrayList<>();
        for (Draftable draftable : draftManager.getDraftables()) {
            List<DraftChoice> deterministicPicks = draftable.getDeterministicPick(draftManager,
                    getCurrentPlayer(playerOrder), simultaneousPicks);
            if (deterministicPicks != null) {
                totalPossiblePicks.addAll(deterministicPicks);
            }
            if (totalPossiblePicks.size() > simultaneousPicks)
                break;
        }
        if (totalPossiblePicks.size() <= simultaneousPicks) {
            Player nextPlayer = draftManager.getGame().getPlayer(getCurrentPlayer(playerOrder));
            DraftChoice forcedPick = totalPossiblePicks.get(0);
            Draftable forcedDraftable = draftManager.getDraftableByType(forcedPick.getType());
            String status = draftManager.routeCommand(event, nextPlayer,
                    forcedDraftable.makeButtonId(forcedPick.getChoiceKey()));
            DraftButtonService.handleButtonResult(event, status);
        } else {
            PublicDraftInfoService.edit(event, draftManager, playerOrder, getCurrentPlayer(playerOrder),
                    getNextPlayer(playerOrder), choice.getType());
            PublicDraftInfoService.pingCurrentPlayer(draftManager, getCurrentPlayer(playerOrder), List.of(), List.of(),
                    List.of(getReprintDraftButton()));
        }

        PartialMapService.tryUpdateMap(event, draftManager);

        return DraftButtonService.DELETE_BUTTON;
    }

    @Override
    public String save() {
        return currentPlayerIndex + "," + isReversing;
    }

    @Override
    public void load(String data) {
        String[] tokens = data.split(",", 2);
        if (tokens.length != 2) {
            throw new IllegalArgumentException("Invalid data for PublicSnakeDraftOrchestrator: " + data);
        }
        currentPlayerIndex = Integer.parseInt(tokens[0]);
        isReversing = Boolean.parseBoolean(tokens[1]);
    }

    @Override
    public String[] savePlayerStates(DraftManager draftManager) {
        String[] playerStates = new String[draftManager.getPlayerStates().size()];
        int i = 0;
        for (Map.Entry<String, PlayerDraftState> entry : draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState playerState = entry.getValue();
            OrchestratorState orchestratorState = playerState.getOrchestratorState();
            if (!(orchestratorState instanceof State)) {
                throw new IllegalStateException("Player " + playerUserId + " has invalid orchestrator state");
            }
            State state = (State) orchestratorState;
            playerStates[i++] = playerUserId + "," + state.getOrderIndex();
        }
        return playerStates;
    }

    @Override
    public OrchestratorState loadPlayerState(String data) {
        int orderIndex = Integer.parseInt(data);
        State state = new State();
        state.setOrderIndex(orderIndex);
        return state;
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (currentPlayerIndex < 0
                || currentPlayerIndex >= draftManager.getPlayerStates().size()) {
            return "Current player index is out of bounds: " + currentPlayerIndex;
        }
        // Ensure all players have a valid State, with unique and valid order indices.
        Set<Integer> distinctOrderIndices = new HashSet<>();
        for (Map.Entry<String, PlayerDraftState> entry : draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState playerState = entry.getValue();
            OrchestratorState orchestratorState = playerState.getOrchestratorState();
            if (orchestratorState == null || !(orchestratorState instanceof State)) {
                return "Player " + playerUserId + " has invalid orchestrator state (missing or unexpected)";
            }
            State state = (State) orchestratorState;
            if (state.getOrderIndex() < 0
                    || state.getOrderIndex() >= draftManager.getPlayerStates().size()) {
                return "Player " + playerUserId + " has out of bounds order index: " + state.getOrderIndex();
            }
            if (distinctOrderIndices.contains(state.getOrderIndex())) {
                return "Duplicate order index found: " + state.getOrderIndex();
            }
            distinctOrderIndices.add(state.getOrderIndex());
        }
        if (distinctOrderIndices.size() != draftManager.getPlayerStates().size()) {
            return "Player order indices are not unique";
        }

        return null;
    }

    @Override
    public String getButtonPrefix() {
        return "psd_";
    }

    private Button getReprintDraftButton() {
        return Buttons.gray(DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + getButtonPrefix() + "reprintdraft",
                "Show draft again");
    }

    @Override
    public String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {

        if (buttonId.equals("reprintdraft")) {
            List<String> playerOrder = getPlayerOrder(draftManager);
            PublicDraftInfoService.send(
                    draftManager, playerOrder, getCurrentPlayer(playerOrder), getNextPlayer(playerOrder),
                    List.of(getReprintDraftButton()));
            return null;
        }

        return "Unknown button action: " + buttonId;
    }

    @Override
    public String getBlockingDraftEndReason(DraftManager draftManager) {
        // This draft mode doesn't impose any additional requirements beyond what the
        // draftables require.
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(DraftManager draftManager, String playerUserId,
            PlayerSetupState playerSetupState) {
        // This draft mode doesn't do any player setup itself, the draftables handle
        // everything.
        return null;
    }

    private List<String> getPlayerOrder(DraftManager draftManager) {
        List<String> playerOrder = new ArrayList<>();
        int numPlayers = draftManager.getPlayerStates().size();
        for (int i = 0; i < numPlayers; i++) {
            for (String playerUserId : draftManager.getPlayerStates().keySet()) {
                State orchestratorState = (State) draftManager.getPlayerStates().get(playerUserId)
                        .getOrchestratorState();
                if (orchestratorState.getOrderIndex() == i) {
                    playerOrder.add(playerUserId);
                    break;
                }
            }
        }
        return playerOrder;
    }

    private String getCurrentPlayer(List<String> playerOrder) {
        return playerOrder.get(currentPlayerIndex);
    }

    private String getNextPlayer(List<String> playerOrder) {
        int nextIndex = currentPlayerIndex + (isReversing ? -1 : 1);
        if (nextIndex < 0 || nextIndex >= playerOrder.size()) {
            // When you get to an end of the snake, the next player is the current player
            // repeated.
            return playerOrder.get(currentPlayerIndex);
        }

        return playerOrder.get(nextIndex);
    }

    private void advanceToNextPlayer(List<String> playerOrder) {
        currentPlayerIndex += isReversing ? -1 : 1;
        if (currentPlayerIndex < 0) {
            currentPlayerIndex = 0;
            isReversing = false;
        } else if (currentPlayerIndex >= playerOrder.size()) {
            currentPlayerIndex = playerOrder.size() - 1;
            isReversing = true;
        }
    }
}
