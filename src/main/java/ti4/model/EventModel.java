package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.utils.StringUtils;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

@Data
public class EventModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String category;
    private String categoryDescription;
    private String type;
    private String target;
    private String text;
    private String mapText;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null && name != null && validateCategory() && type != null && text != null && source != null;
    }

    private boolean validateCategory() {
        switch (getCategory()) {
            case "faction" -> {
                return Mapper.isValidFaction(getCategoryDescription());
            }
            case "event" -> {
                return Stream.of("immediate", "permanent", "temporary")
                        .anyMatch(s -> s.equalsIgnoreCase(getCategoryDescription()));
            }
            default -> {
                return true;
            }
        }
    }

    public boolean staysInPlay() {
        return getCategoryDescription().equalsIgnoreCase("permanent")
                || getCategoryDescription().equalsIgnoreCase("temporary");
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return Optional.ofNullable(category).orElse("");
    }

    public String getCategoryDescription() {
        return Optional.ofNullable(categoryDescription).orElse("");
    }

    public String getType() {
        return Optional.ofNullable(type).orElse("");
    }

    public String getTarget() {
        return Optional.ofNullable(target).orElse("");
    }

    public String getText() {
        return Optional.ofNullable(text).orElse("");
    }

    public String getMapText() {
        return Optional.ofNullable(mapText).orElse(getText());
    }

    public String getRepresentation() {
        return getRepresentation(null);
    }

    public String getRepresentation(@Nullable Integer uniqueID) {
        StringBuilder sb = new StringBuilder();

        sb.append("**__");
        if (uniqueID != null) {
            sb.append("(").append(uniqueID).append(") - ");
        }
        sb.append(name).append("__** ");
        sb.append(getSource().emoji());
        sb.append("\n");

        sb.append("> **").append(type).append(":** *").append(target).append("*\n");
        if (!getText().isEmpty()) {
            String arg = getText().replace("For:", "**For:**");
            sb.append("> ").append(arg).append("\n");
        }

        return sb.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, null);
    }

    public MessageEmbed getRepresentationEmbed(int numericalID) {
        return getRepresentationEmbed(false, numericalID);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, Integer numericalID) {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        if (numericalID != null) sb.append("(").append(numericalID).append(") ");
        sb.append("__**").append(getName()).append("**__").append(getSource().emoji());
        eb.setTitle(sb.toString());

        eb.setColor(Color.black);
        eb.addField(StringUtils.capitalize(getCategoryDescription()) + " " + getType(), getText(), false);
        if (includeID) eb.setFooter("ID: " + getAlias() + "  Source: " + getSource());
        return eb.build();
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString)
                || getName().toLowerCase().contains(searchString)
                || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " [" + getSource() + "]";
    }
}
