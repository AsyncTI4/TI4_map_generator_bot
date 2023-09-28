package ti4.model;

import java.awt.Color;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;

@Data
public class LeaderModel implements ModelInterface {
    //leaderid = LeaderName; LeaderTitle; AbilityName**; AbilityWindow; AbilityText; UnlockCondition   
    private String ID;
    private String type;
    private String faction;
    private String name;
    private String title;
    private String abilityName;
    private String abilityWindow;
    private String abilityText;
    private String unlockCondition;
    private String source;

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getAlias() {
        return getID();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean showUnlockConditions) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(Emojis.ActionCard);
        title.append("__**").append(getName()).append("**__").append(": ").append(getTitle());
        title.append(getSourceEmoji());
        eb.setTitle(title.toString());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        description.append(getFaction()).append(" ").append(getType());
        eb.setDescription(description.toString());

        //FIELD
        eb.addField(getAbilityName(), "*" + getAbilityWindow() + ":* " + getAbilityText(), false);

        if (showUnlockConditions) eb.addField("Unlock:", getUnlockCondition(), false);

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());
        
        eb.setColor(Color.orange);
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
    
}
