package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import ti4.helpers.Constants;

public class Leader {
    private final String id;
    private String type = null;
    private int tgCount = 0;
    private boolean exhausted = false;
    private boolean locked = true;
    private boolean active = false;

    @JsonCreator
    public Leader(@JsonProperty("id") String id,
                  @JsonProperty("type") String type,
                  @JsonProperty("tgCount") int tgCount,
                  @JsonProperty("exhausted") boolean exhausted,
                  @JsonProperty("locked") boolean locked,
                  @JsonProperty("active") boolean active) {
        this.id = id;
        this.type = type;
        this.tgCount = tgCount;
        this.exhausted = exhausted;
        this.locked = locked;
        this.active = active;
    }

    public Leader(String id) {
        this.id = id;
        if (id.contains(Constants.AGENT)) {
            locked = false;
            type = Constants.AGENT;
        } else if (id.contains(Constants.COMMANDER)) {
            type = Constants.COMMANDER;
        } else if (id.contains(Constants.HERO)) {
            type = Constants.HERO;
        } else if (id.contains(Constants.ENVOY)) {
            type = Constants.ENVOY;
        }
    }

    public String getId() {
        return id;
    }
    
    public String getType() {
        return type;
    }

    public int getTgCount() {
        return tgCount;
    }

    public void setTgCount(int tgCount) {
        this.tgCount = tgCount;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public void setExhausted(boolean exhausted) {
        this.exhausted = exhausted;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
