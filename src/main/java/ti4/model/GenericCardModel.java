package ti4.model;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;

@Data
public class GenericCardModel implements ModelInterface, EmbeddableModel {
    public enum CardType {
        trap
    }

    String alias;
    String name;
    String text;
    CardType cardType;
    ComponentSource source;

    public boolean isValid() {
        return alias != null
            && name != null
            && text != null
            && cardType != null
            && source != null;
    }

    public String autoCompleteString() {
        return getAlias() + ": " + getName() + " [" + getSource().toString() + "]";
    }

    public String getAutoCompleteName() {
        return getAlias() + ": " + getName() + " [" + getSource().toString() + "]";
    }

    public boolean search(String searchString) {
        searchString = searchString.toLowerCase();
        return getAlias().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getCardType().toString().contains(searchString);
    }

    public String getRepresentation() {
        StringBuilder sb = new StringBuilder();
        String cardEmojis = cardTypeEmoji();

        sb.append(cardEmojis).append(" **__").append(name).append("__**");
        sb.append(" - ").append(text);
        return sb.toString();
    }

    public String cardTypeEmoji() {
        return switch (cardType) {
            case trap -> FactionEmojis.lizho.toString();
        };
    }

    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        String title = cardTypeEmoji() + " " + name;
        eb.setTitle(title);
        eb.setAuthor(cardType.toString());
        eb.setDescription(text);
        eb.setFooter(alias);
        return eb.build();
    }
}