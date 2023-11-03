package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import ti4.helpers.Emojis;

@Data
public class TechnologyModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private TechnologyType type;
    private String requirements;
    private String faction;
    private String baseUpgrade;
    private String source;
    private String text;
    private String homebrewReplacesID;
    private List<String> searchTags = new ArrayList<>();

    public enum TechnologyType {
        UNITUPGRADE, PROPULSION, BIOTIC, CYBERNETIC, WARFARE, NONE;

            public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public boolean isValid() {
        return alias != null
            && name != null
            && type != null
            && getRequirements() != null
            && getFaction() != null
            && getBaseUpgrade() != null
            && source != null
            && text != null;
    }

    public static final Comparator<TechnologyModel> sortByTechRequirements = (tech1, tech2) -> {
        return TechnologyModel.sortTechsByRequirements(tech1, tech2);
    };

    public static int sortTechsByRequirements(TechnologyModel t1, TechnologyModel t2) {
        int r1 = t1.getRequirements().orElse("").length();
        int r2 = t2.getRequirements().orElse("").length();
        if (r1 != r2) return r1 < r2 ? -1 : 1;

        int factionOrder = sortFactionTechsFirst(t1, t2);
        return factionOrder == 0 ? t1.getName().compareTo(t2.getName()) : factionOrder;
    }

    public static int sortFactionTechsFirst(TechnologyModel t1, TechnologyModel t2) {
        if (t1.getFaction().isEmpty() && t2.getFaction().isEmpty()) return 0;
        if (!t1.getFaction().isEmpty() && !t2.getFaction().isEmpty()) return 0;
        if (!t1.getFaction().isEmpty() && t2.getFaction().isEmpty()) return 1;
        return -1;
    }

    public static int sortFactionTechsLast(TechnologyModel t1, TechnologyModel t2) {
        return sortFactionTechsFirst(t1, t2) * -1;
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
        if (includeCardText) sb.append("\n").append("> ").append(getText()).append("\n");
        return sb.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeRequirements) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        String factionEmoji = "";
        String techFaction = getFaction().orElse("");
        if (!techFaction.isBlank()) factionEmoji = Emojis.getFactionIconFromDiscord(techFaction);
        String techEmoji = Emojis.getEmojiFromDiscord(getType().toString().toLowerCase() + "tech");
        eb.setTitle(techEmoji + "**__" + getName() + "__**" + factionEmoji + getSourceEmoji());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        if (includeRequirements) description.append("*Requirements: ").append(getRequirementsEmoji()).append("*\n");
        description.append(getText());
        eb.setDescription(description.toString());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());

        eb.setColor(getEmbedColour());
        return eb.build();
    }

    private Color getEmbedColour() {
        return switch (getType()) {
            case PROPULSION -> Color.blue; //Color.decode("#00FF00");
            case CYBERNETIC -> Color.yellow;
            case BIOTIC -> Color.green;
            case WARFARE -> Color.red;
            case UNITUPGRADE -> Color.black;
            default -> Color.white;
        };
    }

    private String getSourceEmoji() {
        return switch (getSource()) {
            case "absol" -> Emojis.Absol;
            case "ds" -> Emojis.DiscordantStars;
            default -> "";
        };
    }

    public String getRequirementsEmoji() {
        if (getRequirements().isPresent()) {
            String requirements = getRequirements().get();
            requirements = requirements.replace("B", Emojis.PropulsionTech);
            requirements = requirements.replace("Y", Emojis.CyberneticTech);
            requirements = requirements.replace("G", Emojis.BioticTech);
            requirements = requirements.replace("R", Emojis.WarfareTech);
            return requirements;
        } 
        return "None";
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getFaction().orElse("").contains(searchString) || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder(getName());
        sb.append(" (");
        if (!getFaction().orElse("").isBlank()) sb.append(getFaction()).append(") (");
        sb.append(getSource()).append(")");
        return sb.toString();
    }
}
