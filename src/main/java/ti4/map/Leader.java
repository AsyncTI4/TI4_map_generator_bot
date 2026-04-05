package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.Color;
import java.util.Comparator;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;
import ti4.service.emoji.MiscEmojis;

@Getter
public class Leader {
    private final String id;
    private String type;

    @Setter
    private int tgCount;

    @Setter
    private boolean exhausted;

    @Setter
    private boolean locked = true;

    @Setter
    private boolean active;

    @JsonCreator
    public Leader(
            @JsonProperty("id") String id,
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

    @JsonIgnore
    public Optional<LeaderModel> getLeaderModel() {
        return Optional.ofNullable(Mapper.getLeader(id));
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
        MessageEmbed modelEmbed = getLeaderModel().get().getRepresentationEmbed(false, false, locked, false);
        eb.copyFrom(modelEmbed);

        if (tgCount > 0) {
            String desc = modelEmbed.getDescription();
            eb.setDescription(desc + "\n" + MiscEmojis.tg(tgCount));
        }

        if (exhausted) {
            eb.setColor(Color.GRAY);
        } else {
            eb.setColor(Color.GREEN);
        }

        if (locked) {
            eb.setColor(Color.RED);
            eb.setAuthor("🔒 Locked");
        }

        if (active) {
            eb.setColor(Color.BLUE);
            eb.setAuthor("🔒 ACTIVE - Leader will be purged during Status Phase cleanup");
        }

        return eb.build();
    }

    @JsonIgnore
    public MessageEmbed getLeaderEmbed(Game game) {
        if (getLeaderModel().isEmpty()) {
            return null;
        }
        EmbedBuilder eb = new EmbedBuilder();
        MessageEmbed modelEmbed =
                getLeaderModel().get().getRepresentationEmbed(false, false, locked, false, game.isTwilightsFallMode());
        eb.copyFrom(modelEmbed);

        if (tgCount > 0) {
            String desc = modelEmbed.getDescription();
            eb.setDescription(desc + "\n" + MiscEmojis.tg(tgCount));
        }

        if (exhausted) {
            eb.setColor(Color.GRAY);
        } else {
            eb.setColor(Color.GREEN);
        }

        if (locked) {
            eb.setColor(Color.RED);
            eb.setAuthor("🔒 Locked");
        }

        if (active) {
            eb.setColor(Color.BLUE);
            eb.setAuthor("🔒 ACTIVE - Leader will be purged during Status Phase cleanup");
        }

        return eb.build();
    }
}
