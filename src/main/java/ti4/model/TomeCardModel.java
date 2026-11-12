package ti4.model;

import java.awt.Color;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;

@Data
public class TomeCardModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String firstRoundText;
    private String text;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return alias != null && name != null && firstRoundText != null && text != null && source != null;
    }

    @Override
    public String getNameRepresentation() {
        return FactionEmojis.xyrix + " _" + name + "_";
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return new EmbedBuilder()
                .setTitle(FactionEmojis.xyrix + " " + name)
                .setDescription("***First game round:***\n" + firstRoundText + "\n\n***Otherwise:***\n" + text)
                .setColor(new Color(95, 80, 44))
                .setFooter(alias)
                .build();
    }

    @Override
    public boolean search(String searchString) {
        String lowerCaseSearch = searchString.toLowerCase();
        return alias.toLowerCase().contains(lowerCaseSearch)
                || name.toLowerCase().contains(lowerCaseSearch)
                || firstRoundText.toLowerCase().contains(lowerCaseSearch)
                || text.toLowerCase().contains(lowerCaseSearch);
    }

    @Override
    public String getAutoCompleteName() {
        return name + " (" + source + ")";
    }
}
