package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

@Data
public class AbilityModel implements ModelInterface, EmbeddableModel {

    private String id;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private String faction;
    private String permanentEffect;
    private String window;
    private String windowEffect;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();
    private String homebrewReplacesID;

    @Override
    public boolean isValid() {
        return id != null && name != null && faction != null && source != null;
    }

    @Override
    public String getAlias() {
        return id;
    }

    public String getShortName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shortName).orElse(name);
        }
        return Optional.ofNullable(shortName)
                .orElse(Mapper.getAbility(getHomebrewReplacesID().get()).getShortName());
    }

    public boolean getShrinkName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shrinkName).orElse(false);
        }
        return Optional.ofNullable(shrinkName)
                .orElse(Mapper.getAbility(getHomebrewReplacesID().get()).getShrinkName());
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

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = getFactionEmoji() + " __**" + name + "**__" + source.emoji();
        eb.setTitle(title);

        // DESCRIPTION
        if (getPermanentEffect().isPresent())
            eb.setDescription(getPermanentEffect().get());

        // FIELDS
        if (getWindow().isPresent())
            eb.addField(getWindow().get(), getWindowEffect().orElse(""), false);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(id).append("    Source: ").append(source);
        eb.setFooter(footer.toString());

        eb.setColor(Color.black);
        return eb.build();
    }

    public String getNameRepresentation() {
        return getFactionEmoji() + " " + name + " " + source.emoji();
    }

    public String getRepresentation() {
        String abilityName = name;
        String abilitySourceFaction = faction;
        String abilityRawModifier = getPermanentEffect().orElse("");
        String abilityWindow = getWindow().orElse("");
        String abilityText = getWindowEffect().orElse("");

        StringBuilder sb = new StringBuilder();
        sb.append(FactionEmojis.getFactionIcon(abilitySourceFaction))
                .append("__**")
                .append(abilityName)
                .append("**__");
        if (!abilityRawModifier.isBlank()) sb.append(": ").append(abilityRawModifier);
        if (!abilityWindow.isBlank() || !abilityText.isBlank())
            sb.append("\n> *").append(abilityWindow).append("*:\n> ").append(abilityText);

        return sb.toString();
    }

    @Override
    public boolean search(String searchString) {
        return id.contains(searchString)
                || name.toLowerCase().contains(searchString)
                || faction.toLowerCase().contains(searchString)
                || source.toString().toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        return name + " (" + faction + ")" + " [" + source + "]";
    }

    public TI4Emoji getFactionEmoji() {
        return FactionEmojis.getFactionIcon(faction);
    }
}
