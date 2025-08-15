package ti4.website.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WebTilePlanet {
    private String controlledBy;
    private Map<String, List<WebEntityData>> entities;
    private Integer commodities; // Number of commodities on this planet (Discordant Stars feature)

    public WebTilePlanet() {
        this.entities = new HashMap<>();
    }

    public WebTilePlanet(String controlledBy) {
        this.controlledBy = controlledBy;
        this.entities = new HashMap<>();
    }
}
