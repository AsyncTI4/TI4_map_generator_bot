package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Emojis;
import ti4.model.Source.ComponentSource;

@Data
public class PromissoryNoteModel implements ModelInterface, EmbeddableModel {
    private String alias;
    private String name;
    private String faction;
    private String colour;
    private Boolean playArea;
    private String attachment;
    private ComponentSource source;
    private String text;
    private List<String> searchTags = new ArrayList<>();

    public boolean isValid() {
        return alias != null
            && name != null
            && (faction != null || colour != null)
            && text != null
            && source != null;
    }

    public Optional<String> getFaction() {
        return Optional.ofNullable(faction);
    }

    public Optional<String> getColour() {
        return Optional.ofNullable(colour);
    }

    public String getFactionOrColour() {
        if (!StringUtils.isBlank(getFaction().orElse(""))) return faction;
        if (!StringUtils.isBlank(getColour().orElse(""))) return colour;
        return faction + "_" + colour;
    }

    public Optional<String> getAttachment() {
        return Optional.ofNullable(attachment);
    }

    public String getOwner() {
        if (faction == null || faction.isEmpty()) return colour;
        return faction;
    }

    public boolean isPlayedDirectlyToPlayArea() {
        if (playArea == null) {
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
        return getRepresentationEmbed(false, false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean justShowName, boolean includeID, boolean includeHelpfulText) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(Emojis.PN);
        if (!StringUtils.isBlank(getFaction().orElse(""))) title.append(Emojis.getFactionIconFromDiscord(getFaction().get()));
        title.append("__**").append(getName()).append("**__");
        if (!StringUtils.isBlank(getColour().orElse(""))) title.append(" (").append(getColour()).append(")");
        title.append(getSource().emoji());
        eb.setTitle(title.toString());

        if (justShowName) return eb.build();

        //DESCRIPTION
        eb.setDescription(getText());

        //FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeHelpfulText) {
            if (!StringUtils.isBlank(getAttachment().orElse(""))) footer.append("Attachment: ").append(getAttachment().orElse("")).append("\n");
            if (getPlayArea()) {
                footer.append("Play area card. ");
                if (isPlayedDirectlyToPlayArea()) {
                    footer.append("Sent directly to play area when received.");
                } else {
                    footer.append("Must be played from hand to enter play area.");
                }
                footer.append("\n");
            }
        }
        if (includeID) {
            footer.append("ID: ").append(getAlias()).append("    Source: ").append(getSource()).append("\n");
        }
        eb.setFooter(footer.toString());

        eb.setColor(Color.blue);
        return eb.build();
    }

    public boolean search(String searchString) {
        return getAlias().toLowerCase().contains(searchString) || getName().toLowerCase().contains(searchString) || getFactionOrColour().toLowerCase().contains(searchString)
            || getSearchTags().contains(searchString);
    }

    public String getAutoCompleteName() {
        return getName() + " (" + getFactionOrColour() + ") (" + getSource() + ")";
    }

}
