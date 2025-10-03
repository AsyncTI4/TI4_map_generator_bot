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
        return id; // looks like were using the attachment_<name>.png for identification for now.
    }

    public boolean allowedInSpace() {
        return spaceOrPlanet == null || "space".equals(spaceOrPlanet) || "both".equals(spaceOrPlanet);
    }

    public boolean allowedOnPlanet() {
        return spaceOrPlanet == null || "planet".equals(spaceOrPlanet) || "both".equals(spaceOrPlanet);
    }

    public boolean searchSource(ComponentSource searchSource) {
        return (searchSource == null || (source != null && source == searchSource));
    }

    @Override
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(id);
        if (spaceOrPlanet != null) sb.append(" [").append(spaceOrPlanet).append("]");
        return sb.toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append("__").append(id).append("__");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (spaceOrPlanet != null) sb.append("Location: ").append(spaceOrPlanet).append("\n");
        if (tokenPlanetName != null)
            sb.append("Planet: ").append(tokenPlanetName).append("\n");
        if (attachmentID != null) sb.append("Attachment: ").append(attachmentID).append("\n");
        if (wormholes != null) sb.append("Wormhole(s): ").append(wormholes).append("\n");
        if (isAnomaly != null) sb.append("Anomaly ");
        if (isRift != null) sb.append("Rift ");
        if (isNebula != null) sb.append("Nebula ");
        eb.setDescription(sb.toString());

        sb = new StringBuilder();
        sb.append("ID: ").append(id);
        sb.append(" Source: ").append(source);
        if (aliasList != null) sb.append("\nAlias list: ").append(aliasList);
        eb.setFooter(sb.toString());

        eb.setThumbnail("https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tokens/"
                + imagePath + "");

        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        return id.toLowerCase().contains(searchString.toLowerCase())
                || (aliasList != null && aliasList.toString().toLowerCase().contains(searchString.toLowerCase()))
                || (spaceOrPlanet != null && spaceOrPlanet.toLowerCase().contains(searchString.toLowerCase()))
                || (tokenPlanetName != null && tokenPlanetName.toLowerCase().contains(searchString.toLowerCase()))
                || (attachmentID != null && attachmentID.toLowerCase().contains(searchString.toLowerCase()))
                || (wormholes != null && wormholes.toString().toLowerCase().contains(searchString.toLowerCase()))
                || (isAnomaly != null && isAnomaly && "anomaly".contains(searchString.toLowerCase()))
                || (isRift != null && isRift && "gravity rift".contains(searchString.toLowerCase()))
                || (isNebula != null && isNebula && "nebula".contains(searchString.toLowerCase()))
                || getAutoCompleteName().toLowerCase().contains(searchString.toLowerCase());
    }
}
