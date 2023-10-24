package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

@Data
public class SecretObjectiveModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String phase;
    private String text;
    private int points;
    private String source;
    private List<String> searchTags = new ArrayList<>();

  public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && text != null
            && points != 0
            && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getPhase() { 
        return phase;
    }

    public String getText() { 
        return text;
    }

    public int getPoints() { 
        return points;
    }

    public String getSource() { 
        return source;
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
        StringBuilder title = new StringBuilder();
        title.append(Emojis.SecretObjective);
        title.append("__**").append(getName()).append("**__");
        title.append(getSourceEmoji());
        eb.setTitle(title.toString());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        description.append(getText());
        eb.setDescription(description.toString());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());
        
        eb.setColor(getEmbedColour());
        return eb.build();
    }

    private String getSourceEmoji() {
        return switch (getSource()) {
            default -> "";
        };
    }

    public Color getEmbedColour() {
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
}
