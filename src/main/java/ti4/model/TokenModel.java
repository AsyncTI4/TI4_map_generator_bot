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
    private String placement;
    private Double scale;
    private Boolean isPlanet;

    @Override
    public boolean isValid() {
        return id != null && imagePath != null && source != null;
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
        sb.append(getId());
        if (getSpaceOrPlanet() != null)
            sb.append(" [").append(getSpaceOrPlanet()).append("]");
        return sb.toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append("__").append(getId()).append("__");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (getSpaceOrPlanet() != null)
            sb.append("Location: ").append(getSpaceOrPlanet()).append("\n");
        if (getTokenPlanetName() != null)
            sb.append("Planet: ").append(getTokenPlanetName()).append("\n");
        if (getAttachmentID() != null)
            sb.append("Attachment: ").append(getAttachmentID()).append("\n");
        if (getWormholes() != null)
            sb.append("Wormhole(s): ").append(getWormholes().toString()).append("\n");
        if (getIsAnomaly() != null) sb.append("Anomaly ");
        if (getIsRift() != null) sb.append("Rift ");
        if (getIsNebula() != null) sb.append("Nebula ");
        eb.setDescription(sb.toString());

        sb = new StringBuilder();
        sb.append("ID: ").append(getId());
        sb.append(" Source: ").append(getSource());
        if (getAliasList() != null)
            sb.append("\nAlias list: ").append(getAliasList().toString());
        eb.setFooter(sb.toString());

        eb.setThumbnail("https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tokens/"
                + getImagePath() + "?raw=true");

        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return getId().toLowerCase().contains(searchString.toLowerCase())
                || (getAliasList() != null
                        && getAliasList().toString().toLowerCase().contains(searchString.toLowerCase()))
                || (getSpaceOrPlanet() != null
                        && getSpaceOrPlanet().toLowerCase().contains(searchString.toLowerCase()))
                || (getTokenPlanetName() != null
                        && getTokenPlanetName().toLowerCase().contains(searchString.toLowerCase()))
                || (getAttachmentID() != null && getAttachmentID().toLowerCase().contains(searchString.toLowerCase()))
                || (getWormholes() != null
                        && getWormholes().toString().toLowerCase().contains(searchString.toLowerCase()))
                || (getIsAnomaly() != null && getIsAnomaly() && "anomaly".contains(searchString.toLowerCase()))
                || (getIsRift() != null && getIsRift() && "gravity rift".contains(searchString.toLowerCase()))
                || (getIsNebula() != null && getIsNebula() && "nebula".contains(searchString.toLowerCase()))
                || getAutoCompleteName().toLowerCase().contains(searchString.toLowerCase());
    }
}
