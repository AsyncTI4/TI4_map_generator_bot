package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.map.Game;

@UtilityClass
public class DraftLoadService {

    public DraftManager loadDraftManager(Game game, String draftDataJoined) {

        List<String> draftData = DraftSaveService.splitLines(draftDataJoined);

        DraftOrchestrator orchestrator = null;
        List<Draftable> draftables = new ArrayList<>();
        List<String> playerUserIds = new ArrayList<>();

        // Re-initialize classes
        String playersKey = DraftSaveService.PLAYER_DATA + DraftSaveService.KEY_SEPARATOR;
        String orchestratorKey = DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR;
        String draftableKey = DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR;
        Map<String, String> shortIdTouserId = new HashMap<>();
        for (String data : draftData) {
            if (data.startsWith(playersKey)) {
                String playerIdsStr = data.substring(playersKey.length());
                String[] playerIds = playerIdsStr.split("\\" + DraftSaveService.DATA_SEPARATOR);
                for (String playerIdEntry : playerIds) {
                    String[] tokens = playerIdEntry.split(",", 2);
                    if (tokens.length == 2) {
                        String userId = tokens[0];
                        String shortId = tokens[1];
                        playerUserIds.add(userId);
                        shortIdTouserId.put(shortId, userId);
                    } else {
                        // This shouldn't happen in normal use, but just in case try to run with it
                        playerUserIds.add(playerIdEntry);
                    }
                }
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
        String playerChoiceKey = DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR;
        String playerOrchestratorStateKey =
                DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR;
        for (String data : draftData) {
            if (data.startsWith(playerChoiceKey)) {
                String[] tokens =
                        data.substring(playerChoiceKey.length()).split("\\" + DraftSaveService.DATA_SEPARATOR, 3);
                String playerUserId = tokens[0];
                if (shortIdTouserId.containsKey(playerUserId)) {
                    playerUserId = shortIdTouserId.get(playerUserId);
                }
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
                    String[] tokens = data.substring(playerOrchestratorStateKey.length())
                            .split("\\" + DraftSaveService.DATA_SEPARATOR, 2);

                    String playerUserId = tokens[0];
                    OrchestratorState playerOrchestratorState = orchestrator.loadPlayerState(tokens[1]);

                    if (shortIdTouserId.containsKey(playerUserId)) {
                        playerUserId = shortIdTouserId.get(playerUserId);
                    }
                    PlayerDraftState playerState =
                            draftManager.getPlayerStates().get(playerUserId);
                    playerState.setOrchestratorState(playerOrchestratorState);
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
