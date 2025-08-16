package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.ExploreEmojis;

@Data
public class ExploreModel implements ModelInterface, EmbeddableModel {
    private static final Pattern PATTERN = Pattern.compile("\n(> )?");
    private String id;
    private String name;
    private String type;
    private String resolution;
    private String text;
    private String attachmentId;
    private String flavorText;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    @Override
    public boolean isValid() {
        return id != null
                && name != null
                && type != null
                && resolution != null
                && List.of("Fragment", "Attach", "Instant", "Token", "Leader").contains(resolution)
                && text != null
                && source != null;
    }

    @Override
    public String getAlias() {
        return id;
    }

    public Optional<String> getAttachmentId() {
        return Optional.ofNullable(attachmentId);
    }

    private Optional<String> getFlavorText() {
        return Optional.ofNullable(flavorText);
    }

    /**
     * @deprecated This only exists to support legacy code reliant on String.split(";")
     */
    @Deprecated
    public String getRepresentation() {
        return String.format(
                "%s;%s;%s;%s;%s;%s;%s",
                name,
                type.toLowerCase(),
                -1,
                resolution,
                text,
                getAttachmentId().orElse(""),
                source);
    }

    public String textRepresentation() {
        StringBuilder sb = new StringBuilder(getTypeEmoji()).append(" ");
        if (source != null) sb.append(source.emoji()).append(" ");
        sb.append("_").append(name).append("_\n> ");
        sb.append(PATTERN.matcher(text).replaceAll("\n> "));
        return sb.toString();
    }

    public boolean search(String searchString) {
        searchString = searchString.toLowerCase();
        return name.toLowerCase().contains(searchString)
                || text.toLowerCase().contains(searchString)
                || id.toLowerCase().contains(searchString)
                || type.toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " (" + type + ") [" + source + "]";
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean showFlavorText) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(getTypeEmoji() + "__" + name + "__" + source.emoji(), null);
        eb.setColor(getEmbedColor());
        eb.setDescription(text);

        if (includeID) {
            StringBuilder sb = new StringBuilder();
            if (getAttachmentId().isPresent())
                sb.append("Attachment: ").append(getAttachmentId().get()).append("\n");
            sb.append("ID: ").append(id).append("  Source: ").append(source);
            eb.setFooter(sb.toString());
        }

        if (showFlavorText && getFlavorText().isPresent()) {
            eb.addField("", getFlavorText().get(), false);
        }

        return eb.build();
    }

    private Color getEmbedColor() {
        return switch (type.toLowerCase()) {
            case "cultural" -> Color.blue;
            case "hazardous" -> Color.red;
            case "industrial" -> Color.green;
            case "frontier" -> Color.black;
            default -> Color.white;
        };
    }

    private String getTypeEmoji() {
        return switch (type.toLowerCase()) {
            case "cultural" -> ExploreEmojis.Cultural.toString();
            case "hazardous" -> ExploreEmojis.Hazardous.toString();
            case "industrial" -> ExploreEmojis.Industrial.toString();
            case "frontier" -> ExploreEmojis.Frontier.toString();
            default -> "";
        };
    }
}
