package ti4.model;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.ResourceHelper;
import ti4.model.Source.ComponentSource;

@Data
public class StrategyCardModel implements ModelInterface, EmbeddableModel {

    private String id;
    private int initiative; // 0 though infinity
    private String group; // used for grouped SC games (pbd100 style)
    private String name;
    private List<String> primaryTexts;
    private List<String> secondaryTexts;
    private String botSCAutomationID; //ID of another SCModel to use the automation/button suite of
    private String imageFileName;
    private String flavourText;
    private String colourHexCode;
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
        sb.append("**").append(initiative).append("** __").append(name).append("__").append(getSource().emoji());
        eb.setTitle(sb.toString());

        // PRIMARY
        eb.addField("Primary Ability", getPrimaryTextFormatted(), false);

        // SECONDARY
        eb.addField("Secondary Ability", getSecondaryTextFormatted(), false);

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
            || source.toString().contains(searchString);
    }

    @Override
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(initiative).append(" ").append(name).append(" (").append(id).append(") [").append(source.toString()).append("]");
        return sb.toString();
    }

    @Override
    public String getAlias() {
        return id;
    }

    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    public Optional<String> getFlavourText() {
        return Optional.ofNullable(flavourText);
    }

    public String getColourHexCode() {
        return Optional.ofNullable(colourHexCode).orElse("000000");
    }

    /**
     * SC ID used for automation - buttons/resolving/etc.
     * Example: You have a Homebrew Leadership on Initiative 3 (instead of 1), 
     * but it can safely use the normal Leadership buttons.
     * Then set botSCAutomationID = "pok1leadership".
     */
    public String getBotSCAutomationID() {
        return Optional.ofNullable(botSCAutomationID).orElse(getId());
    }

    public boolean usesAutomationForSCID(String scID) {
        return getBotSCAutomationID().equals(scID);
    }

    public boolean hasImageFile() {
        return imageFileName != null
            && ResourceHelper.getInstance().getResourceFromFolder("strat_cards/",
                imageFileName + ".png", null) != null;
    }

    public String getImageFilePath() {
        return ResourceHelper.getInstance().getResourceFromFolder("strat_cards/", getImageFileName() + ".png", "Could not find SC image!");
    }
}
