package ti4.model;

import org.jetbrains.annotations.Nullable;

import ti4.helpers.Emojis;

public class AgendaModel implements ModelInterface {
    private String alias;
    private String name;
    private String type;
    private String target;
    private String text1;
    private String text2;
    private String source;

    public AgendaModel() {
    }

    public boolean isValid() {
        return alias != null
            && name != null
            && type != null
            && target != null
            && text1 != null
            && text2 != null
            && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public String getText1() {
        return text1;
    }

    public String getText2() {
        return text2;
    }

    public String getSource() {
        return source;
    }

    public String getSourceEmoji() {
        switch (source) {
            case "absol":
                return Emojis.Absol;
            case "PoK":
                return Emojis.Agenda;
            default:
                return Emojis.AsyncTI4Logo;
        }
    }

    public String footnote() {
        switch (alias) {
            case "mutiny":
                return "Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`\n";
            case "seed_empire":
                return "Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`\n";
            case "censure":
                return "Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`\n";
            default:
                return null;
        }
    }

    public String getRepresentation(@Nullable Integer uniqueID) {
        StringBuilder sb = new StringBuilder();

        sb.append("**__");
        if (uniqueID != null) {
            sb.append("(").append(uniqueID).append(") - ");
        }
        sb.append(name).append("__** ");
        sb.append(getSourceEmoji());
        sb.append("\n");

        sb.append("> **").append(type).append(":** *").append(target).append("*\n");
        if (text1.length() > 0) {
            String arg = text1.replace("For:", "**For:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (text2.length() > 0) {
            String arg = text2.replace("Against:", "**Against:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (footnote() != null) sb.append(footnote());

        return sb.toString();
    }
}
