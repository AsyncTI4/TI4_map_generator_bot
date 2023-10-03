package ti4.model;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;

public class ActionCardModel implements ModelInterface {
    private String alias;
    private String name;
    private String phase;
    private String window;
    private String text;
    private String flavorText;
    private String source;

    public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && window != null
            && text != null
            && flavorText != null
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

    public String getWindow() {
        return window;
    }

    public String getText() {
        return text;
    }

    public String getFlavorText() {
        return flavorText;
    }

    public String getSource() {
        return source;
    }

    public String getRepresentation() {
        return Emojis.ActionCard + "__**" + name + "**__" + " *(" + phase + " Phase)*: " +
            "_" + window + ":_ " + text + "\n";
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(Emojis.ActionCard);
        title.append("__**").append(getName()).append("**__");
        title.append(getSourceEmoji());
        eb.setTitle(title.toString());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        description.append("***").append(getWindow()).append(":***\n").append(getText());
        eb.setDescription(description.toString());

        //FLAVOUR TEXT
        if (includeFlavourText && getFlavorText() != null) eb.addField("", "*" + getFlavorText() + "*", true);

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());
        
        eb.setColor(Color.orange);
        return eb.build();
    }

    private String getSourceEmoji() {
        return switch (getSource()) {
            case "ds" -> Emojis.DiscordantStars;
            case "action_deck_2" -> Emojis.ActionDeck2;
            default -> "";
        };
    }
}
