package ti4.model;

import java.awt.*;
import java.util.List;

public class PlanetModel {
    private String id;
    private String name;
    private List<String> aliases;
    private Point positionInTile;
    private int resources;
    private int influence;
    private PlanetTypeModel planetType;
    private String cardImagePath;
    private List<TechSpecialtyModel> techSpecialties;
    private String legendaryAbilityName;
    private String legendaryAbilityText;
    private String unitPositions;
}
