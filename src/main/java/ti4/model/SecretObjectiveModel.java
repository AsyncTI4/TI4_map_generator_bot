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
public class SecretObjectiveModel implements ColorableModelInterface<SecretObjectiveModel>, EmbeddableModel {
    private String alias;
    private String name;
    private String phase;
    private String text;
    private int points;
    private String homebrewReplacesID;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();
    private SecretObjectiveModel sourceModel; // used for duped promissory notes, to know their source

    /**
     * @return true if this is duplicated from a generic colour promissory note
     */
    public boolean isDupe() {
        return sourceModel != null;
    }

    public boolean isColorable() {
        return alias != null && alias.contains("<color>");
    }

    @Override
    public SecretObjectiveModel duplicateAndSetColor(ColorModel newColor) {
        SecretObjectiveModel so = new SecretObjectiveModel();
        so.setAlias(alias.replace("<color>", newColor.getName()));
        so.setName(name.replace("<color>", newColor.getDisplayName()));
        so.setPhase(phase);
        so.setText(text.replace("<color>", newColor.getName()));
        so.setPoints(points);
        so.setHomebrewReplacesID(homebrewReplacesID);
        so.setImageURL(imageURL);
        so.setSource(source);
        so.setSearchTags(new ArrayList<>(searchTags));
        so.setSourceModel(this);
        return so;
    }

    public boolean isValid() {
        return alias != null && name != null && phase != null && text != null && points != 0 && source != null;
    }

    public static final Comparator<SecretObjectiveModel> sortByPointsAndName = (po1, po2) -> {
        if (po1.points == po2.points) {
            return po1.name.compareTo(po2.name);
        } else {
            return po1.points < po2.points ? -1 : 1;
        }
    };

    public String getRepresentation() {
        return getRepresentation(true);
    }

    public String getRepresentation(boolean vps) {
        return CardEmojis.SecretObjective + "_" + name + "_ - " + text + (vps ? " (" + points + " VP)" : "");
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = CardEmojis.SecretObjective + "__**" + name + "**__" + source.emoji();
        eb.setTitle(title);

        // DESCRIPTION
        eb.setDescription(text);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("ID: ").append(alias).append("    Source: ").append(source);
        eb.setFooter(footer.toString());

        eb.setColor(getEmbedColor());
        return eb.build();
    }

    public Color getEmbedColor() {
        return switch (points) {
            case 2 -> Color.BLACK;
            default -> Color.RED;
        };
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " (" + source + ")";
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }
}
