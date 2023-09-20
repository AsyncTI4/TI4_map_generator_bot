package ti4.model;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import ti4.generator.Mapper;
import ti4.helpers.Emojis;

public class AgendaModel implements ModelInterface {
    private String alias;
    private String name;
    private String category;
    private String categoryDescription;
    private String type;
    private String target;
    private String text1;
    private String text2;
    private String mapText;
    private String source;

    public boolean isValid() {
        return alias != null
            && name != null
            && validateCategory()
            && type != null
            && text1 != null
            && source != null;
    }

    private boolean validateCategory() {
        if (category == null) return true;
        switch (category) {
            case "faction" -> {
                return Mapper.isFaction(categoryDescription);
            }
            case "event" -> {
                return List.of("immediate", "permanent", "temporary").stream().anyMatch(s -> s.equalsIgnoreCase(categoryDescription));
            }
            default -> {
                return true;
            }
        }
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getCategoryDescription() {
        return categoryDescription;
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

    public String getMapText() {
        return mapText;
    }

    public String getSource() {
        return source;
    }

    public String getSourceEmoji() {
      return switch (source.toLowerCase()) {
        case "absol" -> Emojis.Absol;
        case "pok" -> Emojis.Agenda;
        default -> Emojis.AsyncTI4Logo;
      };
    }

    public String footnote() {
      return switch (alias) {
        case "mutiny" -> "Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`\n";
        case "seed_empire" -> "Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`\n";
        case "censure" -> "Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`\n";
        default -> null;
      };
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

    public boolean displayElectedFaction() {
        return "Elect Player".equalsIgnoreCase(target);
    }
}
