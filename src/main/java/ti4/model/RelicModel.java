package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;

@Data
public class RelicModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String shortName;
    private String text;
    private String flavourText;
    private String source;
    private Boolean isFakeRelic;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null 
            && name != null 
            && text != null 
            && source != null;
    }

    public String getSimpleRepresentation() {
        return getSourceEmoji() + String.format("**%s**: %s *(%s)*", getName(), getText(), getSource());
    }

    public String getSourceEmoji() {
        return switch (source) {
            case "absol" -> Emojis.Absol;
            case "ds" -> Emojis.DiscordantStars;
            default -> "";
        };
    }

    /**
     * @return whether this object is implemented as a relic, but is not actually a relic
     */
    public boolean isFakeRelic() {return getIsFakeRelic();}
    private boolean getIsFakeRelic() {
        return Optional.ofNullable(isFakeRelic).orElse(false);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText) {
        EmbedBuilder eb = new EmbedBuilder();
        String name = getName() == null ? "" : getName();
        eb.setTitle(Emojis.Relic + "__" + name + "__" + getSourceEmoji(), null);
        eb.setColor(Color.yellow);
        eb.setDescription(getText());
        if (includeFlavourText && getFlavourText() != null) eb.addField("", "*" + getFlavourText() + "*", false);
        if (includeID) eb.setFooter("ID: " + getAlias() + "  Source: " + getSource());
        return eb.build();
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString) || getText().toLowerCase().contains(searchString) || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getSource() + ")";
    }
}
