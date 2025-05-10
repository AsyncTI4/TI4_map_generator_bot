package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TI4Emoji;

@Data
public class PublicObjectiveModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String phase;
    private String text;
    private Integer points;
    private String homebrewReplacesID;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && text != null
            && source != null;
    }

    @JsonIgnore
    public TI4Emoji getObjectiveEmoji() {
        return CardEmojis.getObjectiveEmoji(Integer.toString(points));
    }

    @JsonIgnore
    public String getRepresentation() {
        return getRepresentation(true);
    }

    @JsonIgnore
    public String getRepresentation(boolean vps) {
        return getObjectiveEmoji() + "_" + name + "_ - " + text + (vps ? " (" + points + " VP)" : "");
    }

    public static final Comparator<PublicObjectiveModel> sortByPointsAndName = (po1, po2) -> {
        if (Objects.equals(po1.getPoints(), po2.getPoints())) {
            return po1.getName().compareTo(po2.getName());
        } else {
            return po1.getPoints() < po2.getPoints() ? -1 : 1;
        }
    };

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        String title = getObjectiveEmoji() + "__**" + getName() + "**__" + getSource().emoji();
        eb.setTitle(title);

        //DESCRIPTION
        eb.setDescription(getText());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());

        eb.setColor(getEmbedColor());
        return eb.build();
    }

    public Color getEmbedColor() {
        return switch (getPoints()) {
            case 1 -> Color.ORANGE;
            case 2 -> Color.BLUE;
            default -> Color.WHITE;
        };
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getSource() + ")";
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }
}
