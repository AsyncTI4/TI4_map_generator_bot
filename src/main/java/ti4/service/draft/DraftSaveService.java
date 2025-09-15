package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DraftSaveService {
    public List<String> saveDraftManager(DraftManager draftManager) {
        List<String> lines = new ArrayList<>();

        // Save player user IDs
        lines.add("players:" + String.join(",", draftManager.getPlayerStates().keySet()));

        // Save orchestrator
        lines.add("orchestrator:" + draftManager.getOrchestrator().getClass().getName() + ":"
                + draftManager.getOrchestrator().save());

        // Save draftables
        for (Draftable draftable : draftManager.getDraftables()) {
            lines.add("draftable:" + draftable.getClass().getName() + ":" + draftable.save());
        }

        // Save player picks
        for (var entry : draftManager.getPlayerStates().entrySet()) {
            String userId = entry.getKey();
            PlayerDraftState state = entry.getValue();

            // Save draft choices
            for (var choiceListValues : state.getPicks().values()) {
                for (DraftChoice choice : choiceListValues) {
                    lines.add("playerchoice:" + userId + "," + choice.getType() + "," + choice.getChoiceKey());
                }
            }
        }
        String[] orchestratorPlayerStates = draftManager.getOrchestrator().savePlayerStates(draftManager);
        for (String orchestratorState : orchestratorPlayerStates) {
            lines.add("playerorchestratorstate:" + orchestratorState);
        }

        return lines;
    }
}
