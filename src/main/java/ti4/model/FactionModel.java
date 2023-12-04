package ti4.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.model.Source.ComponentSource;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

@Data
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
    private ComponentSource source;
    private String homebrewReplacesID;

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

    public String getFactionEmoji() {
        if (homebrewReplacesID != null) return Emojis.getFactionIconFromDiscord(homebrewReplacesID);
        return Emojis.getFactionIconFromDiscord(getAlias());
    }

    public String getShortTag() {
        return StringUtils.left(Optional.ofNullable(shortTag).orElse(getAlias()), 3).toUpperCase();
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

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRepresentationEmbed'");
    }

    @Override
    public boolean search(String searchString) {
        searchString = searchString.toLowerCase();
        return getFactionName().contains(searchString)
            || getAlias().contains(searchString)
            || getShortTag().contains(searchString)
            || getSource().toString().contains(searchString)
            || getAlias().equals(AliasHandler.resolveFaction(searchString));
    }

    @Override
    public String getAutoCompleteName() {
        return getFactionName() + " [" + source + "]";
    }
}
