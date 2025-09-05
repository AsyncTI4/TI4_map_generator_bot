package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.Nullable;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

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
    private String forEmoji;
    private String againstEmoji;
    private String mapText;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null && name != null && validateCategory() && type != null && text1 != null && source != null;
    }

    private boolean validateCategory() {
        switch (getCategory()) {
            case "faction" -> {
                return Mapper.isValidFaction(getCategoryDescription());
            }
            case "event" -> {
                return Stream.of("immediate", "permanent", "temporary")
                        .anyMatch(s -> s.equalsIgnoreCase(getCategoryDescription()));
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

    private String getCategory() {
        return Optional.ofNullable(category).orElse("");
    }

    private String getCategoryDescription() {
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

    public String getForEmoji() {
        return Optional.ofNullable(forEmoji).orElse("👍");
    }

    public String getAgainstEmoji() {
        return Optional.ofNullable(againstEmoji).orElse("👎");
    }

    public String getMapText() {
        return Optional.ofNullable(mapText).orElse("");
    }

    public String footnote() {
        return switch (alias) {
            case "mutiny" ->
                "Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`\n";
            case "seed_empire" ->
                "Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`\n";
            case "censure" ->
                "Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`\n";
            case Constants.VOICE_OF_THE_COUNCIL_ID ->
                "Use this command to change the electee: `/omegaphase elect_voice_of_the_council`\n";
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
        sb.append(source.emoji());
        sb.append("\n");

        sb.append("> **").append(type).append(":** *").append(target).append("*\n");
        if (!getText1().isEmpty()) {
            String arg = getText1().replace("For:", "**For:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (!getText2().isEmpty()) {
            String arg = getText2().replace("Against:", "**Against:**");
            sb.append("> ").append(arg).append("\n");
        }
        if (footnote() != null) sb.append(footnote());

        return sb.toString();
    }

    public boolean displayElectedFaction() {
        return target.contains("Elect Player");
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();
        String name = this.name == null ? "" : this.name;
        eb.setTitle(CardEmojis.Agenda + "__" + name + "__" + source.emoji(), null);
        eb.setColor(Color.blue);

        // DESCRIPTION
        StringBuilder text = new StringBuilder("**" + getType() + ":** *" + getTarget() + "*\n");
        if (!getText1().isEmpty()) {
            String arg = getText1().replace("For:", "__**For:**__");
            text.append(arg).append("\n");
        }
        if (!getText2().isEmpty()) {
            String arg = getText2().replace("Against:", "__**Against:**__");
            text.append(arg).append("\n");
        }
        eb.setDescription(text.toString());

        if (includeID) eb.setFooter("ID: " + alias + "  Source: " + source);
        return eb.build();
    }

    public static MessageEmbed agendaIsInSomeonesHandEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        String name = "No info available";
        eb.setTitle(CardEmojis.Agenda + "__" + name + "__", null);
        eb.setColor(Color.blue);

        // DESCRIPTION
        eb.setDescription("This agenda is in somebody's hand");
        return eb.build();
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " [" + source + "]";
    }
}
