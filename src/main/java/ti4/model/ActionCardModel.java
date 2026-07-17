package ti4.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.game.Game;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class ActionCardModel implements ModelInterface, EmbeddableModel {

    public enum PlayTiming {
        NONE, // Catch-all for cards without a modeled play timing restriction; this is currently most cards.
        AGENDA_AFTER,
        AGENDA_WHEN;

        public boolean isDuringAgendaReveal() {
            return this == AGENDA_AFTER || this == AGENDA_WHEN;
        }
    }

    private String alias;
    private String name;
    private String phase;
    private String window;
    private String text;
    private String notes;
    private String flavorText;
    private String imageURL;
    private String automationID;
    private PlayTiming playTiming = PlayTiming.NONE;
    private ComponentSource source;
    private ComponentSource actualSource;
    private List<String> searchTags = new ArrayList<>();
    private boolean affectedByWildWildGalaxy;
    private String wildWildWindow;
    private String wildWildText;

    public boolean isValid() {
        return alias != null && name != null && phase != null && window != null && text != null && source != null;
    }

    public String getNameRepresentation(Game game) {
        return CardEmojis.getACEmoji(game) + (isWild(game) ? "" + CardEmojis.Event : "") + "_" + name + "_";
    }

    public String getNameRepresentation() {
        return getNameRepresentation(null);
    }

    public String getRepresentation(Game game) {
        String cardJustText = getRepresentationJustText(game);
        return getNameRepresentation(game) + " - " + cardJustText + "\n";
    }

    public String getRepresentation() {
        return getRepresentation(null);
    }

    public String getRepresentationJustText(Game game) {
        boolean useWildText = hasWildText(game);
        String cardText = useWildText ? wildWildText : text;
        String cardWindow = useWildText ? wildWildWindow : window;
        if (game != null && game.isTwilightKart() && "tf-starflare".equalsIgnoreCase(alias)) {
            cardText =
                    "Select a system that contains your ships and does not contain any planets, space stations, or printed wormholes. Then draw a random red-backed anomaly tile and replace the selected system with that tile.";
        }
        return cardWindow + ": " + cardText;
    }

    public String getRepresentationJustText() {
        return getRepresentationJustText(null);
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false, false);
    }

    public String getAutomationID() {
        if (automationID == null) return alias;
        return automationID;
    }

    public PlayTiming getPlayTiming() {
        return playTiming == null ? PlayTiming.NONE : playTiming;
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText) {
        return getRepresentationEmbed(includeID, includeFlavourText, null);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID, boolean includeFlavourText, Game game) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        String title = CardEmojis.getACEmoji(game) + (isWild(game) ? "" + CardEmojis.Event : "") + "__**" + name
                + "**__" + source.emoji();
        eb.setTitle(title);

        boolean useWildText = hasWildText(game);
        String cardText = useWildText ? wildWildText : text;
        if (game != null && game.isTwilightKart() && "tf-starflare".equalsIgnoreCase(alias)) {
            cardText =
                    "Select a system that contains your ships and does not contain any planets, space stations, or printed wormholes. Then draw a random red-backed anomaly tile and replace the selected system with that tile.";
        }
        String cardWindow = useWildText ? wildWildWindow : window;

        // DESCRIPTION
        if (notes == null) {
            eb.setDescription("\n***" + cardWindow + ":***\n" + cardText);
        } else {
            eb.setDescription("\n***" + cardWindow + ":***\n" + cardText + "\n-# [" + notes + "]");
        }

        // FLAVOUR TEXT
        if (includeFlavourText && getFlavorText().isPresent())
            eb.addField("", "*" + getFlavorText().get() + "*", true);

        // FOOTER
        StringBuilder footer = new StringBuilder();
        if (includeID)
            footer.append("ID: ").append(alias).append("    Source: ").append(source);
        eb.setFooter(footer.toString());

        eb.setColor(Color.orange);
        return eb.build();
    }

    public boolean search(String searchString) {
        return alias.toLowerCase().contains(searchString)
                || name.toLowerCase().contains(searchString)
                || searchTags.contains(searchString);
    }

    public String getAutoCompleteName() {
        return name + " (" + source + ")";
    }

    public Optional<String> getFlavorText() {
        return Optional.ofNullable(flavorText);
    }

    public boolean isWild(Game game) {
        return (game != null) && affectedByWildWildGalaxy && game.isWildWildGalaxyMode();
    }

    public boolean hasWildText(Game game) {
        return (game != null)
                && affectedByWildWildGalaxy
                && game.isWildWildGalaxyMode()
                && wildWildText != null
                && wildWildWindow != null;
    }
}
