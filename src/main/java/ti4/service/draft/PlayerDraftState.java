package ti4.service.draft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PlayerDraftState {
    private final Map<DraftableType, List<DraftChoice>> picks = new HashMap<>();

    private OrchestratorState orchestratorState;
}
