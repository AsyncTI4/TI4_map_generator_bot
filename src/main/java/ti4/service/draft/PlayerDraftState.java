package ti4.service.draft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PlayerDraftState {
    private final Map<DraftableType, List<DraftChoice>> picks = new HashMap<>();

    /// Helpers
    
    public Integer getPickCount(DraftableType type) {
        List<DraftChoice> typePicks = picks.get(type);
        return typePicks == null ? 0 : typePicks.size();
    }

    public List<DraftChoice> getPicks(DraftableType type) {
        return picks.get(type);
    }

    private OrchestratorState orchestratorState;
}
