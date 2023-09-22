package ti4.model;

import java.awt.Color;


import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;

@Data
public class RelicModel implements ModelInterface {
    private String alias;
    private String name;
    private String shortName;
    private String text;
    private String flavourText;
    private String source;

    @Override
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

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();
        String name = getName() == null ? "" : getName();
        eb.setTitle(Emojis.Relic + "__" + name + "__" + getSourceEmoji(), null);
        eb.setColor(Color.yellow);
        eb.setDescription(getText());
        if (includeID) eb.setFooter("ID: " + getAlias() + "  Source: " + getSource());
        return eb.build();
    }
}
