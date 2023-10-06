package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.generator.TileHelper;
import ti4.generator.UnitTokenPosition;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.TechSpecialtyModel.TechSpecialty;

import java.awt.*;
import java.util.List;
import java.util.Optional;

@Data
public class PlanetModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String tileId;
    private String name;
    private String shortName;
    private List<String> aliases;
    private Point positionInTile;
    private int resources;
    private int influence;
    private String factionHomeworld;
    private PlanetTypeModel.PlanetType planetType;
    private String cardImagePath; //todo
    private List<TechSpecialtyModel.TechSpecialty> techSpecialties;
    private String legendaryAbilityName;
    private String legendaryAbilityText;
    private String flavourText;
    private UnitTokenPosition unitPositions;
    private int spaceCannonDieCount = 0;
    private int spaceCannonHitsOn = 0;

    public boolean isValid() {
        return getId() != null
            && name != null;
    }

    public String getAlias() {
        return getId();
    }

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        
        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append(getEmoji()).append("__").append(getName()).append("__");
        eb.setTitle(sb.toString());

        switch (getPlanetType()) {
            case HAZARDOUS -> eb.setColor(Color.red);
            case INDUSTRIAL -> eb.setColor(Color.green);
            case CULTURAL -> eb.setColor(Color.blue);
            default -> eb.setColor(Color.white);
        }
        
        TileModel tile = TileHelper.getTile(getTileId());
        sb = new StringBuilder();
        sb.append(getInfResEmojis()).append(getPlanetTypeEmoji()).append(getTechSpecialtyEmoji());
        if (tile != null) sb.append("\nSystem: " + tile.getName());
        eb.setDescription(sb.toString());
        if (getLegendaryAbilityName() != null) eb.addField(Emojis.LegendaryPlanet +  getLegendaryAbilityName(), getLegendaryAbilityText(), false);
        if (getFlavourText() != null) eb.addField("", getFlavourText(), false);

        sb = new StringBuilder();
        sb.append("ID: ").append(getId());
        if (includeAliases) sb.append("\nAliases: ").append(getAliases());
        eb.setFooter(sb.toString());

        if (getEmojiURL() != null) eb.setThumbnail(getEmojiURL());

        return eb.build();
    }

    private String getInfResEmojis() {
        return Helper.getResourceEmoji(resources) + Helper.getResourceEmoji(influence);
    }

    private String getPlanetTypeEmoji() {
        return switch (getPlanetType()) {
            case HAZARDOUS -> Emojis.Hazardous;
            case INDUSTRIAL -> Emojis.Industrial;
            case CULTURAL -> Emojis.Cultural;
            default -> "";
        };
    }

    private String getTechSpecialtyEmoji() {
        if (getTechSpecialties() == null || getTechSpecialties().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TechSpecialty techSpecialty : getTechSpecialties()) {
            switch (techSpecialty) {
                case BIOTIC -> sb.append(Emojis.BioticTech);
                case CYBERNETIC -> sb.append(Emojis.CyberneticTech);
                case PROPULSION -> sb.append(Emojis.PropulsionTech);
                case WARFARE -> sb.append(Emojis.WarfareTech);
                case UNITSKIP -> sb.append(Emojis.UnitTechSkip);
                case NONUNITSKIP -> sb.append(Emojis.NonUnitTechSkip);
            }
        }
        return sb.toString();
    }

    private String getTechSpecialtyStringRepresentation() {
        if (getTechSpecialties() == null || getTechSpecialties().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TechSpecialty techSpecialty : getTechSpecialties()) {
            switch (techSpecialty) {
                case BIOTIC -> sb.append("G");
                case CYBERNETIC -> sb.append("Y");
                case PROPULSION -> sb.append("B");
                case WARFARE -> sb.append("R");
                default -> sb.append("");
                
            }
        }
        return sb.toString();
    }

    public boolean isLegendary() {
        return getLegendaryAbilityName() != null;
    }

    public String getEmoji() {
        return Helper.getPlanetEmoji(getId());
    }

    public String getEmojiURL() {
        Emoji emoji = Emoji.fromFormatted(getEmoji());
        if (emoji instanceof CustomEmoji) {
            CustomEmoji customEmoji = (CustomEmoji) emoji;
            return customEmoji.getImageUrl();
        }
        return null;
    }

    public boolean search(String searchString) {
        return getName().toLowerCase().contains(searchString) || getId().toLowerCase().contains(searchString);
    }

    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" (").append(getResources()).append("/").append(getInfluence());
        if (!getTechSpecialtyStringRepresentation().isBlank()) sb.append(" ").append(getTechSpecialtyStringRepresentation());
        sb.append(")");
        return sb.toString();
    }
}
