package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;
import ti4.model.Source.ComponentSource;

@Data
public class ExploreModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String name;
    private String type;
    private String resolution;
    private String text;
    private String attachmentId;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    @Override
    public boolean isValid() {
        return id != null
            && name != null
            && type != null
            && resolution != null
            && List.of("Fragment", "Attach", "Instant", "Token").contains(resolution)
            && text != null
            && source != null;
    }

    @Override
    public String getAlias() {
        return getId();
    }

    public Optional<String> getAttachmentId() {
        return Optional.ofNullable(attachmentId);
    }

    public String getRepresentation() {
        return String.format("%s;%s;%s;%s;%s;%s;%s", getName(), getType(), -1, getResolution(), getText(), getAttachmentId().orElse(""), getSource());
    }

    public boolean search(String searchString) {
        searchString = searchString.toLowerCase();
        return getName().toLowerCase().contains(searchString) || getText().toLowerCase().contains(searchString) || getId().toLowerCase().contains(searchString)
            || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getType() + ") (" + getSource() + ")";
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(getTypeEmoji() + "__" + getName() + "__" + getSource().emoji(), null);
        eb.setColor(getEmbedColor());
        eb.setDescription(getText());

        if (includeID) {
            StringBuilder sb = new StringBuilder();
            if (getAttachmentId().isPresent()) sb.append("Attachment: ").append(getAttachmentId().get()).append("\n");
            sb.append("ID: ").append(getId()).append("  Source: ").append(getSource());
            eb.setFooter(sb.toString());
        }

        return eb.build();
    }

    private Color getEmbedColor() {
        return switch (getType().toLowerCase()) {
            case "cultural" -> Color.blue;
            case "hazardous" -> Color.red;
            case "industrial" -> Color.green;
            case "frontier" -> Color.black;
            default -> Color.white;
        };
    }

    private String getTypeEmoji() {
        return switch (getType().toLowerCase()) {
            case "cultural" -> Emojis.Cultural;
            case "hazardous" -> Emojis.Hazardous;
            case "industrial" -> Emojis.Industrial;
            case "frontier" -> Emojis.Frontier;
            default -> "";
        };
    }
}
