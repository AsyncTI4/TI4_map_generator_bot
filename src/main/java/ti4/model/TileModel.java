package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.awt.*;
import java.util.*;
import java.util.List;

@Data
public class TileModel {
    private String id;
    private String name;
    private List<String> aliases;
    private String imagePath;

    private List<String> planetIds;

    private ShipPositionModel.ShipPosition shipPositionsType;
    private List<Point> spaceTokenLocations;
    private Set<WormholeModel.Wormhole> wormholes;
    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    public List<String> getPlanets() {
        return planetIds;
    }

    public void setPlanets(List<String> planetIds) {
        this.planetIds = planetIds;
    }
}
