package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class ActionCardModel implements ModelInterface, EmbeddableModel {

    private String alias;
    private String name;
    private String phase;
    private String window;
    private String text;
    private String flavorText;
    private String imageURL;
    private String automationID;
    private ComponentSource source;
    private ComponentSource actualSource;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null && name != null && phase != null && window != null && text != null && source != null;
    }

    public String getNameRepresentation() {
        return CardEmojis.ActionCard + "_" + name + "_";
    }

    public String getRepresentation() {
        return getRepresentationJustName() + " - " + window + ": " + text + "\n";
    }

    public String getRepresentationJustName() {
        return CardEmojis.ActionCard + "_" + name + "_";
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
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = CardEmojis.ActionCard + "__**" + name + "**__"
                + source.emoji();
        eb.setTitle(title);

        // DESCRIPTION
        eb.setDescription(phase + " Phase\n***" + window + ":***\n" + text);

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
}
