package ti4.map;

import ti4.generator.Mapper;
import ti4.model.FactionModel;

public class Faction {

    private final FactionModel baseFaction;

    // private List<String> factionAbilities;
    // private List<String> factionTechs;
    // private List<String> factionPNs;
    // private List<Leader> leaders;

    // private String homeSystem;
    // private String flagship;

    // private int baseCommodities;
    // private String startingFleet;
    // private List<String> startingTech;

    //Constructors
    public Faction(String faction) {
        baseFaction = Mapper.getFaction(faction);
    }

    public FactionModel getBaseFaction() {
        return baseFaction;
    }
}
