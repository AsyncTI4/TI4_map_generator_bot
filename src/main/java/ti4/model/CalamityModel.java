package ti4.model;

import java.awt.Color;
import java.util.List;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class CalamityModel implements ModelInterface, EmbeddableModel {

    private String id;
    private String name;
    private String type; // collapse/disaster
    private String permanentEffect;
    private List<String> effects;
    private String flavourText;
    private ComponentSource source;
    private String version;

    @Override
    public String getAlias() {
        return id;
    }

    private int getEffectCount() {
        return effects != null ? effects.size() : 0;
    }

    private String getEffectWindow(String effect) {
        return StringUtils.substringBefore(effect, ":").trim();
    }

    private String getEffectWithoutWindow(String effect) {
        return StringUtils.substringAfter(effect, ":").trim();
    }

    private String getEffectFormatted(String effect) {
        return String.format("***%s:***\n> %s", getEffectWindow(effect), getEffectWithoutWindow(effect));
    }

    private String getEffect() {
        return getEffect(1);
    }

    private String getEffect(int effectNumber) {
        if (effectNumber < 1 || effectNumber > getEffectCount()) {
            throw new IllegalArgumentException("CalamityModel.getEffect(): effectNumber out of bounds");
        }
        return getEffectFormatted(effects.get(effectNumber - 1));
    }

    public String getEffectWithWindowRegex(String regex) {
        for (String effect : effects) {
            if (containsEffectWindowRegex(regex)) {
                return getEffectFormatted(effect);
            }
        }
        return null;
    }

    public boolean containsEffectWindowRegex(String regex) {
        for (String effect : effects) {
            if (getEffectWindow(effect).toLowerCase().matches(".*" + regex.toLowerCase() + ".*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = CardEmojis.Calamity + "__**" + name + "**__" + source.emoji();
        eb.setTitle(title);

        // DESCRIPTION
        eb.setDescription(permanentEffect);

        // EFFECTS
        for (String effect : effects) {
            eb.addField("", getEffectFormatted(effect), false);
        }

        // FLAVOUR TEXT
        if (includeFlavourText && StringUtils.isNotBlank(flavourText)) {
            eb.addField("", flavourText, false);
        }

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("Version: ")
                    .append(version)
                    .append("    ID: ")
                    .append(id)
                    .append("    Source: ")
                    .append(source);
        eb.setFooter(footer.toString());

        eb.setColor(Color.orange);
        return eb.build();
    }

    @Override
    public boolean search(String searchString) {
        searchString = searchString.toLowerCase();
        return id.contains(searchString)
                || name.toLowerCase().contains(searchString)
                || type.toLowerCase().contains(searchString)
                || source.toString().toLowerCase().contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        return "%s (%s) [%s]".formatted(name, type, source);
    }

    @Override
    public boolean isValid() {
        return id != null
                && !id.isEmpty()
                && name != null
                && !name.isEmpty()
                && type != null
                && !type.isEmpty()
                && type.toLowerCase().matches("collapse|disaster")
                && effects != null
                && !effects.isEmpty()
                && source != null
                && version != null
                && !version.isEmpty();
    }
}
