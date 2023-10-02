package ti4.model;

import java.awt.Color;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

@Data
public class LeaderModel implements ModelInterface { 
    private String ID;
    private String type;
    private String faction;
    private String name;
    private String title;
    private String abilityName;
    private String abilityWindow;
    private String abilityText;
    private String unlockCondition;
    private String flavourText;
    private String emoji;
    private String source;

    @Override
    public boolean isValid() {
        return ID != null
            && type != null
            && faction != null
            && name != null
            && title != null
            // && abilityName != null
            && abilityWindow != null
            && abilityText != null
            && unlockCondition != null
            // && flavourText != null
            // && emoji != null
            && source != null;
    }

    @Override
    public String getAlias() {
        return getID();
    }

    public String getLeaderEmoji() {
        return Optional.ofNullable(getEmoji()).orElse(Helper.getEmojiFromDiscord(getID()));
    }

    public String getAbilityName() {
        return Optional.ofNullable(abilityName).orElse("");
    }

    public String getFlavourText() {
        return Optional.ofNullable(flavourText).orElse("");
    }

    public String getRepresentation(boolean includeTitle, boolean includeAbility, boolean includeUnlockCondition) {
        StringBuilder representation = new StringBuilder();
        representation.append(getLeaderEmoji()).append(" **").append(getName()).append("**");
        
        if (includeTitle) representation.append(": ").append(getTitle()); //add title
        if (includeAbility && Constants.HERO.equals(getType())) representation.append(" - ").append("__**").append(getAbilityName()).append("**__"); //add hero ability name
        if (includeAbility) representation.append(" - *").append(getAbilityWindow()).append("* ").append(getAbilityText()); //add ability
        if (includeUnlockCondition) representation.append(" *Unlock:* ").append(getUnlockCondition());

        return representation.toString();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean showUnlockConditions, boolean includeFlavourText) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(getLeaderEmoji());
        title.append(" __**").append(getName()).append("**__").append(" - ").append(getTitle());
        title.append(getSourceEmoji());
        eb.setTitle(title.toString());

        Emoji emoji = Emoji.fromFormatted(getLeaderEmoji());
        if (emoji instanceof CustomEmoji) {
            CustomEmoji customEmoji = (CustomEmoji) emoji;
            eb.setThumbnail(customEmoji.getImageUrl());
        }

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        FactionModel faction = Mapper.getFactionSetup(getFaction());
        if (faction != null) {
            description.append(Helper.getFactionIconFromDiscord(faction.getAlias())).append(" ").append(faction.getFactionName()).append(" ");
        } else {
            description.append(Helper.getFactionIconFromDiscord(getFaction())).append(" ").append(getFaction());
        }
        description.append(" ").append(StringUtils.capitalize(getType()));
        if (showUnlockConditions && !"agent".equals(getType())) description.append("\n*Unlock: ").append(getUnlockCondition()).append("*");
        eb.setDescription(description.toString());

        //FIELDS
        eb.addField(Optional.ofNullable(getAbilityName()).orElse(" "), "**" + getAbilityWindow() + "**\n> " + getAbilityText(), false);
        if (includeFlavourText && !StringUtils.isBlank(getFlavourText())) eb.addField(" ", "*" + getFlavourText() + "*", false);

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());
        
        eb.setColor(Color.black);
        return eb.build();
    }

    private String getSourceEmoji() {
        return switch (getSource()) {
            case "ds" -> Emojis.DiscordantStars;
            case "cryppter" -> "";
            case "baldrick" -> "";
            default -> "";
        };
    }

    public boolean search(String searchString) {
        if (searchString == null) return true;
        searchString = searchString.toLowerCase();
        return getID().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getTitle().toLowerCase().contains(searchString) || getAbilityName().toLowerCase().contains(searchString) || getAbilityWindow().toLowerCase().contains(searchString) || getAbilityText().toLowerCase().contains(searchString) || getUnlockCondition().toLowerCase().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getFaction() + " " + getType() + ")";
    }
    
}
