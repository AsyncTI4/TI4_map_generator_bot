package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
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
    private List<String> preferredColours;
    private Integer priorityNumber;

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
        if (homebrewReplacesID != null)
            return FactionEmojis.getFactionIcon(homebrewReplacesID).toString();
        return FactionEmojis.getFactionIcon(alias).toString();
    }

    public String getShortName() {
        return Optional.ofNullable(shortName).orElse(StringUtils.capitalize(alias));
    }

    public String getShortTag() {
        return StringUtils.left(Optional.ofNullable(shortTag).orElse(alias), 3).toUpperCase();
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
        if (startingTechAmount != null) amount += startingTechAmount;
        if (alias.startsWith("keleres")) amount += 2;
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

    private Optional<String> getFactionSheetFrontImageURL() {
        return Optional.ofNullable(factionSheetFrontImageURL);
    }

    private Optional<String> getFactionSheetBackImageURL() {
        return Optional.ofNullable(factionSheetBackImageURL);
    }

    private Optional<String> getFactionReferenceImageURL() {
        return Optional.ofNullable(factionReferenceImageURL);
    }

    private Optional<String> getWikiURL() {
        return Optional.ofNullable(wikiURL);
    }

    public String getBreakthrough() {
        String breakthrough = alias + "bt";
        if (breakthrough.contains("keleres")) {
            breakthrough = "keleresbt";
        }
        return breakthrough;
    }

    public List<String> getPreferredColours() {
        return Optional.ofNullable(preferredColours)
                .orElse(
                        homebrewReplacesID != null
                                ? Mapper.getFaction(homebrewReplacesID).getPreferredColours()
                                : new ArrayList<>());
    }

    public String getLinksText() {
        StringBuilder sb = new StringBuilder();
        getFactionSheetFrontImageURL()
                .ifPresent(
                        url -> sb.append("[Faction Sheet Front](").append(url).append(")\n"));
        getFactionSheetBackImageURL()
                .ifPresent(url -> sb.append("[Faction Sheet Back](").append(url).append(")\n"));
        getFactionReferenceImageURL()
                .ifPresent(
                        url -> sb.append("[Quick Reference Card](").append(url).append(")\n"));
        getWikiURL().ifPresent(url -> sb.append("[Wiki Link](").append(url).append(")\n"));
        return sb.toString();
    }

    public String getFactionSheetMessage() {
        if (getFactionSheetFrontImageURL().isEmpty()
                && getFactionSheetBackImageURL().isEmpty()) return null;

        StringBuilder sb = new StringBuilder("## Faction Sheet: ");
        getFactionSheetFrontImageURL()
                .ifPresent(url -> sb.append("[Front](").append(url).append(") "));
        getFactionSheetBackImageURL()
                .ifPresent(url -> sb.append("[Back](").append(url).append(") "));
        getWikiURL().ifPresent(url -> sb.append("[Wiki Link](").append(url).append(")"));
        return sb.toString();
    }

    public String getNameRepresentation() {
        return getFactionEmoji() + " _" + factionName + "_ " + source.emoji();
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
        if (includeID)
            footer.append("ID: ").append(alias).append("    Source: ").append(source);
        if (includeAliases) footer.append("\nAliases: ").append(AliasHandler.getFactionAliasEntryList(alias));
        eb.setFooter(footer.toString());

        eb.setColor(Color.black);
        return eb.build();
    }

    public MessageEmbed fancyEmbed() {
        return fancyEmbeds().getFirst();
    }

    public List<MessageEmbed> fancyEmbeds() {
        List<FancyEmbedField> fields = getFancyEmbedFields();
        List<MessageEmbed> embeds = new ArrayList<>();
        EmbedBuilder builder = newFancyEmbedBuilder(false);
        int embedLength = getFancyEmbedBaseLength(false);
        int fieldCount = 0;
        for (FancyEmbedField field : fields) {
            List<String> values = splitFancyEmbedFieldValue(field.value());
            for (int index = 0; index < values.size(); index++) {
                String name = index == 0
                        ? StringUtils.abbreviate(field.name(), 256)
                        : getContinuedFancyEmbedFieldName(field.name(), index);
                String value = values.get(index);
                if (fieldCount == 25 || embedLength + name.length() + value.length() > 6000) {
                    embeds.add(builder.build());
                    builder = newFancyEmbedBuilder(true);
                    embedLength = getFancyEmbedBaseLength(true);
                    fieldCount = 0;
                }
                builder.addField(name, value, field.inline());
                embedLength += name.length() + value.length();
                fieldCount++;
            }
        }
        embeds.add(builder.build());
        return embeds;
    }

    private List<FancyEmbedField> getFancyEmbedFields() {
        List<FancyEmbedField> fields = new ArrayList<>();
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
            sb.append('\n');
        }
        if (!sb.isEmpty()) {
            fields.add(new FancyEmbedField("__Abilities:__", sb.toString(), false));
        }

        // Faction Tech
        sb = new StringBuilder();
        for (String id : getFactionTech()) {
            TechnologyModel model = Mapper.getTech(id);
            sb.append(model.getCondensedReqsEmojis(false)).append(' ').append(model.getName());
            sb.append("\n> ").append(model.getText()).append('\n');
        }
        if (!sb.isEmpty()) {
            fields.add(new FancyEmbedField("__Faction Technologies__", sb.toString(), false));
        }

        // Special Units
        sb = new StringBuilder();
        for (String id : getUnits()) {
            UnitModel model = Mapper.getUnit(id);
            if (model.getFaction().isEmpty()) continue;
            sb.append(model.getUnitEmoji()).append(' ').append(model.getName());
            if (model.getAbility().isPresent())
                sb.append("\n> ").append(model.getAbility().get());
            sb.append('\n');
        }
        if (!sb.isEmpty()) {
            fields.add(new FancyEmbedField("__Units__", sb.toString(), false));
        }

        // Promissory Notes
        for (String id : getPromissoryNotes()) {
            PromissoryNoteModel model = Mapper.getPromissoryNote(id);
            if (model == null) continue;
            fields.add(new FancyEmbedField(
                    "__Promissory Note__ _" + model.getName() + "_",
                    "> " + model.getText().replace("\n", "\n> "),
                    false));
        }

        // Leaders
        for (String id : getLeaders()) {
            LeaderModel model = Mapper.getLeader(id);
            if (model == null) continue;
            sb = new StringBuilder();
            if (!StringUtils.isBlank(model.getAbilityWindow())) {
                sb.append("> **").append(model.getAbilityWindow()).append("**\n");
            }
            if (!StringUtils.isBlank(model.getAbilityText())) {
                sb.append("> ")
                        .append(model.getAbilityText().replace("\n", "\n> "))
                        .append('\n');
            }
            if (sb.isEmpty()) {
                sb.append("> No ability text.");
            }
            fields.add(new FancyEmbedField(
                    "__" + model.getLeaderEmoji() + " " + model.getName() + ", the " + getShortName() + " "
                            + model.getType()
                            + "__",
                    sb.toString(),
                    false));
        }

        sb = new StringBuilder();
        TileModel homeSystemTile = TileHelper.getTileById(homeSystem);
        sb.append("**").append(homeSystem).append("**");
        if (homeSystemTile != null && !StringUtils.isBlank(homeSystemTile.getName())) {
            sb.append(" — ").append(homeSystemTile.getName());
        }
        for (String planetId : getHomePlanets()) {
            PlanetModel planet = Mapper.getPlanet(planetId);
            if (planet == null) continue;
            sb.append("\n> **")
                    .append(planet.getName())
                    .append("** (")
                    .append(planet.getResources())
                    .append("/")
                    .append(planet.getInfluence())
                    .append(")");
            if (!planet.getPlanetTypes().isEmpty()) {
                sb.append(" — ").append(planet.getPlanetTypes());
            }
        }
        fields.add(new FancyEmbedField("__Home System__", sb.toString(), false));

        sb = new StringBuilder();
        sb.append(Helper.getUnitListEmojis(startingFleet)).append('\n');
        if (!startingFleet.isEmpty()) {
            fields.add(new FancyEmbedField("__Starting Fleet__", sb.toString(), false));
        }

        sb = new StringBuilder();
        if (getStartingTech() != null && !getStartingTech().isEmpty()) {
            for (String id : getStartingTech()) {
                TechnologyModel model = Mapper.getTech(id);
                sb.append(model.getCondensedReqsEmojis(false)).append(' ').append(model.getName());
                // sb.append("\n> ").append(model.getText().replace("\n","\n> ")).append('\n');
            }
        } else {
            if (startingTechOptions != null && startingTechAmount != 0 && !startingTechOptions.isEmpty()) {
                sb.append("\nPick ").append(startingTechAmount).append(" of the following:\n");
                for (String id : startingTechOptions) {
                    TechnologyModel model = Mapper.getTech(id);
                    sb.append(model.getCondensedReqsEmojis(false)).append(' ').append(model.getName());
                    // sb.append("\n> ").append(model.getText().replace("\n","\n> ")).append('\n');
                }
            }
        }
        if (factionName.toLowerCase().contains("keleres")) {
            sb = new StringBuilder();
            sb.append("Choose 2 non-faction technologies owned by other players.");
        }
        if (!sb.isEmpty()) {
            fields.add(new FancyEmbedField("__Starting Tech__", sb.toString(), false));
        }

        return fields;
    }

    private EmbedBuilder newFancyEmbedBuilder(boolean continued) {
        String title = getFactionEmoji() + " " + getFactionNameWithSourceEmoji();
        if (continued) {
            title = StringUtils.abbreviate(title, 242) + " (continued)";
        }
        return new EmbedBuilder().setTitle(title).setDescription("\n" + MiscEmojis.comm(commodities));
    }

    private int getFancyEmbedBaseLength(boolean continued) {
        int titleLength = (getFactionEmoji() + " " + getFactionNameWithSourceEmoji()).length();
        if (continued) {
            titleLength = StringUtils.abbreviate(getFactionEmoji() + " " + getFactionNameWithSourceEmoji(), 242)
                            .length()
                    + " (continued)".length();
        }
        return titleLength + ("\n" + MiscEmojis.comm(commodities)).length();
    }

    private static List<String> splitFancyEmbedFieldValue(String value) {
        List<String> values = new ArrayList<>();
        while (value.length() > 1024) {
            int splitIndex = value.lastIndexOf('\n', 1024);
            if (splitIndex <= 0) {
                splitIndex = 1024;
            }
            values.add(value.substring(0, splitIndex));
            value = value.substring(splitIndex).stripLeading();
        }
        values.add(value);
        return values;
    }

    private static String getContinuedFancyEmbedFieldName(String name, int index) {
        String suffix = " (continued " + (index + 1) + ")";
        return StringUtils.abbreviate(name, 256 - suffix.length()) + suffix;
    }

    private record FancyEmbedField(String name, String value, boolean inline) {}

    @Override
    public boolean search(String searchString) {
        searchString = searchString.toLowerCase();
        return factionName.contains(searchString)
                || alias.contains(searchString)
                || getShortTag().contains(searchString)
                || source.toString().contains(searchString)
                || alias.equals(AliasHandler.resolveFaction(searchString));
    }

    @Override
    public String getAutoCompleteName() {
        return factionName + " [" + source + "]";
    }

    public String getFactionNameWithSourceEmoji() {
        return factionName + source.emoji();
    }

    public String getFactionTitle() {
        return getFactionEmoji() + " __**" + factionName + "**__" + source.emoji();
    }
}
