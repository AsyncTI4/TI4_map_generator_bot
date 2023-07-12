package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import ti4.generator.UnitTokenPosition;

import java.awt.*;
import java.util.List;
import java.util.Optional;

@Data
public class PlanetModel {
    private String id;
    private String tileId;
    private String name;
    private String shortName;
    private List<String> aliases;
    private Point positionInTile;
    private int resources;
    private int influence;
    private String factionHomeworld;
    private PlanetTypeModel.PlanetType planetType;
    private String cardImagePath; //todo
    private List<TechSpecialtyModel.TechSpecialty> techSpecialties;
    private String legendaryAbilityName;
    private String legendaryAbilityText;
    private UnitTokenPosition unitPositions;

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }
}
