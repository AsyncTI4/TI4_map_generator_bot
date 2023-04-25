package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import ti4.helpers.Constants;

public class Leader {
    private final String id;
    private final String name;
    private int tgCount = 0;
    private boolean exhausted = false;
    private boolean locked = true;
    private boolean active = false;

    @JsonCreator
    public Leader(@JsonProperty("id") String id,
                  @JsonProperty("name") String name,
                  @JsonProperty("tgCount") int tgCount,
                  @JsonProperty("exhausted") boolean exhausted,
                  @JsonProperty("locked") boolean locked,
                  @JsonProperty("active") boolean active) {
        this.id = id;
        this.name = name;
        this.tgCount = tgCount;
        this.exhausted = exhausted;
        this.locked = locked;
        this.active = active;
    }

    public Leader(String id, String name) {
        this.id = id;
        if (Constants.AGENT.equals(id)){
            locked = false;
        }
        if (!name.equals(".")) {
            this.name = name;
        } else {
            this.name = "";
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
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
