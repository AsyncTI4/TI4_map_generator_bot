package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

@UtilityClass
public class DraftLoadService {
    // For security reasons, make sure we only deserialize known types.
    private static final List<Class<? extends Draftable>> KNOWN_DRAFTABLE_TYPES = List.of(
            FactionDraftable.class, SliceDraftable.class, SeatDraftable.class, SpeakerOrderDraftable.class
            // Add other Draftable subclasses here as they are created.
            );
    private static final List<Class<? extends DraftOrchestrator>> KNOWN_ORCHESTRATOR_TYPES = List.of(
            PublicSnakeDraftOrchestrator.class
            // Add DraftOrchestrator subclasses here as they are created.
            );

    public DraftManager loadDraftManager(Game game, List<String> draftData) {
        DraftOrchestrator orchestrator = null;
        List<Draftable> draftables = new ArrayList<>();
        List<String> playerUserIds = new ArrayList<>();

        // Re-initialize classes
        for (String data : draftData) {
            if (data.startsWith("players:")) {
                playerUserIds = List.of(data.substring("players:".length()).split(","));
            } else if (data.startsWith("orchestrator:")) {
                orchestrator = loadOrchestrator(data);
            } else if (data.startsWith("draftable:")) {
                Draftable draftable = loadDraftable(data);
                draftables.add(draftable);
            }
        }

        DraftManager draftManager = new DraftManager(game);
        draftManager.setPlayers(playerUserIds);
        draftManager.setOrchestrator(orchestrator);
        draftManager.getDraftables().addAll(draftables);

        // Setup player states
        for (String data : draftData) {
            if (data.startsWith("playerchoice:")) {
                String[] tokens = data.substring("playerchoice:".length()).split(",");
                String playerUserId = tokens[0];
                DraftableType draftableType = DraftableType.of(tokens[1]);
                String choiceKey = tokens[2];
                DraftChoice choice = loadDraftChoice(draftables, draftableType, choiceKey);
                PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
                playerState
                        .getPicks()
                        .computeIfAbsent(choice.getType(), k -> new ArrayList<>())
                        .add(choice);
            } else if (data.startsWith("playerorchestratorstate:")) {
                String[] tokens =
                        data.substring("playerorchestratorstate:".length()).split(",");
                String playerUserId = tokens[0];
                String orchestratorStateData = tokens[1];
                PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
                if (orchestrator != null) {
                    OrchestratorState orchestratorState = orchestrator.loadPlayerState(orchestratorStateData);
                    playerState.setOrchestratorState(orchestratorState);
                }
            }
        }

        draftManager.validateState();

        return draftManager;
    }

    private DraftOrchestrator loadOrchestrator(String data) {
        String[] orchestratorTokens = data.split(":");
        if (orchestratorTokens.length != 3) {
            throw new IllegalArgumentException("Invalid orchestrator data: " + data);
        }
        String className = orchestratorTokens[1];
        String orchestratorData = orchestratorTokens[2];
        for (Class<? extends DraftOrchestrator> knownClass : KNOWN_ORCHESTRATOR_TYPES) {
            if (knownClass.getName().equals(className)) {
                try {
                    DraftOrchestrator orchestrator =
                            knownClass.getDeclaredConstructor().newInstance();
                    orchestrator.load(orchestratorData);
                    return orchestrator;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to load orchestrator of type " + className, e);
                }
            }
        }
        throw new IllegalArgumentException("Unknown orchestrator type: " + className);
    }

    private Draftable loadDraftable(String data) {
        String[] draftableTokens = data.split(":", 3);
        if (draftableTokens.length != 3) {
            throw new IllegalArgumentException("Invalid draftable data: " + data);
        }
        String className = draftableTokens[1];
        String draftableData = draftableTokens[2];
        for (Class<? extends Draftable> knownClass : KNOWN_DRAFTABLE_TYPES) {
            if (knownClass.getName().equals(className)) {
                try {
                    Draftable draftable = knownClass.getDeclaredConstructor().newInstance();
                    draftable.load(draftableData);
                    return draftable;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to load draftable of type " + className, e);
                }
            }
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
