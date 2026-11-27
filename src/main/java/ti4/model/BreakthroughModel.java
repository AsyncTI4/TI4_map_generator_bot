package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.collections4.CollectionUtils;
import ti4.game.Game;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

@Data
public class BreakthroughModel implements ModelInterface, EmbeddableModel {
    private static final Pattern FLIPS_ON_UPGRADE_TECH_PATTERN = Pattern.compile(
            "If you have the (?<tech>[^.]+?) (?:unit upgrade )?technology, flip this card", Pattern.CASE_INSENSITIVE);

    private String alias;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private List<TechnologyType> synergy;
    private String faction;
    private String text;
    private String notes;
    private String homebrewReplacesID;
    private String imageURL;
    private ComponentSource source;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient Optional<UnitModel> linkedUnit;

    public boolean isValid() {
        return alias != null && name != null && source != null && text != null;
    }

    public Optional<String> getFaction() {
        return Optional.ofNullable(faction);
    }

    @JsonIgnore
    public Optional<UnitModel> getLinkedUnit() {
        if (linkedUnit == null) {
            linkedUnit = findLinkedUnit();
        }
        return linkedUnit;
    }

    private Optional<UnitModel> findLinkedUnit() {
        if (text == null || !text.toLowerCase().contains("flip this card")) return Optional.empty();
        FactionModel factionModel = faction == null ? null : Mapper.getFaction(faction);
        if (factionModel == null) return Optional.empty();
        List<UnitModel> flipSides = Mapper.getUnits().values().stream()
                .filter(unit -> getFaction().equals(unit.getFaction()))
                .filter(unit -> unit.getSource() == source)
                .filter(unit -> !factionModel.getUnits().contains(unit.getId()))
                .filter(unit -> unit.getRequiredTechId().isEmpty())
                .filter(unit -> unit.getHomebrewReplacesID().isEmpty())
                .toList();
        return flipSides.size() == 1 ? Optional.of(flipSides.getFirst()) : Optional.empty();
    }

    @JsonIgnore
    public Optional<UnitModel> getLinkedUnitUpgrade() {
        if (getFlipTechName().isEmpty()) return Optional.empty();
        return getLinkedUnit();
    }

    @JsonIgnore
    public Optional<String> getFlipTechName() {
        if (text == null) return Optional.empty();
        Matcher matcher = FLIPS_ON_UPGRADE_TECH_PATTERN.matcher(text);
        return matcher.find() ? Optional.of(matcher.group("tech")) : Optional.empty();
    }

    public TI4Emoji getFactionEmoji() {
        return FactionEmojis.getFactionIcon(faction);
    }

    @JsonIgnore
    public boolean hasSynergy() {
        return synergy != null && CollectionUtils.containsAny(synergy, TechnologyType.mainFour);
    }

    @JsonIgnore
    public TechnologyType getFirstSynergy() {
        if (synergy == null || synergy.isEmpty()) return TechnologyType.NONE;
        return Optional.ofNullable(synergy.getFirst()).orElse(TechnologyType.NONE);
    }

    @JsonIgnore
    public TechnologyType getSecondSynergy() {
        if (synergy == null || synergy.size() < 2) return TechnologyType.NONE;
        return Optional.ofNullable(synergy.get(1)).orElse(TechnologyType.NONE);
    }

    public String getNameRepresentation() {
        return getFactionEmoji() + " _" + name + "_ " + getSynergyEmojis() + " " + source.emoji();
    }

