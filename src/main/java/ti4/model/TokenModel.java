package ti4.model;

import java.util.List;
import lombok.Data;
import ti4.model.Source.ComponentSource;
import ti4.model.WormholeModel.Wormhole;

@Data
public class TokenModel implements ModelInterface {
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
        return id != null && imagePath != null;
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
}
