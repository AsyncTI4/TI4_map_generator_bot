package ti4.model;

import java.awt.Color;
import java.util.Comparator;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

public class PublicObjectiveModel implements ModelInterface {
    private String alias;
    private String name;
    private String phase;
    private String text;
    private Integer points;
    private String source;

  public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && text != null
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

    public String getRepresentation() {
        String emoji = Helper.getEmojiFromDiscord("Public" + points + "alt");
        return emoji + "**__" + name + "__**: " + text + " (" + points + " VP)";
    }

    public static final Comparator<PublicObjectiveModel> sortByPointsAndName = (po1, po2) -> {
        if (po1.getPoints() == po2.getPoints()) {
            return po1.getName().compareTo(po2.getName());
        } else {
            return po1.getPoints() < po2.getPoints() ? -1 : 1;
        }
    };

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(Helper.getEmojiFromDiscord("Public" + points + "alt"));
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
            case 1 -> Color.ORANGE;
            case 2 -> Color.BLUE;
            default -> Color.WHITE;
        };
    }
}
