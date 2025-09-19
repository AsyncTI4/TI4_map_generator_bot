package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DraftSaveService {
    public static final String KEY_SEPARATOR = ":";
    public static final String DATA_SEPARATOR = "|";

    public List<String> saveDraftManager(DraftManager draftManager) {
        List<String> lines = new ArrayList<>();

        // Save player user IDs
        if (!draftManager.getPlayerStates().keySet().isEmpty()) {
            lines.add("players" + KEY_SEPARATOR
                    + String.join(DATA_SEPARATOR, draftManager.getPlayerStates().keySet()));
        }

        // Save orchestrator
        if (draftManager.getOrchestrator() != null) {
            lines.add("orchestrator" + KEY_SEPARATOR
                    + draftManager.getOrchestrator().getClass().getSimpleName() + DATA_SEPARATOR
                    + draftManager.getOrchestrator().save());
        }

        // Save draftables
        for (Draftable draftable : draftManager.getDraftables()) {
            lines.add("draftable" + KEY_SEPARATOR + draftable.getClass().getSimpleName() + DATA_SEPARATOR
                    + draftable.save());
        }

        // Save player picks
        for (var entry : draftManager.getPlayerStates().entrySet()) {
            String userId = entry.getKey();
            PlayerDraftState state = entry.getValue();

            // Save draft choices
            for (var choiceListValues : state.getPicks().values()) {
                for (DraftChoice choice : choiceListValues) {
                    lines.add("playerchoice" + KEY_SEPARATOR + userId + DATA_SEPARATOR + choice.getType()
                            + DATA_SEPARATOR + choice.getChoiceKey());
                }
            }
        }
        if (draftManager.getOrchestrator() != null) {
            String[] orchestratorPlayerStates = draftManager.getOrchestrator().savePlayerStates(draftManager);
            for (String orchestratorState : orchestratorPlayerStates) {
                lines.add("playerorchestratorstate:" + orchestratorState);
            }
        }

        return lines;
    }
}
