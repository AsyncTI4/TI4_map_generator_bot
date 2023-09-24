package ti4.model;

import java.awt.Color;
import java.util.Comparator;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

@Data
public class TechnologyModel implements ModelInterface {
    private String alias;
    private String name;
    private TechnologyType type;
    private String requirements;
    private String faction;
    private String baseUpgrade;
    private String source;
    private String text;

    public enum TechnologyType {
        UNITUPGRADE, PROPULSION, BIOTIC, CYBERNETIC, WARFARE, NONE;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public boolean isValid() {
        return alias != null
            && name != null
            && text != null
            && source != null
            && baseUpgrade != null
            && faction != null
            && requirements != null
            && type != null;
    }

    public static final Comparator<TechnologyModel> sortByTechRequirements = (tech1, tech2) -> {
        return TechnologyModel.sortTechsByRequirements(tech1, tech2);
    };

    public static int sortTechsByRequirements(TechnologyModel t1, TechnologyModel t2) {
        int r1 = t1.getRequirements().length();
        int r2 = t2.getRequirements().length();
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

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeRequirements) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        String factionEmoji = "";
        String techFaction = getFaction();
        if (!techFaction.isBlank()) factionEmoji = Helper.getFactionIconFromDiscord(techFaction);
        String techEmoji = Helper.getEmojiFromDiscord(getType().toString().toLowerCase() + "tech");
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
        switch (getType()) {
            case NONE -> {
                return "None";
            }
            case PROPULSION -> {
                return switch (getRequirements()) {
                    case "B" -> Emojis.PropulsionTech;
                    case "BB" -> Emojis.Propulsion2;
                    case "BBB" -> Emojis.Propulsion3;
                    default -> "None";
                };
            }
            case CYBERNETIC -> {
                return switch (getRequirements()) {
                    case "Y" -> Emojis.CyberneticTech;
                    case "YY" -> Emojis.Cybernetic2;
                    case "YYY" -> Emojis.Cybernetic3;
                    default -> "None";
                };
            }
            case BIOTIC -> {
                return switch (getRequirements()) {
                    case "G" -> Emojis.BioticTech;
                    case "GG" -> Emojis.Biotic2;
                    case "GGG" -> Emojis.Biotic3;
                    default -> "None";
                };
            }
            case WARFARE -> {
                return switch (getRequirements()) {
                    case "R" -> Emojis.WarfareTech;
                    case "RR" -> Emojis.Warfare2;
                    case "RRR" -> Emojis.Warfare3;
                    default -> "None";
                };
            }
            case UNITUPGRADE -> {
                String unitType = getBaseUpgrade().isEmpty() ? getAlias() : getBaseUpgrade();
                return switch (unitType) {
                    case "inf2" -> Emojis.Biotic2;
                    case "ff2" -> Emojis.BioticTech + Emojis.PropulsionTech;
                    case "pds2" -> Emojis.WarfareTech + Emojis.CyberneticTech;
                    case "sd2" -> Emojis.Cybernetic2;
                    case "dd2" -> Emojis.Warfare2;
                    case "cr2" -> Emojis.BioticTech + Emojis.CyberneticTech + Emojis.WarfareTech;
                    case "cv2" -> Emojis.Propulsion2;
                    case "dn2" -> Emojis.Propulsion2 + Emojis.CyberneticTech;
                    case "ws" -> Emojis.CyberneticTech + Emojis.Warfare3;
                    case "fs" -> Emojis.BioticTech + Emojis.PropulsionTech + Emojis.CyberneticTech;
                    default -> "None";
                };
            }
        }
        return "None";
    }
}
