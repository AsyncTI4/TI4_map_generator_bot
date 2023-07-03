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

    public AgendaModel() {}

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

    public String getRepresentation(@Nullable Integer uniqueID) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("**__");
        if (uniqueID != null) {
            sb.append("(").append(uniqueID).append(") - ");
        }
        sb.append(name).append("__** ");
        switch (source) {
            case "absol" -> sb.append(Emojis.Absol);
            case "PoK" -> sb.append(Emojis.AgendaWhite);
            default -> sb.append(Emojis.AsyncTI4Logo);
        }
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

        switch (alias) {
            case ("mutiny") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`").append("\n");
            case ("seed_empire") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`").append("\n");
            case ("censure") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`").append("\n");
        }

        return sb.toString();
    }
}
