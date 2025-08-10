package ti4.website.model;

import lombok.Data;

@Data
public class WebEntityData {
    private String entityId;
    private String entityType; // "unit" or "token"
    private int count;
    private Integer sustained; // Optional - only for units that can sustain damage

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
}
