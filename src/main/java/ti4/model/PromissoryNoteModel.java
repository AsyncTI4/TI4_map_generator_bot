package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;

@Data
public class PromissoryNoteModel implements ColorableModelInterface<PromissoryNoteModel>, EmbeddableModel {
    private String alias;
    private String name;
    private String shortName;
    private Boolean shrinkName;
    private String faction;
    private String color;
    private Boolean playArea;
    private Boolean playImmediately;
    private String attachment;
    private ComponentSource source;
    private String text;
    private String homebrewReplacesID;
    private String imageURL;
    private List<String> searchTags = new ArrayList<>();
    private PromissoryNoteModel sourcePNModel; // used for duped promissory notes, to know their source

    /**
     * @return true if this is duplicated from a generic colour promissory note
     */
    public boolean isDupe() {
        return sourcePNModel != null;
    }

    public boolean isColorable() {
        return color.equals("<color>");
    }

    @Override
    public PromissoryNoteModel duplicateAndSetColor(ColorModel newColor) {
        PromissoryNoteModel pn = new PromissoryNoteModel();
        pn.setAlias(alias.replace("<color>", newColor.getName()));
        pn.setName(name);
        pn.setShortName(shortName);
        pn.setShrinkName(shrinkName);
        pn.setFaction(faction);
        pn.setColor(newColor.getName());
        pn.setPlayArea(playArea);
        pn.setPlayImmediately(playImmediately);
        pn.setAttachment(attachment);
        pn.setSource(source);
        String newText = text.replace("<color>", "<" + newColor.getName() + ">");
        pn.setText(newText);
        pn.setHomebrewReplacesID(homebrewReplacesID);
        pn.setSearchTags(new ArrayList<>(searchTags));
        pn.setSourcePNModel(this);
        return pn;
    }

    public boolean isValid() {
        return alias != null && name != null && (faction != null || color != null) && text != null && source != null;
    }

    public String getID() {
        return alias;
    }

    public String getShortName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shortName).orElse(name);
        }
        return Optional.ofNullable(shortName)
                .orElse(Mapper.getPromissoryNote(getHomebrewReplacesID().get()).getShortName());
    }

    public boolean getShrinkName() {
        if (getHomebrewReplacesID().isEmpty()) {
            return Optional.ofNullable(shrinkName).orElse(false);
        }
        return Optional.ofNullable(shrinkName)
                .orElse(Mapper.getPromissoryNote(getHomebrewReplacesID().get()).getShrinkName());
    }

    public Optional<String> getFaction() {
        return Optional.ofNullable(faction);
    }

    public Optional<String> getColor() {
        return Optional.ofNullable(color);
    }

    public String getFactionOrColor() {
        if (!StringUtils.isBlank(getFaction().orElse(""))) return faction;
        if (!StringUtils.isBlank(getColor().orElse(""))) {
            if ("<color>".equals(color)) return "generic";
            return color;
        }
        return faction + "_" + color;
    }

    public Optional<String> getAttachment() {
        return Optional.ofNullable(attachment);
    }

    public Optional<String> getHomebrewReplacesID() {
        return Optional.ofNullable(homebrewReplacesID);
    }

    public String getOwner() {
        if (faction == null || faction.isEmpty()) return color;
        return faction;
    }

    public boolean getPlayArea() {
        return Optional.ofNullable(playArea).orElse(false);
    }

    public boolean isPlayedDirectlyToPlayArea() {
        if (playArea == null) {
            return false;
        }
        if (playImmediately != null) return playArea && playImmediately;

        return playArea;
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false, false);
    }

    public MessageEmbed getRepresentationEmbed(boolean justShowName, boolean includeID, boolean includeHelpfulText) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        StringBuilder title = new StringBuilder();
        title.append(CardEmojis.PN);
        if (!StringUtils.isBlank(getFaction().orElse("")))
            title.append(FactionEmojis.getFactionIcon(getFaction().get()));
        title.append("_").append(name).append("_");
        if (!StringUtils.isBlank(getColor().orElse(""))) {
            title.append(" (");
            if ("<color>".equals(color)) {
                title.append("generic");
            } else {
                title.append(color);
            }
            title.append(")");
        }
        title.append(source.emoji());
        eb.setTitle(title.toString());

        if (justShowName) return eb.build();

        // DESCRIPTION
        eb.setDescription(text);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeHelpfulText) {
            if (!StringUtils.isBlank(getAttachment().orElse("")))
                footer.append("Attachment: ").append(getAttachment().orElse("")).append("\n");
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
            footer.append("ID: ")
                    .append(alias)
                    .append("    Source: ")
                    .append(source)
                    .append("\n");
        }
        eb.setFooter(footer.toString());

        eb.setColor(Color.blue);
        return eb.build();
    }

    public String getNameRepresentation() {
        StringBuilder sb = new StringBuilder();
        if (!StringUtils.isBlank(getFaction().orElse("")))
            sb.append(FactionEmojis.getFactionIcon(getFaction().get()));
        sb.append(CardEmojis.PN);
        sb.append(" ").append(name);
        if (!StringUtils.isBlank(getColor().orElse(""))) {
            sb.append(" (");
            if ("<color>".equals(color)) {
                sb.append("generic");
            } else {
                sb.append(color);
            }
            sb.append(")");
        }
        sb.append(source.emoji());
        return sb.toString();
    }

    public String getTextFormatted(Game game) {
        String formattedText = text;
        formattedText = formattedText.replace("\n", "\n> ");
        StringBuilder replaceText = new StringBuilder();
        Player pnOwner = game.getPNOwner(getID());
        if (pnOwner != null && pnOwner.isRealPlayer()) {
            if (!game.isFowMode()) replaceText.append(pnOwner.getFactionEmoji()); // add Owner's Faction Emoji
            replaceText.append(pnOwner.getColor());
            formattedText = formattedText.replaceAll("<" + pnOwner.getColor() + ">", replaceText.toString());
        }
        return formattedText;
    }

    public boolean isNotWellKnown() {
        return getFaction().isPresent() || (source != ComponentSource.base && source != ComponentSource.pok);
    }

    /**
     * @deprecated This only exists to simulate the old text based promissory note .property files
     */
    @Deprecated
    public String getShortText() {
        String promStr = text;
        // if we would break trying to split the note, just return whatever is there
        if (promStr == null || !promStr.contains(";")) {
            return promStr;
        }
        return name + ";" + getFaction() + getColor();
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || getFactionOrColor().toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " (" + getFactionOrColor() + ") [" + source + "]";
    }

    public boolean getPlayImmediately() {
        return Optional.ofNullable(playImmediately).orElse(false);
    }
}
