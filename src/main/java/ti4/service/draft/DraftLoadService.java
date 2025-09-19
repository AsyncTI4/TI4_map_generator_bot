package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.service.draft.DraftOrchestrator.PlayerOrchestratorState;

@UtilityClass
public class DraftLoadService {

    public DraftManager loadDraftManager(Game game, List<String> draftData) {
        DraftOrchestrator orchestrator = null;
        List<Draftable> draftables = new ArrayList<>();
        List<String> playerUserIds = new ArrayList<>();

        // Re-initialize classes
        String playersKey = "players" + DraftSaveService.KEY_SEPARATOR;
        String orchestratorKey = "orchestrator" + DraftSaveService.KEY_SEPARATOR;
        String draftableKey = "draftable" + DraftSaveService.KEY_SEPARATOR;
        for (String data : draftData) {
            if (data.startsWith(playersKey)) {
                playerUserIds =
                        List.of(data.substring(playersKey.length()).split("\\" + DraftSaveService.DATA_SEPARATOR));
            } else if (data.startsWith(orchestratorKey)) {
                orchestrator = loadOrchestrator(data.substring(orchestratorKey.length()));
            } else if (data.startsWith(draftableKey)) {
                Draftable draftable = loadDraftable(data.substring(draftableKey.length()));
                draftables.add(draftable);
            }
        }

        DraftManager draftManager = new DraftManager(game);
        if (!playerUserIds.isEmpty()) {
            draftManager.setPlayers(playerUserIds);
        }
        if (orchestrator != null) {
            draftManager.setOrchestrator(orchestrator);
        }
        if (!draftables.isEmpty()) {
            draftManager.getDraftables().addAll(draftables);
        }

        // Setup player states
        String playerChoiceKey = "playerchoice" + DraftSaveService.KEY_SEPARATOR;
        String playerOrchestratorStateKey = "playerorchestratorstate" + DraftSaveService.KEY_SEPARATOR;
        for (String data : draftData) {
            if (data.startsWith(playerChoiceKey)) {
                String[] tokens =
                        data.substring(playerChoiceKey.length()).split("\\" + DraftSaveService.DATA_SEPARATOR, 3);
                String playerUserId = tokens[0];
                DraftableType draftableType = DraftableType.of(tokens[1]);
                String choiceKey = tokens[2];
                DraftChoice choice = loadDraftChoice(draftables, draftableType, choiceKey);
                PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
                playerState
                        .getPicks()
                        .computeIfAbsent(choice.getType(), k -> new ArrayList<>())
                        .add(choice);
            } else if (data.startsWith(playerOrchestratorStateKey)) {
                if (orchestrator != null) {
                    PlayerOrchestratorState playerOrchestratorState =
                            orchestrator.loadPlayerState(data.substring(playerOrchestratorStateKey.length()));
                    PlayerDraftState playerState =
                            draftManager.getPlayerStates().get(playerOrchestratorState.playerUserId());
                    playerState.setOrchestratorState(playerOrchestratorState.state());
                } else {
                    throw new IllegalStateException(
                            "Orchestrator must be set before loading player orchestrator states");
                }
            }
        }

        return draftManager;
    }

    private DraftOrchestrator loadOrchestrator(String data) {
        String[] orchestratorTokens = data.split("\\" + DraftSaveService.DATA_SEPARATOR, 2);
        if (orchestratorTokens.length != 2) {
            throw new IllegalArgumentException("Invalid orchestrator data: " + data);
        }
        String className = orchestratorTokens[0];
        String orchestratorData = orchestratorTokens[1];

        DraftOrchestrator orchestrator = DraftComponentFactory.createOrchestrator(className);
        if (orchestrator != null) {
            orchestrator.load(orchestratorData);
            return orchestrator;
        }

        throw new IllegalArgumentException("Unknown orchestrator type: " + className);
    }

    private Draftable loadDraftable(String data) {
        String[] draftableTokens = data.split("\\" + DraftSaveService.DATA_SEPARATOR, 2);
        if (draftableTokens.length != 2) {
            throw new IllegalArgumentException("Invalid draftable data: " + data);
        }
        String className = draftableTokens[0];
        String draftableData = draftableTokens[1];

        Draftable draftable = DraftComponentFactory.createDraftable(className);
        if (draftable != null) {
            draftable.load(draftableData);
            return draftable;
        }

        throw new IllegalArgumentException("Unknown draftable type: " + className);
    }

    private DraftChoice loadDraftChoice(List<Draftable> draftables, DraftableType draftableType, String choiceKey) {
        for (Draftable draftable : draftables) {
            if (draftable.getType().equals(draftableType)) {
                for (DraftChoice choice : draftable.getAllDraftChoices()) {
                    if (choice.getChoiceKey().equals(choiceKey)) {
                        return choice;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Draft choice not found: " + draftableType + "," + choiceKey);
    }
}
