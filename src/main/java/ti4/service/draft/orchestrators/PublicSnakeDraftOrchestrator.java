package ti4.service.draft.orchestrators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.DraftPlayerManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.OrchestratorState;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.draft.PublicDraftInfoService;
import ti4.service.draft.DraftManager.CommandSource;

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

    @Getter
    @Setter
    private int currentPlayerIndex;

    @Getter
    @Setter
    private boolean isReversing;

    public void initialize(DraftManager draftManager, List<String> presetPlayerOrder) {
        initializePlayerStates(draftManager);

        if (presetPlayerOrder != null) {
            setDraftOrder(draftManager, presetPlayerOrder);
        } else {
            List<String> shuffledPlayers =
                    new ArrayList<>(draftManager.getPlayerStates().keySet());
            Collections.shuffle(shuffledPlayers);
            setDraftOrder(draftManager, shuffledPlayers);
        }

        currentPlayerIndex = 0;
        isReversing = false;
    }

    public void setDraftOrder(DraftPlayerManager draftManager, List<String> playerOrder) {
        if (playerOrder.size() != draftManager.getPlayerStates().size()) {
            throw new IllegalArgumentException("Player order size "
                    + playerOrder.size()
                    + " does not match number of players in draft "
                    + draftManager.getPlayerStates().size());
        }
        Set<String> distinctPlayers = new HashSet<>(playerOrder);
        if (distinctPlayers.size() != playerOrder.size()) {
            throw new IllegalArgumentException("Player order contains duplicate players");
        }
        for (String playerUserId : playerOrder) {
            if (!draftManager.getPlayerStates().containsKey(playerUserId)) {
                throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
            }
        }

        int orderIndex = 0;
        for (String playerUserId : playerOrder) {
            PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
            State orchestratorState = (State) playerState.getOrchestratorState();
            orchestratorState.setOrderIndex(orderIndex++);
        }
    }

    public void setPlayerPosition(DraftPlayerManager draftManager, String playerUserId, int position) {
        if (position < 1 || position > draftManager.getPlayerStates().size()) {
            throw new IllegalArgumentException("Position " + position + " is out of bounds for draft with "
                    + draftManager.getPlayerStates().size()
                    + " players");
        }
        if (!draftManager.getPlayerStates().containsKey(playerUserId)) {
            throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
        }

        State targetState =
                (State) draftManager.getPlayerStates().get(playerUserId).getOrchestratorState();
        int currentIndex = targetState.getOrderIndex();
        int targetIndex = position - 1;
        if (currentIndex == targetIndex) {
            // No change
            return;
        }

        // Finally set the target player's index
        targetState.setOrderIndex(targetIndex);
    }

    @Override
    public void initializePlayerStates(DraftPlayerManager draftManager) {
        for (String playerUserId : draftManager.getPlayerStates().keySet()) {
            PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
            if (playerState.getOrchestratorState() == null || !(playerState.getOrchestratorState() instanceof State)) {
                State orchestratorState = new State();
                playerState.setOrchestratorState(orchestratorState);
            }
        }
    }

    @Override
    public void sendDraftButtons(DraftManager draftManager) {
        List<String> playerOrder = getPlayerOrder(draftManager);
        PublicDraftInfoService.send(
                draftManager,
                playerOrder,
                getCurrentPlayer(playerOrder),
                getNextPlayer(playerOrder),
                List.of(getReprintDraftButton()));
    }

    @Override
    public String applyDraftChoice(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice, CommandSource source) {
        List<String> playerOrder = getPlayerOrder(draftManager);

        // Picks are made one player at a time, with all buttons visible.
        // Ensure this is the current player
        if (!playerUserId.equals(getCurrentPlayer(playerOrder))) {
            return "It's not your turn to draft.";
        }
        // Ensure no one else has picked this choice
        if (draftManager
                        .getPlayersWithChoiceKey(choice.getType(), choice.getChoiceKey())
                        .size()
                > 0) {
            return "That choice has already been taken.";
        }

        // Persist the choice in Player State.
        Map<DraftableType, List<DraftChoice>> playerChoices =
                draftManager.getPlayerStates().get(playerUserId).getPicks();
        playerChoices.computeIfAbsent(choice.getType(), k -> new ArrayList<>()).add(choice);

        Player player = draftManager.getGame().getPlayer(playerUserId);
        StringBuilder sb = new StringBuilder();
        sb.append(player.getPing())
                .append(" drafted ");
        if(source == CommandSource.AUTO_PICK) {
            sb.append("(automatically) ");
        } else if(source == CommandSource.SLASH_COMMAND) {
            sb.append("(forcefully) ");
        }
        sb.append(choice.getDisplayName())
                .append("!");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), sb.toString());

        // Move the draft to the next player
        advanceToNextPlayer(playerOrder);

        // Do automated picking when there's only deterministic picks.
        int simultaneousPicks = 1;
        // TODO: We can support doing multiple simultaneous picks, but there's a race condition in the
        // "delete one button" method: it works by editing the whole message to be the same except that
        // one button. If multiple picks happen at once, these operations coincide and whichever edit process
        // resolves last is the only one that deletes its button, while re-adding the other deleted buttons.
        // if (currentPlayerIndex == 0 && isReversing) simultaneousPicks = 2;
        // else if (currentPlayerIndex == playerOrder.size() - 1 && !isReversing) simultaneousPicks = 2;
        List<DraftChoice> totalPossiblePicks = new ArrayList<>();
        boolean undeterministicPicks = false;
        for (Draftable draftable : draftManager.getDraftables()) {
            List<DraftChoice> deterministicPicks =
                    draftable.getDeterministicPick(draftManager, getCurrentPlayer(playerOrder), simultaneousPicks);
            if (deterministicPicks == null) {
                undeterministicPicks = true;
                break;
            }

            totalPossiblePicks.addAll(deterministicPicks);
            if (totalPossiblePicks.size() > simultaneousPicks) break;
        }
        if (!undeterministicPicks && totalPossiblePicks.size() > 0 && totalPossiblePicks.size() <= simultaneousPicks) {
            Player nextPlayer = draftManager.getGame().getPlayer(getCurrentPlayer(playerOrder));
            DraftChoice forcedPick = totalPossiblePicks.get(0);
            Draftable forcedDraftable = draftManager.getDraftableByType(forcedPick.getType());
            String status = draftManager.routeCommand(
                    event,
                    nextPlayer,
                    forcedDraftable.makeCommandKey(forcedPick.getChoiceKey()),
                    DraftManager.CommandSource.AUTO_PICK);
            DraftButtonService.handleButtonResult(event, status);
        } else {
            PublicDraftInfoService.edit(
                    event,
                    draftManager,
                    playerOrder,
                    getCurrentPlayer(playerOrder),
                    getNextPlayer(playerOrder),
                    choice.getType());
            PublicDraftInfoService.pingCurrentPlayer(
                    draftManager,
                    getCurrentPlayer(playerOrder),
                    List.of(),
                    List.of(),
                    List.of(getReprintDraftButton()));
        }

        PartialMapService.tryUpdateMap(event, draftManager);

        return DraftButtonService.DELETE_BUTTON;
    }

    @Override
    public String save() {
        return currentPlayerIndex + DraftOrchestrator.SAVE_SEPARATOR + isReversing;
    }

    @Override
    public void load(String data) {
        String[] tokens = data.split(DraftOrchestrator.SAVE_SEPARATOR, 2);
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
        for (Map.Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState playerState = entry.getValue();
            OrchestratorState orchestratorState = playerState.getOrchestratorState();
            if (!(orchestratorState instanceof State)) {
                continue;
            }
            State state = (State) orchestratorState;
            playerStates[i++] = playerUserId + DraftOrchestrator.SAVE_SEPARATOR + state.getOrderIndex();
        }
        return playerStates;
    }

    @Override
    public PlayerOrchestratorState loadPlayerState(String data) {
        String[] tokens = data.split(DraftOrchestrator.SAVE_SEPARATOR, 2);
        String playerUserId = tokens[0];
        int orderIndex = Integer.parseInt(tokens[1]);
        State state = new State();
        state.setOrderIndex(orderIndex);
        return new PlayerOrchestratorState(playerUserId, state);
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (currentPlayerIndex < 0
                || currentPlayerIndex >= draftManager.getPlayerStates().size()) {
            return "Current player index is out of bounds: " + currentPlayerIndex;
        }
        // Ensure all players have a valid State, with unique and valid order indices.
        Set<Integer> distinctOrderIndices = new HashSet<>();
        for (Map.Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
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
        return Buttons.gray(
                DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + getButtonPrefix() + "reprintdraft",
                "Show draft again");
    }

    @Override
    public String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {

        if (buttonId.equals("reprintdraft")) {
            List<String> playerOrder = getPlayerOrder(draftManager);
            PublicDraftInfoService.send(
                    draftManager,
                    playerOrder,
                    getCurrentPlayer(playerOrder),
                    getNextPlayer(playerOrder),
                    List.of(getReprintDraftButton()));
            return null;
        }

        return "Unknown button action: " + buttonId;
    }

    @Override
    public String whatsStoppingDraftEnd(DraftManager draftManager) {
        // This draft mode doesn't impose any additional requirements beyond what the
        // draftables require.
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        // This draft mode doesn't do any player setup itself, the draftables handle
        // everything.
        return null;
    }

    public List<String> getPlayerOrder(DraftManager draftManager) {
        List<String> playerOrder = new ArrayList<>();
        int numPlayers = draftManager.getPlayerStates().size();
        for (int i = 0; i < numPlayers; i++) {
            for (String playerUserId : draftManager.getPlayerStates().keySet()) {
                State orchestratorState =
                        (State) draftManager.getPlayerStates().get(playerUserId).getOrchestratorState();
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
