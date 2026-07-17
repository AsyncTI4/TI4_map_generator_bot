package ti4.website.model;

import java.util.List;
import lombok.Data;

@Data
class WebEntityData {
    private String entityId;
    // "unit" or "token"
    private String entityType;
    private int count;
    // Optional - only for units that can sustain damage
    private Integer sustained;
    // Optional - unit state counts: [healthy, damaged, galvanized, damaged+galvanized]
    private List<Integer> unitStates;

    public WebEntityData(String entityId, String entityType, int count) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.count = count;
    }

    public WebEntityData(String entityId, String entityType, int count, Integer sustained) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.count = count;
        this.sustained = sustained;
    }

    public WebEntityData(String entityId, String entityType, int count, Integer sustained, List<Integer> unitStates) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.count = count;
        this.sustained = sustained;
        this.unitStates = unitStates;
    }
}
