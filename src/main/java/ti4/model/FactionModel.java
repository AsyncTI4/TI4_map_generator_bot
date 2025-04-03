package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;

@Data
public class FactionModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String factionName;
    private String shortName;
    private String shortTag;
    private String homeSystem;
    private String startingFleet;
    private int commodities;
    private String complexity;
    private List<String> factionTech;
    private List<String> startingTech;
    private List<String> startingTechOptions;
    private Integer startingTechAmount;
    private List<String> homePlanets;
    private List<String> abilities;
    private List<String> leaders;
    private List<String> promissoryNotes;
    private List<String> units;
    private ComponentSource source;
    private String homebrewReplacesID;
    private String factionSheetFrontImageURL;
    private String factionSheetBackImageURL;
    private String factionReferenceImageURL;
    private String wikiURL;

    public boolean isValid() {
        return alias != null
            && factionName != null
            && homeSystem != null
            && startingFleet != null
            && factionTech != null
            && (startingTech != null || (startingTechOptions != null && startingTechAmount != null))
            && homePlanets != null
            && abilities != null
            && leaders != null
            && promissoryNotes != null
            && units != null
            && source != null;
    }

    public String getFactionEmoji() {
        if (homebrewReplacesID != null) return FactionEmojis.getFactionIcon(homebrewReplacesID).toString();
        return FactionEmojis.getFactionIcon(getAlias()).toString();
    }

    public String getShortName() {
        return Optional.ofNullable(shortName).orElse(getFactionName().replace("The ", ""));
    }

    public String getShortTag() {
        return StringUtils.left(Optional.ofNullable(shortTag).orElse(getAlias()), 3).toUpperCase();
    }

    public List<String> getFactionTech() {
        return new ArrayList<>(factionTech);
    }

    public List<String> getStartingTech() {
        if (startingTech == null) return null;
        return new ArrayList<>(startingTech);
    }

    public int finalStartingTechAmount() {
        int amount = 0;
        if (getStartingTech() != null) amount += getStartingTech().size();
        if (getStartingTechAmount() != null) amount += getStartingTechAmount();
        if (getAlias().startsWith("keleres")) amount += 2;
        return amount;
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

    public String getComplexity() {
        return Optional.ofNullable(complexity).orElse("Not added to bot yet");
    }

    public Optional<String> getFactionSheetFrontImageURL() {
        return Optional.ofNullable(factionSheetFrontImageURL);
    }

    public Optional<String> getFactionSheetBackImageURL() {
        return Optional.ofNullable(factionSheetBackImageURL);
    }

    public Optional<String> getFactionReferenceImageURL() {
        return Optional.ofNullable(factionReferenceImageURL);
    }

    public Optional<String> getWikiURL() {
        return Optional.ofNullable(wikiURL);
    }

    public String getLinksText() {
        StringBuilder sb = new StringBuilder();
        getFactionSheetFrontImageURL().ifPresent(url -> sb.append("[Faction Sheet Front](").append(url).append(")\n"));
        getFactionSheetBackImageURL().ifPresent(url -> sb.append("[Faction Sheet Back](").append(url).append(")\n"));
        getFactionReferenceImageURL().ifPresent(url -> sb.append("[Quick Reference Card](").append(url).append(")\n"));
        getWikiURL().ifPresent(url -> sb.append("[Wiki Link](").append(url).append(")\n"));
        return sb.toString();
    }

    public String getFactionSheetMessage() {
        if (getFactionSheetFrontImageURL().isEmpty() && getFactionSheetBackImageURL().isEmpty()) return null;

        StringBuilder sb = new StringBuilder("## Faction Sheet: ");
        getFactionSheetFrontImageURL().ifPresent(url -> sb.append("[Front](").append(url).append(") "));
        getFactionSheetBackImageURL().ifPresent(url -> sb.append("[Back](").append(url).append(") "));
        getWikiURL().ifPresent(url -> sb.append("[Wiki Link](").append(url).append(")"));
        return sb.toString();
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        eb.setTitle(getFactionTitle());
        getWikiURL().ifPresent(eb::setUrl);

        getFactionSheetFrontImageURL().ifPresent(eb::setImage);

        // FOOTER
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
        eb.setTitle(getFactionEmoji() + " " + getFactionNameWithSourceEmoji());

        // DESCRIPTION - <Commodity><Commodity><Commodity>
        eb.setDescription("\n" + MiscEmojis.comm(getCommodities()));

        // FIELDS
        // Abilities
        StringBuilder sb = new StringBuilder();
        for (String id : getAbilities()) {
            AbilityModel model = Mapper.getAbility(id);
            sb.append(model.getName()).append(":");
            if (model.getPermanentEffect().isPresent()) {
                String effect = model.getPermanentEffect().get().replace("\n", "");
                sb.append("\n> - ").append(effect);
            }
            if (model.getWindow().isPresent() && model.getWindowEffect().isPresent()) {
                String effect = model.getWindowEffect().get().replace("\n", "");
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
            if (model.getAbility().isPresent()) sb.append("\n> ").append(model.getAbility().get());
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
        sb.append(Helper.getUnitListEmojis(getStartingFleet())).append("\n");
        eb.addField("__Starting Fleet__", sb.toString(), false);

        sb = new StringBuilder();
        if (getStartingTech() != null && !getStartingTech().isEmpty()) {
            for (String id : getStartingTech()) {
                TechnologyModel model = Mapper.getTech(id);
                sb.append(model.getCondensedReqsEmojis(false)).append(" ").append(model.getName());
                //sb.append("\n> ").append(model.getText().replace("\n","\n> ")).append("\n");
            }
        } else {
            if (getStartingTechOptions() != null && getStartingTechAmount() != 0 && !getStartingTechOptions().isEmpty()) {
                sb.append("\nPick ").append(getStartingTechAmount()).append(" of the following:\n");
                for (String id : getStartingTechOptions()) {
                    TechnologyModel model = Mapper.getTech(id);
                    sb.append(model.getCondensedReqsEmojis(false)).append(" ").append(model.getName());
                    //sb.append("\n> ").append(model.getText().replace("\n","\n> ")).append("\n");
                }
            }
        }
        if (getFactionName().toLowerCase().contains("keleres")) {
            sb = new StringBuilder();
            sb.append("Choose 2 tech owned by other players.");
        }
        eb.addField("__Starting Tech__", sb.toString(), false);

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
