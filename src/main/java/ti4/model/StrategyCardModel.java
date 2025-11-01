package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.Color;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.ResourceHelper;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.CardEmojis;

@Data
public class StrategyCardModel implements ModelInterface, EmbeddableModel {

    private String id;
    private int initiative; // 0 though integer max
    private String group; // used for grouped SC games (pbd100 style)
    private String name;
    private List<String> primaryTexts;
    private List<String> secondaryTexts;
    private String botSCAutomationID; // ID of another SCModel to use the automation/button suite of
    private String imageFileName;
    private String flavourText;
    private String colourHexCode;
    private String imageURL;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return id != null
                && name != null
                && initiative >= 0
                && primaryTexts != null
                && secondaryTexts != null
                && source != null;
    }

    @Override
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeID) {
        EmbedBuilder eb = new EmbedBuilder();
        StringBuilder sb = new StringBuilder();

        // TITLE
        sb.append(getEmojiWordRepresentation()).append(source.emoji());
        eb.setTitle(sb.toString());

        // PRIMARY
        eb.addField("__Primary Ability__", getPrimaryTextFormatted(), false);

        // SECONDARY
        eb.addField("__Secondary Ability__", getSecondaryTextFormatted(), false);

        // FLAVOUR
        if (getFlavourText().isPresent()) {
            eb.addField("", getFlavourText().get(), false);
        }

        // COLOR
        eb.setColor(Color.decode(getColourHexCode()));

        // FOOTER
        if (includeID) {
            sb = new StringBuilder();
            sb.append("ID: ").append(id).append("  source: ").append(source.toString());
            if (!id.equals(getBotSCAutomationID())) {
                sb.append("\nUses automation of SCID: ").append(getBotSCAutomationID());
            }
            eb.setFooter(sb.toString());
        }
        return eb.build();
    }

    @JsonIgnore
    private String getPrimaryTextFormatted() {
        StringBuilder sb = new StringBuilder();
        for (String s : primaryTexts) {
            sb.append("- ").append(s).append("\n");
        }
        return sb.toString();
    }

    @JsonIgnore
    private String getSecondaryTextFormatted() {
        StringBuilder sb = new StringBuilder();
        for (String s : secondaryTexts) {
            sb.append("- ").append(s).append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean search(String searchString) {
        return id.contains(searchString)
                || name.contains(searchString)
                || source.toString().contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        return initiative + " " + name + " (" + id + ") [" + source.toString() + "]";
    }

    @Override
    public String getAlias() {
        return id;
    }

    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    private Optional<String> getFlavourText() {
        return Optional.ofNullable(flavourText);
    }

    public Color getColour() {
        return Color.decode(getColourHexCode());
    }

    private String getColourHexCode() {
        if (colourHexCode == null && id.equals(getBotSCAutomationID())) {
            return "#ffffff";
        } else if (colourHexCode == null) {
            if (Mapper.getStrategyCard(getBotSCAutomationID()) == null
                    || Mapper.getStrategyCard(getBotSCAutomationID()).getColourHexCode() == null) {
                return "#ffffff";
            }
            return Mapper.getStrategyCard(getBotSCAutomationID()).getColourHexCode();
        }
        return Optional.of(colourHexCode).orElse("#ffffff");
    }

    /**
     * SC ID used for automation - buttons/resolving/etc.
     * Example: You have a Homebrew Leadership on Initiative 3 (instead of 1),
     * but it can safely use the normal Leadership buttons.
     * Then set botSCAutomationID = "pok1leadership".
     */
    public String getBotSCAutomationID() {
        return Optional.ofNullable(botSCAutomationID).orElse(id);
    }

    public boolean usesAutomationForSCID(String scID) {
        return getBotSCAutomationID().equals(scID);
    }

    public boolean hasImageFile() {
        return imageFileName != null && getImageFilePath() != null;
    }

    public String getImageFilePath() {
        return ResourceHelper.getResourceFromFolder("strat_cards/", imageFileName + ".png");
    }

    @Deprecated
    public String getImageUrl() {
        return imageURL;
    }

    public String getImageFileUrl() {
        String urlBase =
                "https://cdn.statically.io/gh/AsyncTI4/TI4_map_generator_bot/master/src/main/resources/strat_cards/";
        if (hasImageFile()) {
            return urlBase + imageFileName + ".png";
        } else {
            return urlBase + "sadFace.png";
        }
    }

    @Nullable
    public String getEmojiWordRepresentation() {
        switch (source) {
            case pok, base, thunders_edge, codex1 -> {
                return switch (initiative) {
                    case 1 -> CardEmojis.SC1Mention();
                    case 2 -> CardEmojis.SC2Mention();
                    case 3 -> CardEmojis.SC3Mention();
                    case 4 -> CardEmojis.SC4Mention();
                    case 5 -> CardEmojis.SC5Mention();
                    case 6 -> CardEmojis.SC6Mention();
                    case 7 -> CardEmojis.SC7Mention();
                    case 8 -> CardEmojis.SC8Mention();
                    default -> null;
                };
            }
            case twilights_fall -> {
                return switch (initiative) {
                    case 1 -> CardEmojis.TFSC1Mention();
                    case 2 -> CardEmojis.TFSC2Mention();
                    case 3 -> CardEmojis.TFSC3Mention();
                    case 4 -> CardEmojis.TFSC4Mention();
                    case 5 -> CardEmojis.TFSC5Mention();
                    case 6 -> CardEmojis.TFSC6Mention();
                    case 7 -> CardEmojis.TFSC7Mention();
                    case 8 -> CardEmojis.TFSC8Mention();
                    default -> null;
                };
            }
            default -> {
                return "**SC" + initiative + "[" + name + "]**";
            }
        }
    }
}
