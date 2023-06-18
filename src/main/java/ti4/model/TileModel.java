package ti4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ti4.map.Tile;

import java.awt.*;
import java.util.*;
import java.util.List;

public class TileModel {
    private String id;
    private String name;
    private List<String> aliases;
    private String imagePath;
    private List<PlanetModel> planets;
    private ShipPositionModel.ShipPosition shipPositionsType;
    private List<Point> spaceTokenLocations;
    private Set<WormholeModel.Wormhole> wormholes;

    public TileModel() {}

    /*@JsonCreator
    public TileModel(@JsonProperty("id") String id,
                     @JsonProperty("name") String name,
                     @JsonProperty("aliases") List<String> aliases,
                     @JsonProperty("imagePath") String imagePath,
                     @JsonProperty("planets") List<PlanetModel> planets,
                     @JsonProperty("shipPositionsType") ShipPositionModel.ShipPosition shipPositionsType,
                     @JsonProperty("spaceTokenLocations") List<Point> spaceTokenLocations,
                     @JsonProperty("wormholes") Set<WormholeModel.Wormhole> wormholes) {
        this.id = id;
        this.name = name;
        this.aliases = aliases;
        this.imagePath = imagePath;
        this.planets = planets;
        this.shipPositionsType = shipPositionsType;
        this.spaceTokenLocations = spaceTokenLocations;
        this.wormholes = wormholes;
    }*/

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public String getNameNullSafe() {
        return Optional.ofNullable(this.name).orElse("");
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public List<PlanetModel> getPlanets() {
        return planets;
    }

    public void setPlanets(List<PlanetModel> planets) {
        this.planets = planets;
    }

    public ShipPositionModel.ShipPosition getShipPositionsType() {
        return shipPositionsType;
    }

    public void setShipPositionsType(ShipPositionModel.ShipPosition shipPositionsType) {
        this.shipPositionsType = shipPositionsType;
    }

    public List<Point> getSpaceTokenLocations() {
        return spaceTokenLocations;
    }

    public void setSpaceTokenLocations(List<Point> spaceTokenLocations) {
        this.spaceTokenLocations = spaceTokenLocations;
    }
    public Set<WormholeModel.Wormhole> getWormholes() {
        return wormholes;
    }

    public void setWormholes(Set<WormholeModel.Wormhole> wormholes) {
        this.wormholes = wormholes;
    }
}
