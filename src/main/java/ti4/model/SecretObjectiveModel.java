package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class SecretObjectiveModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String phase;
    private String text;
    private int points;
    private String homebrewReplacesID;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && text != null
            && points != 0
            && source != null;
    }

    public static final Comparator<SecretObjectiveModel> sortByPointsAndName = (po1, po2) -> {
        if (po1.getPoints() == po2.getPoints()) {
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
        String title = CardEmojis.SecretObjective + "__**" + getName() + "**__" + getSource().emoji();
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
            case 2 -> Color.BLACK;
            default -> Color.RED;
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
