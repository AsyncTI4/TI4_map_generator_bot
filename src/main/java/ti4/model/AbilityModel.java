package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

@Data
public class AbilityModel implements ModelInterface, EmbeddableModel {

    private String id;
    private String name;
    private String shortName;
    private String faction;
    private String permanentEffect;
    private String window;
    private String windowEffect;
    private String source;
    private List<String> searchTags = new ArrayList<>();

    @Override
    public boolean isValid() {
        return id != null
                && name != null
                && faction != null
                // && permanentEffect != null
                // && window != null
                // && windowEffect != null
                && source != null;
    }

    @Override
    public String getAlias() {
        return getId();
    }

    public String getShortName() {
        return Optional.ofNullable(shortName).orElse(getName());
    }

    public Optional<String> getPermanentEffect() {
        return Optional.ofNullable(permanentEffect);
    }

    public Optional<String> getWindow() {
        return Optional.ofNullable(window);
    }

    public Optional<String> getWindowEffect() {
        return Optional.ofNullable(windowEffect);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(getFactionEmoji());
        title.append(" __**").append(getName()).append("**__");
        title.append(getSourceEmoji());
        eb.setTitle(title.toString());

        //DESCRIPTION
        if (getPermanentEffect().isPresent()) eb.setDescription(getPermanentEffect().get());

        //FIELDS
        if (getWindow().isPresent()) eb.addField(getWindow().get(), getWindowEffect().orElse(""), false);

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());
        
        eb.setColor(Color.black);
        return eb.build();
    }

    public String getRepresentation() {
        String abilityName = getName();
        String abilitySourceFaction = getFaction();
        String abilityRawModifier = getPermanentEffect().orElse("");
        String abilityWindow = getWindow().orElse("");
        String abilityText = getWindowEffect().orElse("");

        StringBuilder sb = new StringBuilder();
        sb.append(Emojis.getFactionIconFromDiscord(abilitySourceFaction)).append("__**").append(abilityName).append("**__");
        if (!abilityRawModifier.isBlank()) sb.append(": ").append(abilityRawModifier);
        if (!abilityWindow.isBlank() || !abilityText.isBlank()) sb.append("\n> *").append(abilityWindow).append("*:\n> ").append(abilityText);

        return sb.toString();
    }

    @Override
    public boolean search(String searchString) {
        return getId().contains(searchString)
                || getName().toLowerCase().contains(searchString)
                || getFaction().toLowerCase().contains(searchString)
                || getSource().toLowerCase().contains(searchString)
                || getSearchTags().contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        sb.append(" (").append(getFaction()).append(")");
        sb.append(" [").append(getSource()).append("]");
        return sb.toString();
    }

    public String getFactionEmoji() {
        return Emojis.getFactionIconFromDiscord(getFaction());
    }

    private String getSourceEmoji() {
        return switch (source) {
            case "ds" -> Emojis.DiscordantStars;
            default -> "";
        };
    }
}
