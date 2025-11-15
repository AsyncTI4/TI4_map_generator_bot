package ti4.service.draft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.helpers.StringHelper;

@UtilityClass
public class DraftSaveService {
    public static final String KEY_SEPARATOR = ":";
    public static final String DATA_SEPARATOR = "|";
    public static final char ENCODED_DATA_SEPARATOR = '&';
    public static final String PLAYER_DATA = "p";
    public static final String ORCHESTRATOR_DATA = "o";
    public static final String DRAFTABLE_DATA = "d";
    public static final String PLAYER_PICK_DATA = "pp";
    public static final String PLAYER_ORCHESTRATOR_STATE_DATA = "po";

    public String saveDraftManager(DraftManager draftManager) {
        List<String> lines = new ArrayList<>();

        // Save player user IDs, and create a shorthand for each ID
        Map<String, String> userIdToShortId = new HashMap<>();
        if (!draftManager.getPlayerStates().isEmpty()) {
            int index = 0;
            List<String> playerSaveList = new ArrayList<>();
            for (String userId : draftManager.getPlayerStates().keySet()) {
                String shortId = "u" + index;
                index++;
                userIdToShortId.put(userId, shortId);
                playerSaveList.add(userId + "," + shortId);
            }
            lines.add(PLAYER_DATA + KEY_SEPARATOR + String.join(DATA_SEPARATOR, playerSaveList));
        }

        // Save orchestrator
        if (draftManager.getOrchestrator() != null) {
            lines.add(ORCHESTRATOR_DATA
                    + KEY_SEPARATOR
                    + draftManager.getOrchestrator().getClass().getSimpleName()
                    + DATA_SEPARATOR
                    + draftManager.getOrchestrator().save());
        }

        // Save draftables
        for (Draftable draftable : draftManager.getDraftables()) {
            lines.add(DRAFTABLE_DATA
                    + KEY_SEPARATOR
                    + draftable.getClass().getSimpleName()
                    + DATA_SEPARATOR
                    + draftable.save());
        }

        // Save player picks
        for (var entry : draftManager.getPlayerStates().entrySet()) {
            String userId = entry.getKey();
            // If we get data for a user that wasn't in the original player list, preserve the broken data for debugging
            // by keeping the full user ID
            String shortId = userIdToShortId.getOrDefault(userId, userId);
            PlayerDraftState state = entry.getValue();

            // Save draft choices
            var sortedTypes = state.getPicks().keySet().stream()
                    .sorted(Comparator.comparing(DraftableType::toString))
                    .toList();
            for (var draftableTypes : sortedTypes) {
                List<DraftChoice> choiceListValues = state.getPicks().get(draftableTypes);
                var sortedPicks = choiceListValues.stream()
                        .sorted(Comparator.comparing(DraftChoice::getChoiceKey))
                        .toList();
                for (DraftChoice choice : sortedPicks) {
                    lines.add(PLAYER_PICK_DATA
                            + KEY_SEPARATOR
                            + shortId
                            + DATA_SEPARATOR
                            + choice.getType()
                            + DATA_SEPARATOR
                            + choice.getChoiceKey());
                }
            }
        }
        if (draftManager.getOrchestrator() != null) {
            for (Map.Entry<String, PlayerDraftState> entry :
                    draftManager.getPlayerStates().entrySet()) {
                String userId = entry.getKey();
                // If we get data for a user that wasn't in the original player list, preserve the broken data for
                // debugging by keeping the full user ID
                String shortId = userIdToShortId.getOrDefault(userId, userId);
                PlayerDraftState state = entry.getValue();
                if (state.getOrchestratorState() != null) {
                    String orchestratorState =
                            draftManager.getOrchestrator().savePlayerState(state.getOrchestratorState());
                    lines.add(PLAYER_ORCHESTRATOR_STATE_DATA
                            + KEY_SEPARATOR
                            + shortId
                            + DATA_SEPARATOR
                            + orchestratorState);
                }
            }
        }

        // Reduce footprint of save data by concatenating lines into a single line
        String joinedLines = StringHelper.safeJoin(lines, ENCODED_DATA_SEPARATOR);

        return joinedLines;
    }
}
