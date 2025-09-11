package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.Color;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.collections4.CollectionUtils;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

@Data
public class BreakthroughModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String displayName;
    private List<TechnologyType> synergy;
    private String faction;
    private String text;
    private ComponentSource source;

    public boolean isValid() {
        return alias != null && name != null && source != null && text != null;
    }

    public Optional<String> getFaction() {
        return Optional.ofNullable(faction);
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
        return synergy.getFirst();
    }

    public String getNameRepresentation() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFactionEmoji()).append(" ").append(getSynergyEmojis());
        sb.append("**").append(getName()).append("**");
        sb.append(getSource().emoji());
        return sb.toString();
    }

    public String getRepresentation(boolean includeCardText) {
        StringBuilder sb = new StringBuilder(getNameRepresentation());
        if (includeCardText) sb.append("\n> ").append(getText());
        return sb.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        eb.setTitle(
                getFactionEmoji() + " **__" + getName() + "__** " + getSource().emoji());

        // DESCRIPTION
        StringBuilder description = new StringBuilder();
        description.append("SYNERGY: ").append(getSynergyEmojis()).append("\n");
        description.append(getText());
        eb.setDescription(description.toString());

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
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

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString)
                || getName().toLowerCase().contains(searchString)
                || getFaction().orElse("").contains(searchString);
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder(getName());
        if (getFaction().isPresent()) sb.append(" (").append(getFaction().get()).append(")");
        sb.append(" [").append(getSource()).append("]");
        return sb.toString();
    }

    @JsonIgnore
    public String getSynergyEmojis() {
        if (synergy.size() == 2) {
            TechnologyType synergy2 = synergy.get(1);
            return switch (getFirstSynergy()) {
                case PROPULSION ->
                    TechEmojis.SynergyPropulsionLeft.toString()
                            + switch (synergy2) {
                                case BIOTIC -> TechEmojis.SynergyBioticRight;
                                case CYBERNETIC -> TechEmojis.SynergyCyberneticRight;
                                case WARFARE -> TechEmojis.SynergyWarfareRight;
                                default -> TechEmojis.SynergyPropulsionRight;
                            };
                case BIOTIC ->
                    TechEmojis.SynergyBioticLeft.toString()
                            + switch (synergy2) {
                                case PROPULSION -> TechEmojis.SynergyPropulsionRight;
                                case CYBERNETIC -> TechEmojis.SynergyCyberneticRight;
                                case WARFARE -> TechEmojis.SynergyWarfareRight;
                                default -> TechEmojis.SynergyBioticRight;
                            };
                case CYBERNETIC ->
                    TechEmojis.SynergyCyberneticLeft.toString()
                            + switch (synergy2) {
                                case PROPULSION -> TechEmojis.SynergyPropulsionRight;
                                case BIOTIC -> TechEmojis.SynergyBioticRight;
                                case WARFARE -> TechEmojis.SynergyWarfareRight;
                                default -> TechEmojis.SynergyCyberneticRight;
                            };
                case WARFARE ->
                    TechEmojis.SynergyWarfareLeft.toString()
                            + switch (synergy2) {
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

    @JsonIgnore
    public String getBackgroundResource() {
        if (synergy.size() == 2) {
            TechnologyType synergy2 = synergy.get(1);
            String mid =
                    switch (getFirstSynergy()) {
                        case PROPULSION ->
                            switch (synergy2) {
                                case BIOTIC -> "multicolorbg";
                                case WARFARE -> "multicolorbr";
                                case CYBERNETIC -> "multicolorby";
                                default -> "propulsion";
                            };
                        case BIOTIC ->
                            switch (synergy2) {
                                case PROPULSION -> "multicolorbg";
                                case WARFARE -> "multicolorgr";
                                case CYBERNETIC -> "multicolorgy";
                                default -> "biotic";
                            };
                        case WARFARE ->
                            switch (synergy2) {
                                case PROPULSION -> "multicolorbr";
                                case BIOTIC -> "multicolorgr";
                                case CYBERNETIC -> "multicolorry";
                                default -> "warfare";
                            };
                        case CYBERNETIC ->
                            switch (synergy2) {
                                case PROPULSION -> "multicolorby";
                                case BIOTIC -> "multicolorgy";
                                case WARFARE -> "multicolorry";
                                default -> "cybernetic";
                            };
                        default -> null;
                    };
            if (mid == null) return null;
            return "pa_tech_techicons_" + mid + ".png";
        }
        return null;
    }
}
