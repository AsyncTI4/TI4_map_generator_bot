package ti4.map;

import java.awt.*;
import java.util.Comparator;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.model.LeaderModel;

public class Leader {
    private final String id;
    private String type;
    private int tgCount;
    private boolean exhausted;
    private boolean locked = true;
    private boolean active;

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

    @JsonIgnore
    public Optional<LeaderModel> getLeaderModel() {
        return Optional.ofNullable(Mapper.getLeader(getId()));
    }

    @JsonIgnore
    public static Comparator<Leader> sortByType() {
        return Comparator.comparing(Leader::getType);
    }

    @JsonIgnore
    public MessageEmbed getLeaderEmbed() {
        if (getLeaderModel().isEmpty()) {
            return null;
        }
        EmbedBuilder eb = new EmbedBuilder();
        MessageEmbed modelEmbed = getLeaderModel().get().getRepresentationEmbed(false, false, isLocked(), false);
        eb.copyFrom(modelEmbed);

        if (getTgCount() > 0) {
            String desc = modelEmbed.getDescription();
            eb.setDescription(desc + "\n" + StringUtils.repeat(Emojis.tg, getTgCount()));
        }

        if (isExhausted()) {
            eb.setColor(Color.GRAY);
        } else {
            eb.setColor(Color.GREEN);
        }

        if (isLocked()) {
            eb.setColor(Color.RED);
            eb.setAuthor("🔒 Locked");
        }
        
        if (isActive()) {
            eb.setColor(Color.BLUE);
            eb.setAuthor("🔒 ACTIVE - Leader will be purged during status phase cleanup");
        }

        return eb.build();
    }
}
