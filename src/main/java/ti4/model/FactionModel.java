package ti4.model;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.message.BotLogger;

import java.util.ArrayList;

public class FactionModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String factionName;
    private String shortTag;
    private String homeSystem;
    private String startingFleet;
    private int commodities;
    private List<String> factionTech;
    private List<String> startingTech;
    private List<String> homePlanets;
    private List<String> abilities;
    private List<String> leaders;
    private List<String> promissoryNotes;
    private List<String> units;
    private String source;

    public boolean isValid() {
        return alias != null
            && factionName != null
            && homeSystem != null
            && startingFleet != null
            && factionTech != null
            && startingTech != null
            && homePlanets != null
            && abilities != null
            && leaders != null
            && promissoryNotes != null
            && units != null
            && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getFactionName() {
        return factionName;
    }

    public String getShortTag() {
        return StringUtils.left(Optional.ofNullable(shortTag).orElse(getAlias()), 3).toUpperCase();
    }

    public String getHomeSystem() {
        return homeSystem;
    }

    public String getStartingFleet() {
        return startingFleet;
    }

    public int getCommodities() {
        return commodities;
    }

    public String getSource() {
        return source;
    }

    public List<String> getFactionTech() {
        return new ArrayList<>(factionTech);
    }

    public List<String> getStartingTech() {
        return new ArrayList<>(startingTech);
    }

    public List<String> getHomePlanets() {
        return new ArrayList<>(homePlanets);
    }

    public List<String> getAbilities() {
        return new ArrayList<>(abilities);
    }

    public List<String> getLeaders() {
        return new ArrayList<>(leaders);
    }

    public List<String> getPromissoryNotes() {
        return new ArrayList<>(promissoryNotes);
    }

    public List<String> getUnits() {
        return new ArrayList<>(units);
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRepresentationEmbed'");
    }

    @Override
    public boolean search(String searchString) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'search'");
    }

    @Override
    public String getAutoCompleteName() {
        return getFactionName();
    }
}
