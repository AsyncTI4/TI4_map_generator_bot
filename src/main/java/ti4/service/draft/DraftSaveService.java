package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DraftSaveService {
    public static final String KEY_SEPARATOR = ":";
    public static final String DATA_SEPARATOR = "|";
    public static final char ENCODED_DATA_SEPARATOR = '&';
    private static final char ESCAPE_CHARACTER = '\\';

    public static String encodeLine(String line) {
        return line.replace(
                String.valueOf(ENCODED_DATA_SEPARATOR),
                String.valueOf(ESCAPE_CHARACTER) + String.valueOf(ENCODED_DATA_SEPARATOR));
    }

    public static String decodeLine(String line) {
        return line.replace(
                String.valueOf(ESCAPE_CHARACTER) + String.valueOf(ENCODED_DATA_SEPARATOR),
                String.valueOf(ENCODED_DATA_SEPARATOR));
    }

    public static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : lines) {
            if (first) {
                first = false;
            } else {
                sb.append(ENCODED_DATA_SEPARATOR);
            }
            line = line.replace(
                    String.valueOf(ESCAPE_CHARACTER),
                    String.valueOf(ESCAPE_CHARACTER) + String.valueOf(ESCAPE_CHARACTER));
            line = line.replace(
                    String.valueOf(ENCODED_DATA_SEPARATOR),
                    String.valueOf(ESCAPE_CHARACTER) + String.valueOf(ENCODED_DATA_SEPARATOR));
            sb.append(line);
        }
        return sb.toString();
    }

    public static List<String> splitLines(String data) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        boolean escapeNext = false;

        for (char c : data.toCharArray()) {
            if (escapeNext) {
                currentLine.append(c);
                escapeNext = false;
            } else if (c == ESCAPE_CHARACTER) {
                escapeNext = true;
            } else if (c == ENCODED_DATA_SEPARATOR) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            } else {
                currentLine.append(c);
            }
        }
        // Add the last line if there's any content
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    public static final String PLAYER_DATA = "p";
    public static final String ORCHESTRATOR_DATA = "o";
    public static final String DRAFTABLE_DATA = "d";
    public static final String PLAYER_PICK_DATA = "pp";
    public static final String PLAYER_ORCHESTRATOR_STATE_DATA = "po";

    public String saveDraftManager(DraftManager draftManager) {
        List<String> lines = new ArrayList<>();

        // Save player user IDs, and create a shorthand for each ID
        Map<String, String> userIdToShortId = new HashMap<>();
        if (!draftManager.getPlayerStates().keySet().isEmpty()) {
            int index = 0;
            List<String> playerSaveList = new ArrayList<>();
            for (String userId : draftManager.getPlayerStates().keySet()) {
                String shortId = "u" + index++;
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
            for (var choiceListValues : state.getPicks().values()) {
                for (DraftChoice choice : choiceListValues) {
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
        String joinedLines = joinLines(lines);

        return joinedLines;
    }
}
