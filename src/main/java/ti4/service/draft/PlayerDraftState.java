package ti4.service.draft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PlayerDraftState {
    // private String faction;
    // private MiltyDraftSlice slice;
    // private Integer position;

    private Map<DraftableType, List<DraftChoice>> picks = new HashMap<>();

    public abstract static class OrchestratorState {
        // Empty base class for extension by orchestrators
    }

    private OrchestratorState orchestratorState;

    // String summary(String doggy) {
    //     return String.join(" ", factionEmoji(doggy), sliceEmoji(), positionEmoji());
    // }

    // private String factionEmoji(String doggy) {
    //     return faction == null
    //             ? doggy
    //             : FactionEmojis.getFactionIcon(faction).toString();
    // }

    // private String sliceEmoji() {
    //     String ord = slice == null ? null : slice.getName();
    //     return MiltyDraftEmojis.getMiltyDraftEmoji(ord).toString();
    // }

    // private String positionEmoji() {
    //     int ord = position == null ? -1 : position;
    //     return MiltyDraftEmojis.getSpeakerPickEmoji(ord).toString();
    // }

    // @JsonIgnore
    // String save() {
    //     String factionStr = faction == null ? "null" : faction;
    //     String sliceStr = slice == null ? "null" : slice.getName();
    //     String orderStr = position == null ? "null" : Integer.toString(position);
    //     return String.join(",", factionStr, sliceStr, orderStr);
    // }
}
