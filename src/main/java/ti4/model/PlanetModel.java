package ti4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ti4.generator.UnitTokenPosition;

import java.awt.*;
import java.util.List;
import java.util.Optional;

public class PlanetModel {
    private String id;
    private String name;
    private List<String> aliases;
    private Point positionInTile;
    private int resources;
    private int influence;
    private PlanetTypeModel.PlanetType planetType;
    private String cardImagePath; //todo
    private List<TechSpecialtyModel.TechSpecialty> techSpecialties;
    private String legendaryAbilityName;
    private String legendaryAbilityText;
    private UnitTokenPosition unitPositions;

    public PlanetModel() {}

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
        return Optional.ofNullable(name).orElse("");
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

    public Point getPositionInTile() {
        return positionInTile;
    }

    public void setPositionInTile(Point positionInTile) {
        this.positionInTile = positionInTile;
    }

    public int getResources() {
        return resources;
    }

    public void setResources(int resources) {
        this.resources = resources;
    }

    public int getInfluence() {
        return influence;
    }

    public void setInfluence(int influence) {
        this.influence = influence;
    }

    public PlanetTypeModel.PlanetType getPlanetType() {
        return planetType;
    }

    public void setPlanetType(PlanetTypeModel.PlanetType planetType) {
        this.planetType = planetType;
    }

    public String getCardImagePath() {
        return cardImagePath;
    }

    public void setCardImagePath(String cardImagePath) {
        this.cardImagePath = cardImagePath;
    }

    public List<TechSpecialtyModel.TechSpecialty> getTechSpecialties() {
        return techSpecialties;
    }

    public void setTechSpecialties(List<TechSpecialtyModel.TechSpecialty> techSpecialties) {
        this.techSpecialties = techSpecialties;
    }

    public String getLegendaryAbilityName() {
        return legendaryAbilityName;
    }

    public void setLegendaryAbilityName(String legendaryAbilityName) {
        this.legendaryAbilityName = legendaryAbilityName;
    }

    public String getLegendaryAbilityText() {
        return legendaryAbilityText;
    }

    public void setLegendaryAbilityText(String legendaryAbilityText) {
        this.legendaryAbilityText = legendaryAbilityText;
    }

    public UnitTokenPosition getUnitPositions() {
        return unitPositions;
    }

    public void setUnitPositions(UnitTokenPosition unitPositions) {
        this.unitPositions = unitPositions;
    }
}
