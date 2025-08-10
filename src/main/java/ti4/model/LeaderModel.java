package ti4.model;

import java.awt.*;
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
    private String ID;
    private String type;
    private String faction;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private String title;
    private String abilityName;
    private String abilityWindow;
    private String abilityText;
    private String unlockCondition;
    private String flavourText;
    private String imageURL;
    private ComponentSource source;
    private List<String> searchTags = new ArrayList<>();
    private String homebrewReplacesID;

    @Override
    public boolean isValid() {
        return ID != null
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
        return getID();
    }

    public String getShortName() {
        return Optional.ofNullable(shortName).orElse(getName());
    }

    public boolean getShrinkName() {
        return Optional.ofNullable(shrinkName).orElse(false);
    }

    public TI4Emoji getLeaderEmoji() {
        if (getHomebrewReplacesID().isPresent()) {
            return LeaderEmojis.getLeaderEmoji(getHomebrewReplacesID().get());
        }
        return LeaderEmojis.getLeaderEmoji(getID());
    }

    public Optional<String> getAbilityName() {
        return Optional.ofNullable(abilityName);
    }

    public Optional<String> getFlavourText() {
        return Optional.ofNullable(flavourText);
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public String getRepresentation(boolean includeTitle, boolean includeAbility, boolean includeUnlockCondition) {
        StringBuilder representation = new StringBuilder();
        representation.append(getLeaderEmoji()).append(" **").append(getName()).append("**");

        if (includeTitle) representation.append(": ").append(getTitle()); // add title
        if (includeAbility && Constants.HERO.equals(getType()))
            representation
                    .append(" - ")
                    .append("__**")
                    .append(getAbilityName())
                    .append("**__"); // add hero ability name
        if (includeAbility)
            representation
                    .append(" - *")
                    .append(getAbilityWindow())
                    .append("* ")
                    .append(getAbilityText()); // add ability
        if (includeUnlockCondition) representation.append(" *Unlock:* ").append(getUnlockCondition());

        return representation.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, true, false, false);
    }

    public MessageEmbed getRepresentationEmbed(
            boolean includeID, boolean includeFactionType, boolean showUnlockConditions, boolean includeFlavourText) {
        EmbedBuilder eb = new EmbedBuilder();
        FactionModel factionModel = Mapper.getFaction(getFaction());
        String factionEmoji = FactionEmojis.getFactionIcon(getFaction()).toString();
        if (factionModel != null)
            factionEmoji = FactionEmojis.getFactionIcon(factionModel.getAlias()).toString();

        String factionName = getFaction();
        if (factionModel != null) factionName = factionModel.getFactionName();

        // TITLE
        String title = factionEmoji + " __**" + getName() + "**__ " + LeaderEmojis.getLeaderTypeEmoji(type) + " "
                + getTitle() + getSource().emoji();
        eb.setTitle(title);

        Emoji emoji = getLeaderEmoji().asEmoji();
        if (emoji instanceof CustomEmoji customEmoji) {
            eb.setThumbnail(customEmoji.getImageUrl());
        }

        // DESCRIPTION
        StringBuilder description = new StringBuilder();
        if (includeFactionType) {
            description.append(factionEmoji).append(" ").append(factionName).append(" ");
            description.append(" ").append(StringUtils.capitalize(getType()));
        }
        if (showUnlockConditions && !"agent".equals(getType()))
            description.append("\n*Unlock: ").append(getUnlockCondition()).append("*");
        eb.setDescription(description.toString());

        // FIELDS
        String fieldTitle = getAbilityName().orElse(" ") + "\n**" + getAbilityWindow() + "**";
        String fieldContent = getAbilityText();
        eb.addField(fieldTitle, fieldContent, false);
        if (includeFlavourText && getFlavourText().isPresent()) eb.addField(" ", "*" + getFlavourText() + "*", false);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());

        eb.setColor(Color.black);
        return eb.build();
    }

    public String getNameRepresentation() {
        return FactionEmojis.getFactionIcon(getFaction()) + " " + LeaderEmojis.getLeaderTypeEmoji(getType())
                + getLeaderEmoji() + " " + getName() + " " + " (" + getTitle() + ") "
                + getSource().emoji();
    }

    public boolean search(String searchString) {
        if (searchString == null) return true;
        searchString = searchString.toLowerCase();
        return getID().toLowerCase().contains(searchString)
                || getName().toLowerCase().contains(searchString)
                || getTitle().toLowerCase().contains(searchString)
                || getAbilityName().orElse("").toLowerCase().contains(searchString)
                || getAbilityWindow().toLowerCase().contains(searchString)
                || getAbilityText().toLowerCase().contains(searchString)
                || getUnlockCondition().toLowerCase().contains(searchString)
                || getAutoCompleteName().toLowerCase().contains(searchString)
                || getSource().toString().toLowerCase().contains(searchString)
                || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getFaction() + " " + getType() + ") ["
                + getSource().toString() + "]";
    }
}
