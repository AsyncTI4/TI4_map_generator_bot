package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class ActionCardModel implements ModelInterface, EmbeddableModel {

    private String alias;
    private String name;
    private String phase;
    private String window;
    private String text;
    private String notes;
    private String flavorText;
    private String imageURL;
    private String automationID;
    private ComponentSource source;
    private ComponentSource actualSource;
    private List<String> searchTags = new ArrayList<>();
    private boolean affectedByWildWildGalaxy;

    public boolean isValid() {
        return alias != null && name != null && phase != null && window != null && text != null && source != null;
    }

    public String getNameRepresentation(Game game) {
        return CardEmojis.getACEmoji(game) + (isWild(game) ? "" + CardEmojis.Event : "") + "_" + name + "_";
    }

    public String getNameRepresentation() {
        return getNameRepresentation(null);
    }

    public String getRepresentation(Game game) {
        return getNameRepresentation(game) + " - " + window + ": " + text + "\n";
    }

    public String getRepresentation() {
        return getRepresentation(null);
    }

    public String getRepresentationJustText() {
        return window + ": " + text;
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public String getAutomationID() {
        if (automationID == null) return alias;
        return automationID;
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText) {
        return getRepresentationEmbed(includeID, includeFlavourText, null);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText, Game game) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = CardEmojis.getACEmoji(game) + (isWild(game) ? "" + CardEmojis.Event : "") + "__**" + name
                + "**__" + source.emoji();
        eb.setTitle(title);

        // DESCRIPTION
        if (notes == null) {
            eb.setDescription("\n***" + window + ":***\n" + text);
        } else {
            eb.setDescription("\n***" + window + ":***\n" + text + "\n-# [" + notes + "]");
        }

        // FLAVOUR TEXT
        if (includeFlavourText && getFlavorText().isPresent())
            eb.addField("", "*" + getFlavorText().get() + "*", true);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("ID: ").append(alias).append("    Source: ").append(source);
        eb.setFooter(footer.toString());

        eb.setColor(Color.orange);
        return eb.build();
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " (" + source + ")";
    }

    public Optional<String> getFlavorText() {
        return Optional.ofNullable(flavorText);
    }

    public boolean isWild(Game game) {
        return (game != null) && Optional.of(affectedByWildWildGalaxy).orElse(false) && game.isWildWildGalaxyMode();
    }
}
