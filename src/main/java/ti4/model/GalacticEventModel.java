package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class GalacticEventModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String text;
    private String notes;
    private String mapText;
    private Integer complexity;
    private String errata;
    private String flavorText;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null && name != null && text != null && complexity != null && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return Optional.ofNullable(text).orElse("");
    }

    public String getMapText() {
        return Optional.ofNullable(mapText).orElse(getText());
    }

    public String getFlavorText() {
        return Optional.ofNullable(flavorText).orElse("");
    }

    public String getRepresentation() {
        return getRepresentation(null);
    }

    public int getComplexity() {
        return complexity == null ? 1 : complexity;
    }

    public String complexityImagePath() {
        String basepath = Storage.getResourcePath() + "/extra/complexity";
        int complexity = Math.max(Math.min(getComplexity(), 3), 1);
        return basepath + complexity + ".png";
    }

    public String complexityString() {
        if (complexity <= 0) return "⬛⬛⬛";
        return switch (getComplexity()) {
            case 1 -> "🟩⬛⬛";
            case 2 -> "🟨🟨⬛";
            default -> StringUtils.repeat("🟥", getComplexity());
        };
    }

    public String getRepresentation(@Nullable Integer uniqueID) {
        StringBuilder sb = new StringBuilder();

        if (uniqueID != null) {
            sb.append("`(")
                    .append(Helper.leftpad(Integer.toString(uniqueID), 3))
                    .append(")` - ");
        }
        sb.append(CardEmojis.Event);
        sb.append("**__").append(name).append("__** ");
        sb.append(source.emoji());
        sb.append("\n");

        if (!getText().isEmpty()) {
            String arg = getText().replace("For:", "**For:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (notes != null) {
            sb.append("\n> -# [").append(notes).append("]");
        }

        return sb.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        String sb = CardEmojis.Event + "__**" + name + "**__" + source.emoji();
        eb.setTitle(sb);

        eb.setDescription(getText());
        if (notes != null) {
            eb.setDescription(getText() + "\n-# [" + notes + "]");
        }

        eb.setColor(Color.black);
        if (includeID) eb.setFooter("ID: " + alias + "  Source: " + source);
        return eb.build();
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " [" + source + "]";
    }
}
