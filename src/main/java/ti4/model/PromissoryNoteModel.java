package ti4.model;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

public class PromissoryNoteModel implements ModelInterface {
    private String alias;
    private String name;
    private String faction;
    private String colour;
    private Boolean playArea;
    private String attachment;
    private String source;
    private String text;

  public boolean isValid() {
        return alias != null
            && name != null
            && (faction != null && colour != null)
            && attachment != null
            && text != null
            && source != null;
    }

    public String getAlias() {
        return alias;
    }
    
    public String getName() {
        return name;
    }
    
    public String getFaction() {
        return faction;
    }

    public String getColour() {
        return colour;
    }

    public String getFactionOrColour() {
        if (!StringUtils.isBlank(getFaction())) return faction;
        if (!StringUtils.isBlank(getColour())) return colour;
        return faction + "_" + colour;
    }

    public Boolean getPlayArea() {
        return playArea;
    }

    public String getAttachment() {
        return attachment;
    }

    public String getText() {
        return text;
    }

    public String getSource() {
        return source;
    }

    public String getOwner() {
        if (faction == null || faction.isEmpty()) return colour;
        return faction;
    }

    public boolean isPlayedDirectlyToPlayArea() {
        if(playArea == null){
            return false;
        }
        List<String> pnIDsToHoldInHandBeforePlayArea = Arrays.asList(
            "gift", "antivirus", "convoys", "dark_pact", "blood_pact", 
            "pop", "terraform", "dspnauge", "dspnaxis", "dspnbent",
            "dspndihm", "dspnghot", "dspngled", "dspnkolu", "dspnkort",
            "dspnlane", "dspnmyko", "dspnolra", "dspnrohd"); //TODO: just add a field to the model for this

        return playArea && !pnIDsToHoldInHandBeforePlayArea.contains(alias);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(Emojis.PN);
        if (!StringUtils.isBlank(getFaction())) title.append(Helper.getFactionIconFromDiscord(getFaction()));
        title.append("__**").append(getName()).append("**__");
        if (!StringUtils.isBlank(getColour())) title.append(" (").append(getColour()).append(")");
        title.append(getSourceEmoji());
        eb.setTitle(title.toString());

        //DESCRIPTION
        StringBuilder description = new StringBuilder();
        description.append(getText());
        eb.setDescription(description.toString());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID) footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource());
        eb.setFooter(footer.toString());
        
        eb.setColor(Color.blue);
        return eb.build();
    }

    private String getSourceEmoji() {
        return switch (getSource()) {
            case "ds" -> Emojis.DiscordantStars;
            default -> "";
        };
    }

}
