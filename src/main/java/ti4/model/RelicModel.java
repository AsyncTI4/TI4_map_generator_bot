package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.ExploreEmojis;

@Data
public class RelicModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private String text;
    private String flavourText;
    private String flavourTextFormatted;
    private Boolean isFakeRelic;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();
    private String homebrewReplacesID;

    public boolean isValid() {
        return alias != null
            && name != null
            && text != null
            && source != null;
    }

    public String getSimpleRepresentation() {
        return getSource().emoji() + String.format("_%s_ - %s (%s)", getName(), getText(), getSource());
    }

    /**
     * @return whether this object is implemented as a relic, but is not actually a relic
     */
    public boolean isFakeRelic() {
        return getIsFakeRelic();
    }

    public String getShortName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shortName).orElse(getName());
        }
        return Optional.ofNullable(shortName).orElse(Mapper.getRelic(getHomebrewReplacesID().get()).getShortName());
    }

    public boolean getShrinkName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shrinkName).orElse(false);
        }
        return Optional.ofNullable(shrinkName).orElse(Mapper.getRelic(getHomebrewReplacesID().get()).getShrinkName());
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    private boolean getIsFakeRelic() {
        return Optional.ofNullable(isFakeRelic).orElse(false);
    }

    public Optional<String> getImageURL() {
        return Optional.ofNullable(imageURL);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText) {
        EmbedBuilder eb = new EmbedBuilder();
        StringBuilder title = new StringBuilder();
        if (!isFakeRelic()) title.append(ExploreEmojis.Relic);
        title.append("__").append(getName()).append("__").append(getSource().emoji());
        eb.setTitle(title.toString(), null);

        eb.setDescription(getText());
        if (includeFlavourText && getFlavourText() != null) eb.addField("", getFlavourText(), false);

        // Colour
        if (isFakeRelic()) {
            eb.setColor(Color.gray);
        } else {
            eb.setColor(Color.yellow);
        }

        // getImageURL().ifPresent(eb::setImage);
        getImageURL().ifPresent(eb::setUrl);

        // Footer
        StringBuilder footer = new StringBuilder();
        if (includeID) {
            footer.append("ID: ").append(getAlias()).append("  Source: ").append(getSource());
        }
        if (isFakeRelic()) footer.append("\nNOTE: NOT ACTUALLY A RELIC");
        if (!footer.isEmpty()) eb.setFooter(footer.toString());

        return eb.build();
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getText().toLowerCase().contains(searchString)
            || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getSource() + ")";
    }
}
