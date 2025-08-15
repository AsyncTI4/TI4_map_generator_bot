package ti4.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;

@Data
public class AttachmentModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String name;
    private String imagePath;
    private String token;
    private Boolean isFakeAttachment; // is an attachment on backend, but should not be displayed as one
    private int resourcesModifier;
    private int influenceModifier;
    private List<String> planetTypes = new ArrayList<>();
    private List<String> techSpeciality = new ArrayList<>();
    private Boolean isLegendary;
    private int spaceCannonHitsOn;
    private int spaceCannonDieCount;
    private String abilityText;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return id != null && imagePath != null && source != null;
    }

    @Override
    public String getAlias() {
        return id; // looks like were using the attachment_<name>.png for identification for now.
    }

    public String getName() {
        return Optional.ofNullable(name).orElse(id);
    }

    public boolean isFakeAttachment() {
        return Optional.ofNullable(isFakeAttachment).orElse(false);
    }

    public boolean isRealAttachment() {
        return !isFakeAttachment();
    }

    public boolean isLegendary() {
        return Optional.ofNullable(isLegendary).orElse(false);
    }

    public Optional<String> getToken() {
        return Optional.ofNullable(token);
    }

    @Override
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(id);
        if (name != null) sb.append(" ").append(getName());
        if (resourcesModifier != 0) sb.append(" R").append(resourcesModifier);
        if (influenceModifier != 0) sb.append(" I").append(influenceModifier);

        if (isLegendary()) sb.append(" Legendary");
        if (isFakeAttachment()) sb.append(" [FAKE]");
        return sb.toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append("__").append(getName()).append("__");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (spaceCannonHitsOn != 0 || spaceCannonDieCount != 0)
            sb.append("Space Cannon: ")
                    .append(spaceCannonHitsOn)
                    .append("x")
                    .append(spaceCannonDieCount)
                    .append("\n");
        if (abilityText != null) sb.append("Ability: ").append(abilityText).append("\n");
        if (getToken().isPresent())
            sb.append("Token: ").append(getToken().get()).append("\n");
        if (isFakeAttachment != null) sb.append("(fake attachment)\n");
        eb.setDescription(sb.toString());

        sb = new StringBuilder();
        sb.append("ID: ").append(id);
        sb.append(" Source: ").append(source);
        eb.setFooter(sb.toString());

        eb.setThumbnail(
                "https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/attachment_token/"
                        + imagePath + "?raw=true");

        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return getName().toLowerCase().contains(searchString.toLowerCase())
                || id.toLowerCase().contains(searchString.toLowerCase())
                || (getToken().isPresent() && getToken().get().toLowerCase().contains(searchString.toLowerCase()))
                || (resourcesModifier != 0 && "resources".contains(searchString.toLowerCase()))
                || (influenceModifier != 0 && "influence".contains(searchString.toLowerCase()))
                || (planetTypes != null && planetTypes.toString().contains(searchString.toLowerCase()))
                || (techSpeciality != null && techSpeciality.toString().contains(searchString.toLowerCase()))
                || (isLegendary() && "legendary".contains(searchString.toLowerCase()))
                || (spaceCannonHitsOn != 0 && "space cannon".contains(searchString.toLowerCase()))
                || (isFakeAttachment() && "fake".contains(searchString.toLowerCase()))
                || getAutoCompleteName().toLowerCase().contains(searchString.toLowerCase());
    }
}
