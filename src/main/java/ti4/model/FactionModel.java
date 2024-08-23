package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.model.Source.ComponentSource;

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
    private String factionSheetURL;

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

    public Optional<String> getFactionSheetURL() {
        return Optional.ofNullable(factionSheetURL);
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        eb.setTitle(getFactionTitle());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        eb.setDescription(description.toString());

        if (getFactionSheetURL().isPresent()) eb.setImage(getFactionSheetURL().get());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        if (includeAliases) footer.append("\nAliases: ").append(AliasHandler.getFactionAliasEntryList(getAlias()));
        eb.setFooter(footer.toString());

        eb.setColor(Color.black);
        return eb.build();
    }

    public MessageEmbed fancyEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE - [FactionEmoji] Sardakk N'orr [SourceEmoji]
        StringBuilder title = new StringBuilder();
        title.append(getFactionEmoji()).append(" ").append(getFactionNameWithSourceEmoji());
        eb.setTitle(title.toString());

        // DESCRIPTION - <Commodity><Commodity><Commodity>
        StringBuilder desc = new StringBuilder();
        desc.append("\n").append(StringUtils.repeat(Emojis.comm, getCommodities()));
        eb.setDescription(desc.toString());

        // FIELDS
        // Abilities
        StringBuilder sb = new StringBuilder();
        for (String id : getAbilities()) {
            AbilityModel model = Mapper.getAbility(id);
            sb.append(model.getName()).append(":");
            if (model.getPermanentEffect().isPresent()) {
                String effect = model.getPermanentEffect().get().replaceAll("\n", "");
                sb.append("\n> - ").append(effect);
            }
            if (model.getWindow().isPresent()) {
                String effect = model.getWindowEffect().get().replaceAll("\n", "");
                sb.append("\n> ").append(model.getWindow().get()).append(":");
                sb.append("\n> - ").append(effect);
            }
            sb.append("\n");
        }
        eb.addField("__Abilities:__", sb.toString(), false);

        // Faction Tech
        sb = new StringBuilder();
        for (String id : getFactionTech()) {
            TechnologyModel model = Mapper.getTech(id);
            sb.append(model.getCondensedReqsEmojis(false)).append(" ").append(model.getName());
            sb.append("\n> ").append(model.getText()).append("\n");
        }
        eb.addField("__Faction Technologies__", sb.toString(), false);

        // Special Units
        sb = new StringBuilder();
        for (String id : getUnits()) {
            UnitModel model = Mapper.getUnit(id);
            if (model.getFaction().isEmpty()) continue;
            sb.append(model.getUnitEmoji()).append(" ").append(model.getName());
            if (model.getAbility().isPresent()) sb.append("\n> ").append(model.getAbility());
            sb.append("\n");
        }
        eb.addField("__Units__", sb.toString(), false);

        // Promissory Notes
        sb = new StringBuilder();
        for (String id : getPromissoryNotes()) {
            PromissoryNoteModel model = Mapper.getPromissoryNote(id);
            sb.append(model.getName()).append("\n");
        }
        eb.addField("__Promissory Notes__", sb.toString(), false);

        // Leaders
        sb = new StringBuilder();
        for (String id : getLeaders()) {
            LeaderModel model = Mapper.getLeader(id);
            sb.append(model.getLeaderEmoji()).append(" ").append(model.getName()).append("\n");
        }
        eb.addField("__Leaders__", sb.toString(), false);

        sb = new StringBuilder();
        sb.append(getStartingFleet() + "\n");
        eb.addField("__Starting Fleet__", sb.toString(), false);

        return eb.build();
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

    public String getFactionNameWithSourceEmoji() {
        return getFactionName() + getSource().emoji();
    }

    public String getFactionTitle() {
        return getFactionEmoji() + " __**" + getFactionName() + "**__" + getSource().emoji();
    }
}
