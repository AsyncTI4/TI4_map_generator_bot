package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

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

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        String name = getName() == null ? "" : getName();
        eb.setTitle("__" + name + "__", null);
        // eb.setColor(Color.yellow);
        eb.setDescription("Type: " + getType() + "  Requires: " + getRequirements());
        eb.addField("", getText(), true);
        eb.setFooter("ID: " + getAlias());
        return eb.build();
    }
}
