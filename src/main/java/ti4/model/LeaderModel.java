package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.TI4Emoji;

@Data
public class LeaderModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String type;
    private String faction;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private String tfName;
    private String tfShortName;
    private Boolean tfShrinkName;
    private String title;
    private String tfTitle;
    private String abilityName;
    private String abilityWindow;
    private String tfAbilityWindow;
    private String abilityText;
    private String notes;
    private String tfAbilityText;
    private String tfNotes;
    private String unlockCondition;
    private String flavourText;
    private String imageURL;
    private String imageBackURL;
    private String tfImageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();
    private String homebrewReplacesID;

    @Override
    public boolean isValid() {
        return id != null
                && type != null
                && faction != null
                && name != null
                && title != null
                && abilityWindow != null
                && abilityText != null
                && unlockCondition != null
                && source != null;
    }

    @Override
    public String getAlias() {
        return id;
    }

    public String getShortName() {
        return Optional.ofNullable(shortName).orElse(name);
    }

    public boolean getShrinkName() {
        return Optional.ofNullable(shrinkName).orElse(false);
    }

    public TI4Emoji getLeaderEmoji() {
        if (getHomebrewReplacesID().isPresent()) {
            return LeaderEmojis.getLeaderEmoji(getHomebrewReplacesID().get());
        }
        return LeaderEmojis.getLeaderEmoji(id);
    }

    public Optional<String> getTFName() {
        return Optional.ofNullable(tfName);
    }

    public String getTFNameIfAble() {
        if (tfName != null) {
            return tfName;
        }
        return name;
    }

    public String getTFShortName() {
        return Optional.ofNullable(tfShortName).orElse(getTFNameIfAble());
    }

    public boolean getTFShrinkName() {
        return Optional.ofNullable(tfShrinkName).orElse(false);
    }

    public String getLeaderPositionAndFaction() {
        return StringUtils.capitalize(faction) + " " + StringUtils.capitalize(type);
    }

    public Optional<String> getTFTitle() {
        return Optional.ofNullable(tfTitle);
    }

    public Optional<String> getAbilityName() {
        return Optional.ofNullable(abilityName);
    }

    public Optional<String> getTFAbilityWindow() {
        if (tfAbilityWindow == null) {
            return Optional.ofNullable(abilityWindow);
        }
        return Optional.of(tfAbilityWindow);
    }

    public Optional<String> getTFAbilityText() {
        if (tfAbilityText == null) {
            return Optional.ofNullable(abilityText);
        }
        return Optional.of(tfAbilityText);
    }

    public boolean isGenome() {
        return Mapper.getDeck(Constants.TF_GENOME).getNewDeck().contains(id);
    }

    public boolean isParadigm() {
        return Mapper.getDeck(Constants.TF_PARADIGM).getNewDeck().contains(id);
    }

    private Optional<String> getFlavourText() {
        return Optional.ofNullable(flavourText);
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public String getRepresentation(boolean includeTitle, boolean includeAbility, boolean includeUnlockCondition) {
        StringBuilder representation = new StringBuilder();
        representation.append(getLeaderEmoji()).append(" **").append(name).append("**");

        if (includeTitle) representation.append(": ").append(title); // add title
        if (includeAbility && Constants.HERO.equals(type))
            representation
                    .append(" - ")
                    .append("__**")
                    .append(getAbilityName())
                    .append("**__"); // add hero ability name
        if (includeAbility)
            representation.append(" - *").append(abilityWindow).append("* ").append(abilityText); // add ability
        if (includeUnlockCondition) representation.append(" *Unlock:* ").append(unlockCondition);

        return representation.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, true, false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean useTwilightsFallText) {
        return getRepresentationEmbed(false, true, false, false, useTwilightsFallText);
    }

    public MessageEmbed getRepresentationEmbed(
            boolean includeID, boolean includeFactionType, boolean showUnlockConditions, boolean includeFlavourText) {
        return getRepresentationEmbed(includeID, includeFactionType, showUnlockConditions, includeFlavourText, false);
    }

    public MessageEmbed getRepresentationEmbed(
            boolean includeID,
            boolean includeFactionType,
            boolean showUnlockConditions,
            boolean includeFlavourText,
            boolean useTwilightsFallText) {
        EmbedBuilder eb = new EmbedBuilder();
        FactionModel factionModel = Mapper.getFaction(faction);
        String factionEmoji = FactionEmojis.getFactionIcon(faction).toString();
        if (factionModel != null)
            factionEmoji = FactionEmojis.getFactionIcon(factionModel.getAlias()).toString();

        String factionName = faction;
        if (factionModel != null) factionName = factionModel.getFactionName();

        // TITLE
        String title_name_component = "";
        String title_subtitle_component = "";
        if (useTwilightsFallText) {
            if (getTFName().isPresent() && !getTFName().get().isBlank()) {
                title_name_component = getTFName().get();
            } else {
                title_name_component = name;
            }
            if (getTFTitle().isPresent() && !getTFTitle().get().isBlank()) {
                title_subtitle_component = getTFTitle().get();
            } else {
                title_subtitle_component = title;
            }
        } else {
            title_name_component = name;
            title_subtitle_component = title;
        }
        String title = factionEmoji + " __**" + title_name_component + "**__ " + LeaderEmojis.getLeaderTypeEmoji(type)
                + " " + title_subtitle_component + source.emoji();
        eb.setTitle(title);

        Emoji emoji = getLeaderEmoji().asEmoji();
        if (emoji instanceof CustomEmoji customEmoji) {
            eb.setThumbnail(customEmoji.getImageUrl());
        }

        // DESCRIPTION
        StringBuilder description = new StringBuilder();
        if (includeFactionType) {
            description.append(factionEmoji).append(" ").append(factionName).append(" ");
            description.append(" ").append(StringUtils.capitalize(type));
        }
        if (showUnlockConditions && !"agent".equals(type))
            description.append("\n*Unlock: ").append(unlockCondition).append("*");
        eb.setDescription(description.toString());

        // FIELDS
        String abilityName = useTwilightsFallText ? " " : getAbilityName().orElse(" ");
        String abilityWindow =
                useTwilightsFallText ? getTFAbilityWindow().orElse(this.abilityWindow) : this.abilityWindow;
        String fieldTitle = abilityName + "\n**" + abilityWindow + "**";
        String fieldContent = useTwilightsFallText ? getTFAbilityText().orElse(abilityText) : abilityText;
        if (useTwilightsFallText && (tfNotes != null)) {
            fieldContent += "\n-# [" + tfNotes + "]";
        } else if (!useTwilightsFallText && (notes != null)) {
            fieldContent += "\n-# [" + notes + "]";
        }
        eb.addField(fieldTitle, fieldContent, false);
        if (includeFlavourText && getFlavourText().isPresent()) eb.addField(" ", "*" + getFlavourText() + "*", false);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(id).append("    Source: ").append(source);
        eb.setFooter(footer.toString());

        eb.setColor(Color.black);
        return eb.build();
    }

    public String getNameRepresentation() {
        return FactionEmojis.getFactionIcon(faction) + " " + LeaderEmojis.getLeaderTypeEmoji(type)
                + getLeaderEmoji() + " " + name + " " + " (" + title + ") "
                + source.emoji();
    }

    public boolean search(String searchString) {
        if (searchString == null) return true;
        searchString = searchString.toLowerCase();
        return id.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || getTFName().orElse("").toLowerCase().contains(searchString)
                || title.toLowerCase().contains(searchString)
                || getTFTitle().orElse("").toLowerCase().contains(searchString)
                || getAbilityName().orElse("").toLowerCase().contains(searchString)
                || abilityWindow.toLowerCase().contains(searchString)
                || getTFAbilityWindow().orElse("").toLowerCase().contains(searchString)
                || abilityText.toLowerCase().contains(searchString)
                || getTFAbilityText().orElse("").toLowerCase().contains(searchString)
                || unlockCondition.toLowerCase().contains(searchString)
                || getAutoCompleteName().toLowerCase().contains(searchString)
                || source.toString().toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " (" + faction + " " + type + ") [" + source.toString() + "]";
    }
}
