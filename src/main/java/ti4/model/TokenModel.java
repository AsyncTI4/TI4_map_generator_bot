package ti4.model;

import java.util.List;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.model.Source.ComponentSource;
import ti4.model.WormholeModel.Wormhole;

@Data
public class TokenModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String imagePath;
    private String spaceOrPlanet;
    private String attachmentID;
    private String tokenPlanetName;
    private Boolean isAnomaly;
    private Boolean isRift;
    private Boolean isNebula;
    private List<String> aliasList;
    private List<Wormhole> wormholes;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return id != null
            && imagePath != null;
    }

    @Override
    public String getAlias() {
        return getId(); // looks like were using the attachment_<name>.png for identification for now.
    }

    public boolean allowedInSpace() {
        return spaceOrPlanet == null || "space".equals(spaceOrPlanet) || "both".equals(spaceOrPlanet);
    }

    public boolean allowedOnPlanet() {
        return spaceOrPlanet == null || "planet".equals(spaceOrPlanet) || "both".equals(spaceOrPlanet);
    }

    public boolean searchSource(ComponentSource searchSource) {
        return (searchSource == null || (getSource() != null && getSource().equals(searchSource)));
    }

    @Override
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getId()).append(" [").append(getSpaceOrPlanet()).append("]");
        return sb.toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append("__").append(getId()).append("__");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (getSpaceOrPlanet() != null) sb.append("Location: ").append(getSpaceOrPlanet().toString()).append("\n");
        if (getTokenPlanetName() != null) sb.append("Planet: ").append(getTokenPlanetName().toString()).append("\n");
        if (getAttachmentID() != null) sb.append("Attachment: ").append(getAttachmentID().toString()).append("\n");
        if (getWormholes() != null) sb.append("Wormhole: ").append(getWormholes().toString()).append("\n");
        if (getIsAnomaly() != null) sb.append("Anomaly");
        eb.setDescription(sb.toString());

        sb = new StringBuilder();
        sb.append("ID: ").append(getId());
        if (getSource() != null) sb.append(" Source: ").append(getSource());
        eb.setFooter(sb.toString());

        eb.setThumbnail("https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tokens/" + getImagePath() + "?raw=true");

        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return getId().contains(searchString)
            || (getAliasList() != null && getAliasList().toString().contains(searchString))
            || getSource().toString().contains(searchString)
            || getAutoCompleteName().contains(searchString);
    }

}
