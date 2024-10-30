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
    private List<String> techSpeciality = new ArrayList<>();
    private List<String> planetTypes = new ArrayList<>();
    private int resourcesModifier;
    private int influenceModifier;
    private String abilityText;
    private String token;
    private Boolean isLegendary;
    private Boolean isFakeAttachment; // is an attachment on backend, but should not be displayed as one

    private int spaceCannonHitsOn;
    private int spaceCannonDieCount;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return id != null
            && imagePath != null
            && source != null;
    }

    @Override
    public String getAlias() {
        return getId(); // looks like were using the attachment_<name>.png for identification for now.
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
        sb.append(getId());
        if (name != null) sb.append (" ").append(getName());
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
        eb.setDescription(sb.toString());

        sb = new StringBuilder();
        sb.append("ID: ").append(getId());
        sb.append(" Source: ").append(getSource());
        eb.setFooter(sb.toString());

        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return getName().contains(searchString)
            || getId().contains(searchString)
            || (isLegendary() && "legendary".contains(searchString))
            || getSource().toString().contains(searchString)
            || getAutoCompleteName().contains(searchString);

    }
}
