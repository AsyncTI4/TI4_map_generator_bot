package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.model.Source.ComponentSource;

@Data
public class TechnologyModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private List<TechnologyType> types;
    private String requirements;
    private String faction;
    private String baseUpgrade;
    private ComponentSource source;
    private String text;
    private String homebrewReplacesID;
    private List<String> searchTags = new ArrayList<>();

    public enum TechnologyType {
        PROPULSION, CYBERNETIC, WARFARE, BIOTIC, UNITUPGRADE, NONE;

        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public boolean isValid() {
        return alias != null
            && name != null
            && types != null
            && !types.isEmpty()
            // && getRequirements() != null
            // && getFaction() != null
            // && getBaseUpgrade() != null
            && source != null
            && text != null;
    }

    /**
     * @return the first techType in the list of techTypes - this will be broken for techs with multiple types
     * @deprecated Added to handle the switch from [TechnologyType type] -> [List(TechnologyType)] types. Use helpers isPropulsionTech, isCyberneticTech, isBioticTech, isWarfareTech, and isUnitUpgrade instead
     */
    @Deprecated
    @JsonIgnore
    public TechnologyType getType() {
        return types.get(0);
    }

    public boolean isPropulsionTech() {
        return types.contains(TechnologyType.PROPULSION);
    }

    public boolean isCyberneticTech() {
        return types.contains(TechnologyType.CYBERNETIC);
    }

    public boolean isBioticTech() {
        return types.contains(TechnologyType.BIOTIC);
    }

    public boolean isWarfareTech() {
        return types.contains(TechnologyType.WARFARE);
    }

    public boolean isUnitUpgrade() {
        return types.contains(TechnologyType.UNITUPGRADE);
    }

    public boolean isDualPropulsionCybernetic() {
        return isPropulsionTech() && isCyberneticTech();
    }

    public boolean isDualPropulsionBiotic() {
        return isPropulsionTech() && isBioticTech();
    }

    public boolean isDualPropulsionWarfare() {
        return isPropulsionTech() && isWarfareTech();
    }

    public boolean isDualCyberneticBiotic() {
        return isCyberneticTech() && isBioticTech();
    }

    public boolean isDualCyberneticWarfare() {
        return isCyberneticTech() && isWarfareTech();
    }

    public boolean isDualWarfareBiotic() {
        return isWarfareTech() && isBioticTech();
    }

    public boolean hasMoreThanOneType() {
        return types.size() > 1;
    }

    public String getImageFileModifier() {
        if (types.size() == 2) {
            if (isDualPropulsionBiotic()) {
                return "propulsionbiotic";
            } else if (isDualPropulsionCybernetic()) {
                return "propulsioncybernetic";
            } else if (isDualPropulsionWarfare()) {
                return "propulsionwarfare";
            } else if (isDualCyberneticBiotic()) {
                return "cyberneticbiotic";
            } else if (isDualCyberneticWarfare()) {
                return "cyberneticwarfare";
            } else if (isDualWarfareBiotic()) {
                return "warfarebiotic";
            }
        }
        return getType().toString();
    }

    public static final Comparator<TechnologyModel> sortByTechRequirements = TechnologyModel::sortTechsByRequirements;

    public static int sortTechsByRequirements(TechnologyModel t1, TechnologyModel t2) {
        int r1 = t1.getRequirements().orElse("").length();
        int r2 = t2.getRequirements().orElse("").length();
        if (r1 != r2) return r1 < r2 ? -1 : 1;

        int factionOrder = sortFactionTechsFirst(t1, t2);
        return factionOrder == 0 ? t1.getName().compareTo(t2.getName()) : factionOrder;
    }

    public static int sortFactionTechsFirst(TechnologyModel t1, TechnologyModel t2) {
        if (t1.getFaction().isEmpty() && t2.getFaction().isEmpty()) return 0;
        if (t1.getFaction().isPresent() && t2.getFaction().isPresent()) return 0;
        if (t1.getFaction().isPresent() && t2.getFaction().isEmpty()) return 1;
        return -1;
    }

    public static int sortFactionTechsLast(TechnologyModel t1, TechnologyModel t2) {
        return sortFactionTechsFirst(t1, t2) * -1;
    }

    public static final Comparator<TechnologyModel> sortByType = TechnologyModel::sortByType;

    public static int sortByType(TechnologyModel t1, TechnologyModel t2) {
        return t1.getType().compareTo(t2.getType());
    }

    public Optional<String> getBaseUpgrade() {
        return Optional.ofNullable(baseUpgrade);
    }

    public Optional<String> getFaction() {
        return Optional.ofNullable(faction);
    }

    public Optional<String> getRequirements() {
        return Optional.ofNullable(requirements);
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public String getRepresentation(boolean includeCardText) {
        String techName = getName();
        TechnologyType techType = getType();
        String techFaction = getFaction().orElse("");
        String factionEmoji = "";
        if (!techFaction.isBlank()) factionEmoji = Emojis.getFactionIconFromDiscord(techFaction);
        String techEmoji = Emojis.getEmojiFromDiscord(techType.toString().toLowerCase() + "tech");
        StringBuilder sb = new StringBuilder();
        sb.append(techEmoji).append("**").append(techName).append("**").append(factionEmoji);
        sb.append(getSource().emoji());
        if (includeCardText) sb.append("\n").append("> ").append(getText()).append("\n");
        return sb.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeRequirements) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        for (TechnologyType techType : types) {
            title.append(Emojis.getEmojiFromDiscord(techType.toString().toLowerCase() + "tech"));
        }
        title.append("**__").append(getName()).append("__**");
        if (getFaction().isPresent()) title.append(Emojis.getFactionIconFromDiscord(getFaction().get()));
        title.append(getSource().emoji());
        eb.setTitle(title.toString());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        if (includeRequirements) description.append("*Requirements: ").append(getRequirementsEmoji()).append("*\n");
        description.append(getText());
        eb.setDescription(description.toString());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());

        eb.setColor(getEmbedColor());
        return eb.build();
    }

    public String getNameRepresentation() {
        StringBuilder title = new StringBuilder();
        String factionEmoji = "";
        String techFaction = getFaction().orElse("");
        if (!techFaction.isBlank()) factionEmoji = Emojis.getFactionIconFromDiscord(techFaction);
        String techEmoji = Emojis.getEmojiFromDiscord(getType().toString().toLowerCase() + "tech");
        title.append(factionEmoji).append(techEmoji).append(" ").append(getName()).append(" ").append(getSource().emoji());
        return title.toString();
    }

    private Color getEmbedColor() {
        return switch (getType()) {
            case PROPULSION -> Color.blue; //Color.decode("#00FF00");
            case CYBERNETIC -> Color.yellow;
            case BIOTIC -> Color.green;
            case WARFARE -> Color.red;
            case UNITUPGRADE -> Color.black;
            default -> Color.white;
        };
    }

    public String getRequirementsEmoji() {
        if (getRequirements().isPresent()) {
            String reqs = getRequirements().get();
            reqs = reqs.replace("B", Emojis.PropulsionTech);
            reqs = reqs.replace("Y", Emojis.CyberneticTech);
            reqs = reqs.replace("G", Emojis.BioticTech);
            reqs = reqs.replace("R", Emojis.WarfareTech);
            return reqs;
        }
        return "None";
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString)
            || getName().toLowerCase().contains(searchString)
            || getFaction().orElse("").contains(searchString)
            || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder(getName());
        if (getFaction().isPresent()) sb.append(" (").append(getFaction().get()).append(")");
        sb.append(" [").append(getSource()).append("]");
        return sb.toString();
    }
}
