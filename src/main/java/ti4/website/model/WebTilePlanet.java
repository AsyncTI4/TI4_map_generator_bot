package ti4.website.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
class WebTilePlanet {
    private String controlledBy;
    private Map<String, List<WebEntityData>> entities;
    private Integer commodities; // Number of commodities on this planet (Discordant Stars feature)
    private boolean planetaryShield; // Whether this planet has a planetary shield (from PDS, abilities, etc.)
    private boolean exhausted;
    private Integer resources;
    private Integer influence;
    private List<String>
            actionCards; // List of action card IDs on this planet (from cards like "Infiltration", "Sabotage", etc.) -
    // Garbozia feature

    public WebTilePlanet() {
        entities = new HashMap<>();
        actionCards = new ArrayList<>();
    }

    public WebTilePlanet(String controlledBy) {
        this.controlledBy = controlledBy;
        entities = new HashMap<>();
        actionCards = new ArrayList<>();
    }
}