    public String getRepresentation(boolean includeCardText) {
        StringBuilder sb = new StringBuilder(getNameRepresentation());
        if (includeCardText) {
            sb.append("\n> ").append(text);
            if (notes != null) {
                sb.append("\n> -# [").append(notes).append("]");
            }
            getLinkedUnit()
                    .ifPresent(unit -> sb.append("\n> ")
                            .append(unit.getNameRepresentation())
                            .append(": ")
                            .append(unit.getRulesText().trim()));
        }
        return sb.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public List<MessageEmbed> getRepresentationEmbedsWithLinkedUnit(boolean includeID) {
        return withLinkedUnitEmbed(getRepresentationEmbed(includeID), includeID);
    }

    public List<MessageEmbed> withLinkedUnitEmbed(MessageEmbed breakthroughEmbed, boolean includeID) {
        List<MessageEmbed> embeds = new ArrayList<>(List.of(breakthroughEmbed));
        getLinkedUnit().ifPresent(unit -> embeds.add(unit.getRepresentationEmbed(includeID)));
        return embeds;
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        eb.setTitle(getFactionEmoji() + " **__" + name + "__** " + source.emoji());

        // DESCRIPTION
        String description = "SYNERGY: " + getSynergyEmojis() + "\n" + text;
        if (notes != null) {
            description += "\n-# [" + notes + "]";
        }
        eb.setDescription(description);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("ID: ").append(alias).append("    Source: ").append(source);
        eb.setFooter(footer.toString());

        eb.setColor(getEmbedColor());
        return eb.build();
    }

    private Color getEmbedColor() {
        return switch (getFirstSynergy()) {
            case PROPULSION -> Color.blue; // Color.decode("#00FF00");
            case CYBERNETIC -> Color.yellow;
            case BIOTIC -> Color.green;
            case WARFARE -> Color.red;
            case UNITUPGRADE -> Color.black;
            default -> Color.white;
        };
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public String getShortName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shortName).orElse(name);
        }
        return Optional.ofNullable(shortName)
                .orElse(Mapper.getBreakthrough(getHomebrewReplacesID().get()).getShortName());
    }

    public boolean getShrinkName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shrinkName).orElse(false);
        }
        return Optional.ofNullable(shrinkName)
                .orElse(Mapper.getBreakthrough(getHomebrewReplacesID().get()).getShrinkName());
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || getFaction().orElse("").contains(searchString);
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder(name);
        if (getFaction().isPresent()) sb.append(" (").append(getFaction().get()).append(")");
        sb.append(" [").append(source).append("]");
        return sb.toString();
    }

    public String getAutoCompleteName(Game game) {
        StringBuilder sb = new StringBuilder(name);
        if (getFaction().isPresent()) sb.append(" (").append(getFaction().get()).append(")");
        game.getPlayersFromBreakthrough(alias).stream()
                .findFirst()
                .ifPresent(p -> sb.append(' ').append(p.isBreakthroughUnlocked(alias) ? "🔓" : "🔒"));
        return sb.toString();
    }

    @JsonIgnore
    public String getSynergyEmojis() {
        if (synergy != null && synergy.size() == 2) {
            return switch (getFirstSynergy()) {
                case PROPULSION ->
                    TechEmojis.SynergyPropulsionLeft.toString()
                            + switch (getSecondSynergy()) {
                                case BIOTIC -> TechEmojis.SynergyBioticRight;
                                case CYBERNETIC -> TechEmojis.SynergyCyberneticRight;
                                case WARFARE -> TechEmojis.SynergyWarfareRight;
                                default -> TechEmojis.SynergyPropulsionRight;
                            };
                case BIOTIC ->
                    TechEmojis.SynergyBioticLeft.toString()
                            + switch (getSecondSynergy()) {
                                case PROPULSION -> TechEmojis.SynergyPropulsionRight;
                                case CYBERNETIC -> TechEmojis.SynergyCyberneticRight;
                                case WARFARE -> TechEmojis.SynergyWarfareRight;
                                default -> TechEmojis.SynergyBioticRight;
                            };
                case CYBERNETIC ->
                    TechEmojis.SynergyCyberneticLeft.toString()
                            + switch (getSecondSynergy()) {
                                case PROPULSION -> TechEmojis.SynergyPropulsionRight;
                                case BIOTIC -> TechEmojis.SynergyBioticRight;
                                case WARFARE -> TechEmojis.SynergyWarfareRight;
                                default -> TechEmojis.SynergyCyberneticRight;
                            };
                case WARFARE ->
                    TechEmojis.SynergyWarfareLeft.toString()
                            + switch (getSecondSynergy()) {
                                case PROPULSION -> TechEmojis.SynergyPropulsionRight;
                                case BIOTIC -> TechEmojis.SynergyBioticRight;
                                case CYBERNETIC -> TechEmojis.SynergyCyberneticRight;
                                default -> TechEmojis.SynergyWarfareRight;
                            };
                default -> TechEmojis.SynergyNone.toString();
            };
        }
        return TechEmojis.SynergyNone.toString();
    }

    public String getBackgroundResource() {
        return getBackgroundResource(true);
    }

    @JsonIgnore
    public String getBackgroundResource(boolean ready) {
        String suffix = ready ? "rdy" : "exh";
        return switch (getFirstSynergy()) {
            case NONE -> "pa_breakthrough_nil_" + suffix + ".png";
            case PROPULSION ->
                switch (getSecondSynergy()) {
                    case NONE -> "pa_tech_techicons_propulsion_" + suffix + ".png";
                    case BIOTIC -> "pa_breakthrough_bg_" + suffix + ".png";
                    case WARFARE -> "pa_breakthrough_br_" + suffix + ".png";
                    case CYBERNETIC -> "pa_breakthrough_by_" + suffix + ".png";
                    default -> null;
                };
            case BIOTIC ->
                switch (getSecondSynergy()) {
                    case NONE -> "pa_tech_techicons_biotic_" + suffix + ".png";
                    case PROPULSION -> "pa_breakthrough_bg_" + suffix + ".png";
                    case WARFARE -> "pa_breakthrough_gr_" + suffix + ".png";
                    case CYBERNETIC -> "pa_breakthrough_gy_" + suffix + ".png";
                    default -> null;
                };
            case WARFARE ->
                switch (getSecondSynergy()) {
                    case NONE -> "pa_tech_techicons_warfare_" + suffix + ".png";
                    case PROPULSION -> "pa_breakthrough_br_" + suffix + ".png";
                    case BIOTIC -> "pa_breakthrough_gr_" + suffix + ".png";
                    case CYBERNETIC -> "pa_breakthrough_ry_" + suffix + ".png";
                    default -> null;
                };
            case CYBERNETIC ->
                switch (getSecondSynergy()) {
                    case NONE -> "pa_tech_techicons_cybernetic_" + suffix + ".png";
                    case PROPULSION -> "pa_breakthrough_by_" + suffix + ".png";
                    case BIOTIC -> "pa_breakthrough_gy_" + suffix + ".png";
                    case WARFARE -> "pa_breakthrough_ry_" + suffix + ".png";
                    default -> null;
                };
            default -> null;
        };
    }
}
