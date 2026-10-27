package ti4.model;

import java.awt.Color;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;

@Data
public class HydromorphicCardModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String window;
    private String text;
    private Integer actionCardDiscardMin;
    private Integer actionCardDiscardMax;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return alias != null
                && name != null
                && window != null
                && text != null
                && actionCardDiscardMin != null
                && actionCardDiscardMin >= 0
                && (actionCardDiscardMax == null || actionCardDiscardMax >= actionCardDiscardMin)
                && source != null;
    }

    @Override
    public String getNameRepresentation() {
        return FactionEmojis.morpha + " _" + name + "_";
    }

    public String getRepresentation() {
        return getNameRepresentation() + " — " + window + ": " + text;
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return new EmbedBuilder()
                .setTitle(FactionEmojis.morpha + " " + name)
                .setDescription("***" + window + ":***\n" + text)
                .setColor(new Color(74, 79, 47))
                .setFooter(alias)
                .build();
    }

    @Override
    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString.toLowerCase())
                || name.toLowerCase().contains(searchString.toLowerCase())
                || text.toLowerCase().contains(searchString.toLowerCase());
    }

    @Override
    public String getAutoCompleteName() {
        return name + " (" + source + ")";
    }
}
