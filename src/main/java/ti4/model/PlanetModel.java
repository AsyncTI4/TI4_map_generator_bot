package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.generator.TileHelper;
import ti4.generator.UnitTokenPosition;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.TechSpecialtyModel.TechSpecialty;

import java.awt.*;
import java.util.List;
import java.util.Optional;

@Data
public class PlanetModel {
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

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        
        EmbedBuilder eb = new EmbedBuilder();
        /*
            Set the title:
            1. Arg: title as string
            2. Arg: URL as string or could also be null
        */
        String name = getName() == null ? "" : getName();
        eb.setTitle("__" + name + "__", null);

        /*
            Set the color
        */
        switch (getPlanetType()) {
            case HAZARDOUS -> eb.setColor(Color.red);
            case INDUSTRIAL -> eb.setColor(Color.green);
            case CULTURAL -> eb.setColor(Color.blue);
            default -> eb.setColor(Color.white);
        }

        /*
            Set the text of the Embed:
            Arg: text as string
        */
        
        TileModel tile = TileHelper.getTile(getTileId());
        StringBuilder sb = new StringBuilder();
        sb.append(getInfResEmojis()).append(getPlanetTypeEmoji()).append(getTechSpecialtyEmoji());
        if (tile != null) sb.append("\nSystem: " + tile.getName());
        eb.setDescription(sb.toString());

        /*
            Add fields to embed:
            1. Arg: title as string
            2. Arg: text as string
            3. Arg: inline mode true / false
        */
        // eb.addField("Title of field", "test of field", false);
        if (getLegendaryAbilityName() != null) eb.addField(Emojis.LegendaryPlanet +  getLegendaryAbilityName(), getLegendaryAbilityText(), false);
        if (getFlavourText() != null) eb.addField("", getFlavourText(), false);

        /*
            Add spacer like field
            Arg: inline mode true / false
        */
        // eb.addBlankField(false);

        /*
            Add embed author:
            1. Arg: name as string
            2. Arg: url as string (can be null)
            3. Arg: icon url as string (can be null)
        */
        // eb.setAuthor("name", null, "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png");

        /*
            Set footer:
            1. Arg: text as string
            2. icon url as string (can be null)
        */
        // eb.setFooter("Text", "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png");
        sb = new StringBuilder();
        sb.append("ID: ").append(getId());
        if (includeAliases) sb.append("\nAliases: ").append(getAliases());
        eb.setFooter(sb.toString());

        /*
            Set image:
            Arg: image url as string
        */
        // eb.setImage("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png");

        /*
            Set thumbnail image:
            Arg: image url as string
        */
        // eb.setThumbnail("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png");

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

    public boolean isLegendary() {
        return getLegendaryAbilityName() != null;
    }
}
