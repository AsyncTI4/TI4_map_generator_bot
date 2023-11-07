package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;

@Data
public class AgendaModel implements ModelInterface, EmbeddableModel {
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
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null
            && name != null
            && validateCategory()
            && type != null
            && text1 != null
            && source != null;
    }

    private boolean validateCategory() {
        switch (getCategory()) {
            case "faction" -> {
                return Mapper.isFaction(getCategoryDescription());
            }
            case "event" -> {
                return List.of("immediate", "permanent", "temporary").stream().anyMatch(s -> s.equalsIgnoreCase(getCategoryDescription()));
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
        return Optional.ofNullable(category).orElse("");
    }

    public String getCategoryDescription() {
        return Optional.ofNullable(categoryDescription).orElse("");
    }

    public String getType() {
        return Optional.ofNullable(type).orElse("");
    }

    public String getTarget() {
        return Optional.ofNullable(target).orElse("");
    }

    public String getText1() {
        return Optional.ofNullable(text1).orElse("");
    }

    public String getText2() {
        return Optional.ofNullable(text2).orElse("");
    }

    public String getMapText() {
        return Optional.ofNullable(mapText).orElse("");
    }

    public String getSource() {
        return Optional.ofNullable(source).orElse("");
    }

    public String getSourceEmoji() {
      return switch (source.toLowerCase()) {
        case "absol" -> Emojis.Absol;
        case "pok" -> Emojis.Agenda;
        case "ignis_aurora" -> Emojis.IgnisAurora;
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
        if (getText1().length() > 0) {
            String arg = getText1().replace("For:", "**For:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (getText2().length() > 0) {
            String arg = getText2().replace("Against:", "**Against:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (footnote() != null) sb.append(footnote());

        return sb.toString();
    }

    public boolean displayElectedFaction() {
        return "Elect Player".equalsIgnoreCase(target);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();
        String name = getName() == null ? "" : getName();
        eb.setTitle(Emojis.Agenda + "__" + name + "__" + getSourceEmoji(), null);
        eb.setColor(Color.blue);
        eb.setDescription(getType() + "\n" + getTarget());
        eb.addField("", getText1() + "\n" + getText2(), false);
        if (includeID) eb.setFooter("ID: " + getAlias() + "  Source: " + getSource());
        return eb.build();
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getSource() + ")";
    }
}
