package ti4.website.model;

import java.util.List;
import lombok.Data;

@Data
class WebEntityData {
    private String entityId;
    private String entityType; // "unit" or "token"
    private int count;
    private Integer sustained; // Optional - only for units that can sustain damage
    private List<Integer>
            unitStates; // Optional - unit state counts: [healthy, damaged, galvanized, damaged+galvanized]

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
